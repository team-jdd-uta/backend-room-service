package com.example.room.dto;

import com.example.room.model.RoomStatus;
import lombok.Builder;

@Builder
public record RoomJoinTokenResponse(
        String roomId,
        String userId,
        String joinToken,
        RoomStatus roomStatus,
        Long issuedAt
) {
}
