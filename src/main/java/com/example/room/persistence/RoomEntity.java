package com.example.room.persistence;

import com.example.room.model.RoomStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "rooms",
        indexes = {
                @Index(name = "idx_rooms_broadcaster_status", columnList = "broadcaster_id,status")
        }
)
public class RoomEntity {
    @Id
    @Column(name = "room_id", nullable = false, length = 64)
    private String roomId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "broadcaster_id", length = 255)
    private String broadcasterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RoomStatus status;

    @Column(name = "stream_key", length = 255)
    private String streamKey;

    @Column(name = "join_token", length = 255)
    private String joinToken;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    @Column(name = "started_at")
    private Long startedAt;

    @Column(name = "ended_at")
    private Long endedAt;
}
