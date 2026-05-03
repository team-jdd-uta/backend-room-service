package com.example.room.dto;

public record InternalUserResponse(
        String userId,
        String username,
        String email
) {
}
