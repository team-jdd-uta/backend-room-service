package com.example.room.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RoomDatabaseCharsetInitializerTest {

    @Test
    void convertsRoomTablesToUtf8mb4OnStartup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RoomDatabaseCharsetInitializer initializer = new RoomDatabaseCharsetInitializer(jdbcTemplate);

        initializer.run();

        verify(jdbcTemplate).execute("ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        verify(jdbcTemplate).execute("ALTER TABLE rooms CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        verify(jdbcTemplate).execute("ALTER TABLE room_outbox_events CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }
}
