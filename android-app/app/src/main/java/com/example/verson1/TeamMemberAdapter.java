package com.example.verson1;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class TeamMemberAdapter extends RecyclerView.Adapter<TeamMemberAdapter.TeamMemberViewHolder> {
    
    private List<RequestSwapActivity.TeamMember> teamMembers = new ArrayList<>();
    private Context context;
    private OnTeamMemberSelectedListener listener;
    private String selectedMemberId = null;
    
    public interface OnTeamMemberSelectedListener {
        void onTeamMemberSelected(RequestSwapActivity.TeamMember member);
    }
    
    public TeamMemberAdapter(Context context, OnTeamMemberSelectedListener listener) {
        this.context = context;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public TeamMemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_team_member, parent, false);
        return new TeamMemberViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull TeamMemberViewHolder holder, int position) {
        RequestSwapActivity.TeamMember member = teamMembers.get(position);
        
        holder.tvName.setText(member.getName());
        holder.tvRole.setText(member.getRole());
        holder.tvAvailability.setText(member.getAvailability());
        
        // Set avatar (you can use image loading library like Glide for real images)
        holder.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
        
        // Set selection state
        holder.rbSelect.setChecked(member.getId().equals(selectedMemberId));
        
        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            selectedMemberId = member.getId();
            listener.onTeamMemberSelected(member);
            notifyDataSetChanged(); // Refresh to update radio buttons
        });
        
        holder.rbSelect.setOnClickListener(v -> {
            selectedMemberId = member.getId();
            listener.onTeamMemberSelected(member);
            notifyDataSetChanged(); // Refresh to update radio buttons
        });
    }
    
    @Override
    public int getItemCount() {
        return teamMembers.size();
    }
    
    public void setTeamMembers(List<RequestSwapActivity.TeamMember> teamMembers) {
        this.teamMembers = teamMembers;
        notifyDataSetChanged();
    }
    
    public void setSelectedMember(String memberId) {
        this.selectedMemberId = memberId;
        notifyDataSetChanged();
    }
    
    static class TeamMemberViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        TextView tvRole;
        TextView tvAvailability;
        RadioButton rbSelect;
        
        public TeamMemberViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvName = itemView.findViewById(R.id.tv_name);
            tvRole = itemView.findViewById(R.id.tv_role);
            tvAvailability = itemView.findViewById(R.id.tv_availability);
            rbSelect = itemView.findViewById(R.id.rb_select);
        }
    }
}
