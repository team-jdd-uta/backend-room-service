package com.example.room.dto;

import com.example.room.model.RoomStatus;
import lombok.Builder;

@Builder
public record RoomProvisioningResponse(
        String roomId,
        String name,
        String broadcasterId,
        RoomStatus status,
        String streamKey,
        String joinToken,
        String rtmpUrl,
        Long createdAt,
        Long updatedAt,
        Long startedAt,
        Long endedAt
) {
}
