package com.example.room.persistence;

import com.example.room.model.ChatRoom;
import com.example.room.model.RoomStatus;

public final class RoomMapper {
    private RoomMapper() {
    }

    public static ChatRoom toModel(RoomEntity entity) {
        if (entity == null) {
            return null;
        }
        return ChatRoom.builder()
                .roomId(entity.getRoomId())
                .name(entity.getName())
                .broadcasterId(entity.getBroadcasterId())
                .status(entity.getStatus() == null ? RoomStatus.DRAFT : entity.getStatus())
                .streamKey(entity.getStreamKey())
                .joinToken(entity.getJoinToken())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .startedAt(entity.getStartedAt())
                .endedAt(entity.getEndedAt())
                .build();
    }

    public static RoomEntity toEntity(ChatRoom model) {
        if (model == null) {
            return null;
        }
        return RoomEntity.builder()
                .roomId(model.getRoomId())
                .name(model.getName())
                .broadcasterId(model.getBroadcasterId())
                .status(model.getStatus() == null ? RoomStatus.DRAFT : model.getStatus())
                .streamKey(model.getStreamKey())
                .joinToken(model.getJoinToken())
                .createdAt(model.getCreatedAt())
                .updatedAt(model.getUpdatedAt())
                .startedAt(model.getStartedAt())
                .endedAt(model.getEndedAt())
                .build();
    }

    public static RoomEntity copy(RoomEntity source) {
        if (source == null) {
            return null;
        }
        return RoomEntity.builder()
                .roomId(source.getRoomId())
                .name(source.getName())
                .broadcasterId(source.getBroadcasterId())
                .status(source.getStatus())
                .streamKey(source.getStreamKey())
                .joinToken(source.getJoinToken())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .startedAt(source.getStartedAt())
                .endedAt(source.getEndedAt())
                .build();
    }
}
