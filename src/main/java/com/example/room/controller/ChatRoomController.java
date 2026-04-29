package com.example.room.controller;

import com.example.room.model.ChatRoom;
import com.example.room.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping
    public List<ChatRoom> findAllRoom() {
        return chatRoomService.findAllRoom();
    }

    @GetMapping("/{roomId}")
    public ChatRoom findRoomById(@PathVariable String roomId) {
        if (isBlank(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required");
        }

        ChatRoom chatRoom = chatRoomService.findRoomById(roomId.trim());
        if (chatRoom == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "chat room not found: " + roomId);
        }
        return chatRoom;
    }

    @GetMapping("/counts")
    public List<Map<String, Object>> roomCounts() {
        return chatRoomService.findAllRoom().stream()
                .map(room -> {
                    String val = stringRedisTemplate.opsForValue().get("sessions:count:" + room.getRoomId());
                    int participants = parseCount(val);
                    return Map.<String, Object>of(
                            "roomId", room.getRoomId(),
                            "name", room.getName(),
                            "participants", participants
                    );
                })
                .toList();
    }

    @PostMapping
    public ResponseEntity<ChatRoom> createRoom(@RequestParam String name) {
        if (isBlank(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "room name is required");
        }
        return ResponseEntity.ok(chatRoomService.createChatRoom(name.trim()));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId) {
        if (isBlank(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required");
        }
        try {
            chatRoomService.deleteChatRoom(roomId.trim());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    private int parseCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
