package com.drivemonitor;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorService.SensorCallback {

    private TextView     tvStatus, tvSpeed, tvScore, tvEvents, tvAccel, tvDistance, tvDuration;
    private Button       btnStartStop, btnHistory;
    private LinearLayout layoutWelcome, layoutTrip;

    // States
    private boolean tripRunning = false;
    private long    tripStartTime;

    private long lastEventDisplayTime = 0;
    private static final long STATUS_DISPLAY_MS = 2000;
    // Duration
    private final Handler  timerHandler  = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override public void run() {
            if (!tripRunning) return;
            long elapsed = System.currentTimeMillis() - tripStartTime;
            long secs    = (elapsed / 1000) % 60;
            long mins    = elapsed / 60000;
            tvDuration.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
            timerHandler.postDelayed(this, 1000);
        }
    };

    private int   lastScore    = 100;
    private int   lastEvents   = 0;
    private float lastDistance = 0f;

    // Perm launcher
    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        startTrip();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        layoutWelcome = findViewById(R.id.layoutWelcome);

        // Second Screen
        layoutTrip    = findViewById(R.id.layoutTrip);
        tvStatus      = findViewById(R.id.tvStatus);
        tvSpeed       = findViewById(R.id.tvSpeed);
        tvScore       = findViewById(R.id.tvScore);
        tvEvents      = findViewById(R.id.tvEvents);
        tvAccel       = findViewById(R.id.tvAccel);
        tvDistance    = findViewById(R.id.tvDistance);
        tvDuration    = findViewById(R.id.tvDuration);
        btnStartStop  = findViewById(R.id.btnStartStop);
        btnHistory    = findViewById(R.id.btnHistory);

        btnStartStop.setOnClickListener(v -> {
            if (tripRunning) stopTrip();
            else             requestPermissionsAndStart();
        });

        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(this, TripHistoryActivity.class)));


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    // Trip Control

    private void requestPermissionsAndStart() {
        // if already have perm
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startTrip();
        }
        // make popup otherwise
        else {
            permLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @Override
    public void onSensorUpdate(float ax, float ay, float az,
                               float speed, float distance,
                               String event, int score, int events) {
        runOnUiThread(() -> {
            lastScore    = score;
            lastEvents   = events;
            lastDistance = distance;

            tvScore.setText(String.valueOf(score));
            tvEvents.setText(String.valueOf(events));
            tvSpeed.setText(String.format(Locale.getDefault(), "%.1f km/h", speed));
            tvDistance.setText(String.format(Locale.getDefault(), "%.1f", distance));
            tvAccel.setText(String.format(Locale.getDefault(),
                    "X: %.2f   Y: %.2f   Z: %.2f", ax, ay, az));

            boolean safe = (event == null || event.isEmpty());

            if (!safe) {
                lastEventDisplayTime = System.currentTimeMillis();
                tvStatus.setText("● " + event.replace("⚠️ ", ""));
                tvStatus.setTextColor(getResources().getColor(R.color.colorUnsafe, null));
                tvStatus.setBackgroundResource(R.drawable.pill_unsafe);
                Toast.makeText(this, event, Toast.LENGTH_SHORT).show();

            } else if (System.currentTimeMillis() - lastEventDisplayTime > STATUS_DISPLAY_MS) {
                tvStatus.setText("● Safe Driving");
                tvStatus.setTextColor(getResources().getColor(R.color.colorSafe, null));
                tvStatus.setBackgroundResource(R.drawable.pill_safe);
            }

            if      (score >= 80) tvScore.setTextColor(0xFF00C853);
            else if (score >= 50) tvScore.setTextColor(0xFFFF6D00);
            else                  tvScore.setTextColor(0xFFFF1744);
        });
    }
    private void startTrip() {
        tripRunning   = true;
        tripStartTime = System.currentTimeMillis();
        lastScore     = 100;
        lastEvents    = 0;
        lastDistance  = 0f;

        layoutWelcome.setVisibility(View.GONE);
        layoutTrip.setVisibility(View.VISIBLE);

        tvScore.setText("100");
        tvEvents.setText("0");
        tvDistance.setText("0.0");
        tvStatus.setText("● Safe Driving");
        tvStatus.setTextColor(getResources().getColor(R.color.colorSafe, null));
        tvStatus.setBackgroundResource(R.drawable.pill_safe);

        btnStartStop.setText("Stop Trip");
        btnStartStop.setBackgroundTintList(getColorStateList(R.color.colorUnsafe));
        btnStartStop.setTextColor(getResources().getColor(android.R.color.white, null));

        SensorService.setCallback(this);
        Intent svc = new Intent(this, SensorService.class);
        ContextCompat.startForegroundService(this, svc);

        timerHandler.post(timerRunnable);
    }

    private void stopTrip() {
        SensorService.clearCallback();
        tripRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        stopService(new Intent(this, SensorService.class));

        layoutTrip.setVisibility(View.GONE);
        layoutWelcome.setVisibility(View.VISIBLE);

        btnStartStop.setText("Start Trip");
        btnStartStop.setBackgroundTintList(getColorStateList(R.color.colorAccent));
        btnStartStop.setTextColor(getResources().getColor(R.color.colorPrimary, null));

        saveTripToDb();
        Toast.makeText(this, "Trip saved! Score: " + lastScore, Toast.LENGTH_LONG).show();
    }

    private void saveTripToDb() {
        long durationMs = tripStartTime > 0 ? System.currentTimeMillis() - tripStartTime : 0;
        long secs = (durationMs / 1000) % 60;
        long mins = durationMs / 60000;
        String dur = String.format(Locale.getDefault(), "%02d:%02d", mins, secs);

        String date = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(new Date());

        new DatabaseHelper(this).insertTrip(date, dur, lastEvents, lastScore, lastDistance);
    }
}