package com.example.room.dto;

public record RoomCreateRequest(
        String name,
        String broadcasterId,
        String userId
) {
    public String resolvedBroadcasterId() {
        if (broadcasterId != null && !broadcasterId.isBlank()) {
            return broadcasterId.trim();
        }
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        return null;
    }
}
