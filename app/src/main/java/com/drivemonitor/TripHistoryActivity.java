package com.drivemonitor;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TripHistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_history);

        RecyclerView recycler = findViewById(R.id.recyclerTrips);
        TextView tvNoTrips    = findViewById(R.id.tvNoTrips);

        DatabaseHelper db = new DatabaseHelper(this);
        List<DatabaseHelper.TripRecord> trips = db.getAllTrips();

        if (trips.isEmpty()) {
            tvNoTrips.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            tvNoTrips.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.setAdapter(new TripAdapter(trips));
        }
    }
}
