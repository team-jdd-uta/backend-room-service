package com.example.room.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {
    private String roomId;
    private String name;
    private String broadcasterId;
    private RoomStatus status;

    @JsonIgnore
    private String streamKey;

    @JsonIgnore
    private String joinToken;

    private Long createdAt;
    private Long updatedAt;
    private Long startedAt;
    private Long endedAt;

    public static ChatRoom create(String name) {
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.roomId = UUID.randomUUID().toString();
        chatRoom.name = name;
        chatRoom.status = RoomStatus.DRAFT;
        return chatRoom;
    }
}
