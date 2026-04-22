package com.example.verson1;

import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter for displaying AI messages in RecyclerView
 * Handles different message types with different styles
 */
public class AIMessageAdapter extends RecyclerView.Adapter<AIMessageAdapter.MessageViewHolder> {

    private List<AIMessage> messages;

    public AIMessageAdapter(List<AIMessage> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) {
            // User message
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ai_message_user, parent, false);
            return new MessageViewHolder(view);
        } else {
            // Bot message
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ai_message_bot, parent, false);
            return new MessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        AIMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public int getItemViewType(int position) {
        AIMessage message = messages.get(position);
        if (message.isUserMessage()) {
            return 0; // User message
        } else {
            return 1; // Bot message
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        private TextView messageText;
        private TextView timestamp;
        private CardView messageCard;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
            timestamp = itemView.findViewById(R.id.timestamp);
            messageCard = itemView.findViewById(R.id.message_card);
        }

        void bind(AIMessage message) {
            // Set message text
            messageText.setText(message.getContent());
            messageText.setMovementMethod(LinkMovementMethod.getInstance());

            // Set timestamp
            timestamp.setText(message.getFormattedTime());

            // Style based on message type
            if (message.isUserMessage()) {
                messageCard.setCardBackgroundColor(itemView.getContext().getColor(R.color.user_message_bg));
                messageText.setTextColor(itemView.getContext().getColor(R.color.user_message_text));
            } else if (message.isErrorMessage()) {
                messageCard.setCardBackgroundColor(itemView.getContext().getColor(R.color.error_message_bg));
                messageText.setTextColor(itemView.getContext().getColor(R.color.error_message_text));
            } else {
                messageCard.setCardBackgroundColor(itemView.getContext().getColor(R.color.bot_message_bg));
                messageText.setTextColor(itemView.getContext().getColor(R.color.bot_message_text));
            }
        }
    }
}
