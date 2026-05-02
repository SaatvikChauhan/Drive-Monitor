package com.drivemonitor;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.ViewHolder> {

    private final List<DatabaseHelper.TripRecord> trips;

    public TripAdapter(List<DatabaseHelper.TripRecord> trips) {
        this.trips = trips;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        DatabaseHelper.TripRecord t = trips.get(position);
        h.tvDate.setText(t.date);
        h.tvScore.setText("Score: " + t.score);
        h.tvDuration.setText("⏱ " + t.duration);
        h.tvEvents.setText("⚠️ " + t.events + " events");
        h.tvDistance.setText(String.format("📍 %.1f km", t.distance));

        // Color-code score
        int color;
        if      (t.score >= 80) color = 0xFF00C853; // green
        else if (t.score >= 50) color = 0xFFFF6D00; // orange
        else                    color = 0xFFFF1744; // red
        h.tvScore.setTextColor(color);
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvScore, tvDuration, tvEvents, tvDistance;

        ViewHolder(View v) {
            super(v);
            tvDate     = v.findViewById(R.id.tvTripDate);
            tvScore    = v.findViewById(R.id.tvTripScore);
            tvDuration = v.findViewById(R.id.tvTripDuration);
            tvEvents   = v.findViewById(R.id.tvTripEvents);
            tvDistance = v.findViewById(R.id.tvTripDistance);
        }
    }
}
