package com.drivemonitor;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class SensorService extends Service implements SensorEventListener {

    public static final String EVENT_NONE         = "";
    public static final String EVENT_HARSH_BRAKE  = "⚠️ Harsh Braking!";
    public static final String EVENT_RAPID_ACCEL  = "⚠️ Rapid Acceleration!";
    public static final String EVENT_SHARP_TURN   = "⚠️ Sharp Turn!";

    private static final float THRESHOLD_BRAKE = -4.5f;   // strong deceleration
    private static final float THRESHOLD_ACCEL =  4.5f;   // strong acceleration
    private static final float THRESHOLD_TURN  =  4.0f;   // lateral (Y axis)

    // Low-pass filter coeff
    private static final float ALPHA = 0.15f;

    private static final String CHANNEL_ID = "drive_monitor_channel";
    private static final int    NOTIF_ID   = 1;

    // Sensor
    private SensorManager    sensorManager;
    private Sensor           accelerometer;
    private float[]          gravity = new float[3];   // low-pass result

    // GPS
    private FusedLocationProviderClient fusedClient;
    private LocationCallback            locationCallback;
    private Location                    lastLocation;
    private float                       totalDistance = 0f; // metres
    private float                       currentSpeed  = 0f; // km/h

    // Trip State
    private int   eventCount = 0;
    private int   score      = 100;
    private static final int PENALTY = 10;

    // Cool-down: avoid flooding same event
    private long lastEventTime = 0;
    private static final long EVENT_COOLDOWN_MS = 2000;

    public interface SensorCallback {
        void onSensorUpdate(float ax, float ay, float az,
                            float speed, float distance,
                            String event, int score, int events);
    }

    private static SensorCallback callback;
    public static void setCallback(SensorCallback cb) { callback = cb; }
    public static void clearCallback()                { callback = null; }

    @Override
    public void onCreate() {
        super.onCreate();
        android.util.Log.d("SensorService", "Service started");
        // Foreground notification
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Monitoring your trip…"));

        // Accelerometer
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        // GPS
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        setupLocationUpdates();
    }

    // Accelerometer

    @Override
    public void onSensorChanged(SensorEvent event) {
        android.util.Log.d("SensorService", "Sensor fired: " + event.values[0]);
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Low-pass filter
        // The accelerometer reads everything — including Earth's constant gravity pulling down on the phone.
        // We don't want gravity triggering false alerts, so we need to separate it from actual movement.
        // ALPHA = 0.15 means each new reading only contributes 15% to the gravity estimate — so it updates very slowly and smoothly.
        // Eg: time     accl (m/s2)
        //      t1      9.8
        //      t2      9.8
        //      t3      20

        // our reading: instead of jumping directly to 20, gravity ≈ 0.15*20 + 0.85*9.8 ≈ 11.23

        gravity[0] = ALPHA * event.values[0] + (1 - ALPHA) * gravity[0];
        gravity[1] = ALPHA * event.values[1] + (1 - ALPHA) * gravity[1];
        gravity[2] = ALPHA * event.values[2] + (1 - ALPHA) * gravity[2];

        // Linear acceleration = raw - gravity
        float linX = event.values[0] - gravity[0];
        float linY = event.values[1] - gravity[1];
        float linZ = event.values[2] - gravity[2];

        String detectedEvent = EVENT_NONE;
        long now = System.currentTimeMillis();

        if (now - lastEventTime > EVENT_COOLDOWN_MS) {
            if      (linX < THRESHOLD_BRAKE) detectedEvent = EVENT_HARSH_BRAKE;
            else if (linX > THRESHOLD_ACCEL) detectedEvent = EVENT_RAPID_ACCEL;
            else if (Math.abs(linY) > THRESHOLD_TURN) detectedEvent = EVENT_SHARP_TURN;

            if (!detectedEvent.isEmpty()) {
                eventCount++;
                score = Math.max(0, 100 - eventCount * PENALTY);
                lastEventTime = now;
            }
        }

        broadcastUpdate(linX, linY, linZ, detectedEvent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // GPS

    private void setupLocationUpdates() {
        LocationRequest req = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                Location loc = result.getLastLocation();
                if (loc == null) return;

                currentSpeed = loc.getSpeed() * 3.6f; // m/s → km/h

                if (lastLocation != null) {
                    totalDistance += lastLocation.distanceTo(loc); // metres
                }
                lastLocation = loc;
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
        }
    }

    // Broadcast

    private void broadcastUpdate(float ax, float ay, float az, String event) {
        if (callback != null) {
            callback.onSensorUpdate(ax, ay, az,
                    currentSpeed, totalDistance / 1000f,
                    event, score, eventCount);
        }
    }

    // called by MainActivity when trip stops

    public int   getScore()         { return score; }
    public int   getEventCount()    { return eventCount; }
    public float getTotalDistanceKm() { return totalDistance / 1000f; }

    // Notification

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.channel_desc));
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Drive Monitor")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setOngoing(true)        // ← prevents user from dismissing it
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        fusedClient.removeLocationUpdates(locationCallback);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
