package com.drivemonitor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "drive_monitor.db";
    private static final int    DB_VERSION = 1;

    // Table
    static final String TABLE_TRIPS     = "trips";
    static final String COL_ID          = "id";
    static final String COL_DATE        = "date";
    static final String COL_DURATION    = "duration";   // stored as "MM:SS"
    static final String COL_EVENTS      = "events";     // integer count
    static final String COL_SCORE       = "score";      // 0–100
    static final String COL_DISTANCE    = "distance";   // km, float

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String create = "CREATE TABLE " + TABLE_TRIPS + " (" +
                COL_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_DATE     + " TEXT, " +
                COL_DURATION + " TEXT, " +
                COL_EVENTS   + " INTEGER, " +
                COL_SCORE    + " INTEGER, " +
                COL_DISTANCE + " REAL" +
                ")";
        db.execSQL(create);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRIPS);
        onCreate(db);
    }

    /** Insert a completed trip. Returns row id, or -1 on failure. */
    public long insertTrip(String date, String duration, int events, int score, float distance) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_DATE,     date);
        cv.put(COL_DURATION, duration);
        cv.put(COL_EVENTS,   events);
        cv.put(COL_SCORE,    score);
        cv.put(COL_DISTANCE, distance);
        long id = db.insert(TABLE_TRIPS, null, cv);
        db.close();
        return id;
    }

    /** Returns all trips, newest first. */
    public List<TripRecord> getAllTrips() {
        List<TripRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TRIPS, null, null, null, null, null, COL_ID + " DESC");
        if (c.moveToFirst()) {
            do {
                TripRecord t = new TripRecord();
                t.id       = c.getInt(   c.getColumnIndexOrThrow(COL_ID));
                t.date     = c.getString(c.getColumnIndexOrThrow(COL_DATE));
                t.duration = c.getString(c.getColumnIndexOrThrow(COL_DURATION));
                t.events   = c.getInt(   c.getColumnIndexOrThrow(COL_EVENTS));
                t.score    = c.getInt(   c.getColumnIndexOrThrow(COL_SCORE));
                t.distance = c.getFloat( c.getColumnIndexOrThrow(COL_DISTANCE));
                list.add(t);
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        return list;
    }

    // data class
    public static class TripRecord {
        public int    id;
        public String date;
        public String duration;
        public int    events;
        public int    score;
        public float  distance;
    }
}
