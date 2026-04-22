package com.example.verson1;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ActivityFeedAdapter extends RecyclerView.Adapter<ActivityFeedAdapter.ActivityViewHolder> {
    
    private List<ActivityItem> activities = new ArrayList<>();
    private Context context;
    
    public ActivityFeedAdapter(Context context) {
        this.context = context;
    }
    
    @NonNull
    @Override
    public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_activity_feed, parent, false);
        return new ActivityViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
        ActivityItem activity = activities.get(position);
        
        holder.activityText.setText(activity.getText());
        holder.activityTime.setText(activity.getTime());
        
        // Set status indicator color based on activity type
        switch (activity.getType()) {
            case "swap":
                holder.statusIndicator.setBackgroundColor(context.getColor(R.color.brand_primary));
                break;
            case "leave":
                holder.statusIndicator.setBackgroundColor(context.getColor(R.color.status_warning));
                break;
            case "approval":
                holder.statusIndicator.setBackgroundColor(context.getColor(R.color.status_success));
                break;
            default:
                holder.statusIndicator.setBackgroundColor(context.getColor(R.color.text_secondary));
                break;
        }
        
        // Set avatar (you can use image loading library like Glide for real images)
        holder.userAvatar.setImageResource(activity.getAvatarRes());
    }
    
    @Override
    public int getItemCount() {
        return activities.size();
    }
    
    public void setActivities(List<ActivityItem> activities) {
        this.activities = activities;
        notifyDataSetChanged();
    }
    
    public void addActivity(ActivityItem activity) {
        activities.add(0, activity); // Add to top
        notifyItemInserted(0);
    }
    
    static class ActivityViewHolder extends RecyclerView.ViewHolder {
        ImageView userAvatar;
        TextView activityText;
        TextView activityTime;
        View statusIndicator;
        
        public ActivityViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.user_avatar);
            activityText = itemView.findViewById(R.id.activity_text);
            activityTime = itemView.findViewById(R.id.activity_time);
            statusIndicator = itemView.findViewById(R.id.status_indicator);
        }
    }
    
    public static class ActivityItem {
        private String text;
        private String time;
        private String type;
        private int avatarRes;
        
        public ActivityItem(String text, String time, String type, int avatarRes) {
            this.text = text;
            this.time = time;
            this.type = type;
            this.avatarRes = avatarRes;
        }
        
        public String getText() { return text; }
        public String getTime() { return time; }
        public String getType() { return type; }
        public int getAvatarRes() { return avatarRes; }
    }
}
