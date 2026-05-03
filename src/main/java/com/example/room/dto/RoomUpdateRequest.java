package com.example.room.dto;

public record RoomUpdateRequest(
        String name,
        String category
) {
    public String resolvedName() {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return null;
    }

    public String resolvedCategory() {
        if (category != null && !category.isBlank()) {
            return category.trim();
        }
        return null;
    }
}
