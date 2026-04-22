package com.example.verson1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.VH> {

    public interface OnClickListener {
        void onClick(NotificationItem item);
    }

    private final List<NotificationItem> items = new ArrayList<>();
    private final OnClickListener listener;

    public NotificationsAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<NotificationItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NotificationItem item = items.get(position);
        h.title.setText(item.title == null || item.title.isEmpty() ? titleFromType(item.type) : item.title);
        h.message.setText(item.message);
        h.time.setText(formatTime(item.createdAt));
        h.dot.setVisibility(item.read ? View.GONE : View.VISIBLE);

        int iconRes;
        int bgRes;
        int tintRes;
        switch (item.type == null ? "info" : item.type) {
            case "shift_assignment":
            case "shift_created":
            case "shift_updated":
            case "shift_cancelled":
                iconRes = R.drawable.ic_briefcase_modern;
                bgRes = R.drawable.bg_icon_container_primary;
                tintRes = R.color.brand_primary;
                break;
            case "chat":
            case "message":
                iconRes = R.drawable.ic_chat_modern;
                bgRes = R.drawable.bg_icon_container_indigo;
                tintRes = R.color.brand_secondary;
                break;
            case "leave_request":
            case "leave_approved":
            case "leave_rejected":
                iconRes = R.drawable.ic_calendar_modern;
                bgRes = R.drawable.bg_icon_container_amber;
                tintRes = R.color.status_warning;
                break;
            case "swap_request":
            case "swap_approved":
                iconRes = R.drawable.ic_swap_modern;
                bgRes = R.drawable.bg_icon_container_emerald;
                tintRes = R.color.status_success;
                break;
            case "announcement":
            case "ai":
                iconRes = R.drawable.ic_sparkles_modern;
                bgRes = R.drawable.bg_icon_container_violet;
                tintRes = R.color.shift_night;
                break;
            default:
                iconRes = R.drawable.ic_bell_modern;
                bgRes = R.drawable.bg_icon_container_primary;
                tintRes = R.color.brand_primary;
        }
        h.icon.setImageResource(iconRes);
        ((View) h.icon.getParent()).setBackgroundResource(bgRes);
        h.icon.setColorFilter(h.itemView.getContext().getResources().getColor(tintRes));

        h.root.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String titleFromType(String type) {
        if (type == null) return "Notification";
        switch (type) {
            case "shift_assignment": return "New shift assigned";
            case "shift_created": return "New shift posted";
            case "shift_cancelled": return "Shift cancelled";
            case "shift_updated": return "Shift updated";
            case "message": return "New message";
            case "leave_request": return "Leave request";
            case "leave_approved": return "Leave approved";
            case "leave_rejected": return "Leave rejected";
            case "swap_request": return "Swap request";
            case "swap_approved": return "Swap approved";
            case "announcement": return "Announcement";
            default: return "Notification";
        }
    }

    private static String formatTime(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            if (iso.length() >= 24) iso = iso.substring(0, 19);
            Date d = in.parse(iso);
            if (d == null) return "";
            long diff = System.currentTimeMillis() - d.getTime();
            long minutes = diff / 60000;
            if (minutes < 1) return "just now";
            if (minutes < 60) return minutes + "m ago";
            long hours = minutes / 60;
            if (hours < 24) return hours + "h ago";
            long days = hours / 24;
            if (days < 7) return days + "d ago";
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return "";
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        View root;
        ImageView icon;
        TextView title, message, time;
        View dot;

        VH(@NonNull View v) {
            super(v);
            root = v.findViewById(R.id.root);
            icon = v.findViewById(R.id.iv_icon);
            title = v.findViewById(R.id.tv_title);
            message = v.findViewById(R.id.tv_message);
            time = v.findViewById(R.id.tv_time);
            dot = v.findViewById(R.id.dot_unread);
        }
    }
}
