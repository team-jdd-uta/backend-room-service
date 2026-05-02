package com.example.room.controller;

import com.example.room.dto.RoomProvisioningResponse;
import com.example.room.dto.RoomJoinTokenResponse;
import com.example.room.dto.RoomCreateRequest;
import com.example.room.model.ChatRoom;
import com.example.room.service.ChatRoomService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final StringRedisTemplate stringRedisTemplate;
    private final String rtmpCallbackSecret;

    public ChatRoomController(ChatRoomService chatRoomService,
                               StringRedisTemplate stringRedisTemplate,
                               @Value("${room.rtmp-callback-secret:rtmp-dev-secret}") String rtmpCallbackSecret) {
        this.chatRoomService = chatRoomService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rtmpCallbackSecret = rtmpCallbackSecret;
    }

    @GetMapping("/categories")
    public List<Map<String, String>> getCategories() {
        return List.of(
                Map.of("categoryName", "게임"),
                Map.of("categoryName", "토크"),
                Map.of("categoryName", "음악"),
                Map.of("categoryName", "스포츠"),
                Map.of("categoryName", "요리"),
                Map.of("categoryName", "예술"),
                Map.of("categoryName", "크리에이티브"),
                Map.of("categoryName", "학습")
        );
    }

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
        List<ChatRoom> rooms = chatRoomService.findAllRoom();
        List<String> keys = rooms.stream()
                .map(room -> "sessions:count:" + room.getRoomId())
                .toList();
        List<String> values = keys.isEmpty() ? List.of() : stringRedisTemplate.opsForValue().multiGet(keys);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            ChatRoom room = rooms.get(i);
            String val = (values != null && i < values.size()) ? values.get(i) : null;
            result.add(Map.of(
                    "roomId", room.getRoomId(),
                    "name", room.getName(),
                    "participants", parseCount(val)
            ));
        }
        return result;
    }

    @PostMapping
    public ResponseEntity<RoomProvisioningResponse> createRoom(@RequestParam(required = false) String name,
                                                               @RequestParam(required = false) String broadcasterId,
                                                               @RequestBody(required = false) RoomCreateRequest request) {
        String resolvedName = request != null && request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : name;
        String resolvedBroadcasterId = request != null ? request.resolvedBroadcasterId() : null;
        if (resolvedBroadcasterId == null || resolvedBroadcasterId.isBlank()) {
            resolvedBroadcasterId = broadcasterId;
        }
        if (isBlank(resolvedName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "room name is required");
        }
        ChatRoom created;
        try {
            created = chatRoomService.createChatRoom(resolvedName.trim(), resolvedBroadcasterId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/rooms/" + created.getRoomId())
                .body(RoomProvisioningResponse.builder()
                        .roomId(created.getRoomId())
                        .name(created.getName())
                        .broadcasterId(created.getBroadcasterId())
                        .status(created.getStatus())
                        .streamKey(created.getStreamKey())
                        .joinToken(created.getJoinToken())
                        .rtmpUrl(chatRoomService.getRtmpUrl())
                        .createdAt(created.getCreatedAt())
                        .updatedAt(created.getUpdatedAt())
                        .startedAt(created.getStartedAt())
                        .endedAt(created.getEndedAt())
                        .build());
    }

    @PostMapping("/{roomId}/join")
    public ChatRoom joinRoom(@PathVariable String roomId,
                             @RequestParam String userId,
                             @RequestParam String joinToken) {
        if (isBlank(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required");
        }
        if (isBlank(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        if (isBlank(joinToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "joinToken is required");
        }
        try {
            return chatRoomService.joinRoom(roomId.trim(), userId.trim(), joinToken.trim());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{roomId}/join-token")
    public RoomJoinTokenResponse issueJoinToken(@PathVariable String roomId,
                                                @RequestParam String userId) {
        if (isBlank(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required");
        }
        if (isBlank(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }

        try {
            return chatRoomService.issueJoinToken(roomId.trim(), userId.trim());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{roomId}/live")
    public ChatRoom startLive(@PathVariable String roomId,
                              @RequestParam(required = false, defaultValue = "") String callbackSecret) {
        verifyRtmpCallbackSecret(callbackSecret);
        if (isBlank(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required");
        }
        try {
            return chatRoomService.startLiveRoom(roomId.trim());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{roomId}/end")
    public ChatRoom endLive(@PathVariable String roomId,
                            @RequestParam(required = false, defaultValue = "") String callbackSecret) {
        verifyRtmpCallbackSecret(callbackSecret);
        if (isBlank(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required");
        }
        try {
            return chatRoomService.endLiveRoom(roomId.trim());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
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

    private void verifyRtmpCallbackSecret(String provided) {
        if (!rtmpCallbackSecret.equals(provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid callback secret");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
