package com.example.room.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomOutboxEventRepository extends JpaRepository<RoomOutboxEventEntity, Long> {
}
