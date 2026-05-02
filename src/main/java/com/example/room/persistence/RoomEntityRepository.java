package com.example.room.persistence;

import com.example.room.model.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomEntityRepository extends JpaRepository<RoomEntity, String> {
    List<RoomEntity> findAllByOrderByUpdatedAtDesc();

    Optional<RoomEntity> findFirstByBroadcasterIdAndStatusInAndRoomIdNot(
            String broadcasterId,
            Collection<RoomStatus> statuses,
            String roomId
    );

    Optional<RoomEntity> findFirstByBroadcasterIdAndStatusIn(
            String broadcasterId,
            Collection<RoomStatus> statuses
    );
}
