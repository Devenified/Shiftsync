package com.example.verson1;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class AvailableShiftAdapter extends RecyclerView.Adapter<AvailableShiftAdapter.ShiftViewHolder> {
    
    private List<AvailableShift> shifts = new ArrayList<>();
    private Context context;
    private OnShiftClickListener listener;
    
    public interface OnShiftClickListener {
        void onShiftClick(AvailableShift shift);
        void onApplyClick(AvailableShift shift);
    }
    
    public AvailableShiftAdapter(Context context, OnShiftClickListener listener) {
        this.context = context;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ShiftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_available_shift, parent, false);
        return new ShiftViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ShiftViewHolder holder, int position) {
        AvailableShift shift = shifts.get(position);
        
        holder.tvShiftTitle.setText(shift.getTitle());
        holder.tvWage.setText("\u20B9" + shift.getWage() + "/day");
        holder.tvShiftType.setText(shift.getType());
        holder.tvDate.setText(shift.getDate());
        holder.tvTime.setText(shift.getTime());
        holder.tvLocation.setText(shift.getLocation());
        holder.tvDescription.setText(shift.getDescription());
        
        // Set shift type color
        setShiftTypeColor(holder.tvShiftType, shift.getType());
        
        // Set click listeners
        holder.itemView.setOnClickListener(v -> listener.onShiftClick(shift));
        holder.btnApply.setOnClickListener(v -> listener.onApplyClick(shift));
        
        // Hide apply button if already applied
        holder.btnApply.setVisibility(shift.isApplied() ? View.GONE : View.VISIBLE);
    }
    
    @Override
    public int getItemCount() {
        return shifts.size();
    }
    
    public void setShifts(List<AvailableShift> shifts) {
        this.shifts = shifts;
        notifyDataSetChanged();
    }
    
    public void markAsApplied(String shiftId) {
        for (AvailableShift shift : shifts) {
            if (shift.getId().equals(shiftId)) {
                shift.setApplied(true);
                notifyDataSetChanged();
                break;
            }
        }
    }
    
    private void setShiftTypeColor(TextView textView, String type) {
        GradientDrawable background = (GradientDrawable) textView.getBackground();
        int color;
        
        switch (type.toLowerCase()) {
            case "morning":
                color = context.getColor(R.color.shift_morning);
                break;
            case "afternoon":
                color = context.getColor(R.color.shift_afternoon);
                break;
            case "night":
                color = context.getColor(R.color.shift_night);
                break;
            default:
                color = context.getColor(R.color.shift_morning);
                break;
        }
        
        background.setColor(color);
    }
    
    static class ShiftViewHolder extends RecyclerView.ViewHolder {
        TextView tvShiftTitle;
        TextView tvWage;
        TextView tvShiftType;
        TextView tvDate;
        TextView tvTime;
        TextView tvLocation;
        TextView tvDescription;
        MaterialButton btnApply;
        
        public ShiftViewHolder(@NonNull View itemView) {
            super(itemView);
            tvShiftTitle = itemView.findViewById(R.id.tv_shift_title);
            tvWage = itemView.findViewById(R.id.tv_wage);
            tvShiftType = itemView.findViewById(R.id.tv_shift_type);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvLocation = itemView.findViewById(R.id.tv_location);
            tvDescription = itemView.findViewById(R.id.tv_description);
            btnApply = itemView.findViewById(R.id.btn_apply);
        }
    }
    
    public static class AvailableShift {
        private String id;
        private String title;
        private String type;
        private String date;
        private String time;
        private String location;
        private String description;
        private double wage;
        private boolean applied;
        
        public AvailableShift(String id, String title, String type, String date, String time, 
                            String location, String description, double wage) {
            this.id = id;
            this.title = title;
            this.type = type;
            this.date = date;
            this.time = time;
            this.location = location;
            this.description = description;
            this.wage = wage;
            this.applied = false;
        }
        
        // Getters
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getType() { return type; }
        public String getDate() { return date; }
        public String getTime() { return time; }
        public String getLocation() { return location; }
        public String getDescription() { return description; }
        public double getWage() { return wage; }
        public boolean isApplied() { return applied; }
        
        public void setApplied(boolean applied) { this.applied = applied; }
    }
}
