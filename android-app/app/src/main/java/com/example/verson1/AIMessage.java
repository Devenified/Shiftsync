package com.example.verson1;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model class for AI chat messages
 */
public class AIMessage {

    public enum MessageType {
        USER,
        BOT,
        ERROR,
        SYSTEM
    }

    private String content;
    private MessageType type;
    private String sender;
    private LocalDateTime timestamp;

    public AIMessage(String content, MessageType type, String sender) {
        this.content = content;
        this.type = type;
        this.sender = sender;
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public String getContent() {
        return content;
    }

    public MessageType getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getFormattedTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        return timestamp.format(formatter);
    }

    public boolean isUserMessage() {
        return type == MessageType.USER;
    }

    public boolean isBotMessage() {
        return type == MessageType.BOT;
    }

    public boolean isErrorMessage() {
        return type == MessageType.ERROR;
    }
}
