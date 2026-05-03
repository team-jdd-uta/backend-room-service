package com.example.room.service;

import com.example.room.dto.RoomJoinTokenResponse;
import com.example.room.model.ChatRoom;
import com.example.room.model.RoomStatus;
import com.example.room.persistence.RoomEntity;
import com.example.room.persistence.RoomEntityRepository;
import com.example.room.persistence.RoomMapper;
import com.example.room.persistence.RoomOutboxEventEntity;
import com.example.room.persistence.RoomOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ChatRoomService {

    private static final String CHAT_ROOMS = "CHAT_ROOM";
    private static final String LIVE_BROADCASTER_PREFIX = "CHAT_ROOM:LIVE_BROADCASTER:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RoomSecurityService roomSecurityService;
    private final RoomEntityRepository roomRepository;
    private final RoomOutboxEventRepository roomOutboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String rtmpUrl;
    private HashOperations<String, String, ChatRoom> hashOperations;

    public ChatRoomService(RedisTemplate<String, Object> redisTemplate,
                           StringRedisTemplate stringRedisTemplate,
                           RoomSecurityService roomSecurityService,
                           RoomEntityRepository roomRepository,
                           RoomOutboxEventRepository roomOutboxEventRepository,
                           ObjectMapper objectMapper,
                           @Value("${room.rtmp-url:rtmp://rtmp.team9.cloud.skala-ai.com/live}") String rtmpUrl) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.roomSecurityService = roomSecurityService;
        this.roomRepository = roomRepository;
        this.roomOutboxEventRepository = roomOutboxEventRepository;
        this.objectMapper = objectMapper;
        this.rtmpUrl = rtmpUrl;
    }

    @PostConstruct
    void init() {
        this.hashOperations = redisTemplate.opsForHash();
    }

    @Transactional
    public List<ChatRoom> findAllRoom() {
        Map<String, ChatRoom> roomsById = new LinkedHashMap<>();
        roomRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(RoomMapper::toModel)
                .forEach(room -> roomsById.put(room.getRoomId(), normalize(room)));

        for (ChatRoom room : loadLegacyRoomsFromRedis()) {
            if (room != null && !roomsById.containsKey(room.getRoomId())) {
                roomsById.put(room.getRoomId(), room);
                roomRepository.save(RoomMapper.toEntity(room));
            }
        }

        return roomsById.values().stream()
                .sorted(Comparator.comparing(ChatRoom::getCreatedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    @Transactional
    public ChatRoom findRoomById(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            return null;
        }
        String normalizedRoomId = roomId.trim();
        Optional<RoomEntity> roomEntity = roomRepository.findById(normalizedRoomId);
        if (roomEntity.isPresent()) {
            ChatRoom room = normalize(RoomMapper.toModel(roomEntity.get()));
            syncRedisRoom(room);
            return room;
        }

        ChatRoom legacyRoom = loadLegacyRoomFromRedis(normalizedRoomId);
        if (legacyRoom != null) {
            roomRepository.save(RoomMapper.toEntity(legacyRoom));
            syncRedisRoom(legacyRoom);
            return legacyRoom;
        }

        return null;
    }

    @Transactional
    public ChatRoom createChatRoom(String name) {
        return createChatRoom(name, null);
    }

    @Transactional
    public ChatRoom createChatRoom(String name, String broadcasterId) {
        ChatRoom chatRoom = ChatRoom.create(name);
        long now = now();
        chatRoom.setCreatedAt(now);
        chatRoom.setUpdatedAt(now);

        if (broadcasterId != null && !broadcasterId.isBlank()) {
            String normalizedBroadcasterId = broadcasterId.trim();
            ensureBroadcasterHasNoActiveRoom(normalizedBroadcasterId);
            chatRoom.setBroadcasterId(normalizedBroadcasterId);
            chatRoom.setStatus(RoomStatus.READY);
            chatRoom.setStreamKey(roomSecurityService.generateStreamKey(chatRoom.getRoomId(), normalizedBroadcasterId));
            chatRoom.setJoinToken(roomSecurityService.generateJoinToken(chatRoom.getRoomId(), normalizedBroadcasterId));
        } else {
            chatRoom.setStatus(RoomStatus.DRAFT);
            chatRoom.setStreamKey(roomSecurityService.generateStreamKey(chatRoom.getRoomId(), chatRoom.getRoomId()));
            chatRoom.setJoinToken(null);
        }

        persistRoom(chatRoom, "ROOM_CREATED");
        syncRedisRoom(chatRoom);
        if (chatRoom.getBroadcasterId() != null && !chatRoom.getBroadcasterId().isBlank()) {
            stringRedisTemplate.opsForValue().set(liveBroadcasterKey(chatRoom.getBroadcasterId().trim()), chatRoom.getRoomId());
        }
        return chatRoom;
    }

    public ChatRoom joinRoom(String roomId, String userId, String joinToken) {
        ChatRoom room = requireRoom(roomId);
        if (room.getStatus() != RoomStatus.READY
                && room.getStatus() != RoomStatus.LIVE
                && room.getStatus() != RoomStatus.STOPPED) {
            throw new IllegalStateException("room is not open for joining: " + roomId);
        }
        if (!roomSecurityService.matchesJoinToken(roomId, userId, joinToken)) {
            throw new IllegalArgumentException("join token mismatch for roomId: " + roomId);
        }
        return room;
    }

    @Transactional
    public RoomJoinTokenResponse issueJoinToken(String roomId, String userId) {
        ChatRoom room = requireRoom(roomId);
        if (room.getStatus() != RoomStatus.READY
                && room.getStatus() != RoomStatus.LIVE
                && room.getStatus() != RoomStatus.STOPPED) {
            throw new IllegalStateException("room is not open for joining: " + roomId);
        }

        String normalizedUserId = requireNonBlank(userId, "userId");
        String token = roomSecurityService.generateJoinToken(room.getRoomId(), normalizedUserId);
        long issuedAt = now();

        persistOutboxEvent(room, "ROOM_JOIN_TOKEN_ISSUED", Map.of(
                "roomId", room.getRoomId(),
                "userId", normalizedUserId,
                "issuedAt", issuedAt
        ));

        return RoomJoinTokenResponse.builder()
                .roomId(room.getRoomId())
                .userId(normalizedUserId)
                .joinToken(token)
                .roomStatus(room.getStatus())
                .issuedAt(issuedAt)
                .build();
    }

    @Transactional
    public ChatRoom startLiveRoom(String roomId) {
        ChatRoom room = requireRoom(roomId);
        if (room.getStatus() == RoomStatus.ENDED) {
            throw new IllegalStateException("ended room cannot go live again: " + roomId);
        }

        String broadcasterId = room.getBroadcasterId();
        if (broadcasterId != null && !broadcasterId.isBlank()) {
            ensureBroadcasterAvailableForLive(room.getRoomId(), broadcasterId.trim());
        }

        long now = now();
        room.setStatus(RoomStatus.LIVE);
        room.setStartedAt(room.getStartedAt() == null ? now : room.getStartedAt());
        room.setUpdatedAt(now);

        persistRoom(room, "ROOM_LIVE_STARTED");
        syncRedisRoom(room);

        if (broadcasterId != null && !broadcasterId.isBlank()) {
            stringRedisTemplate.opsForValue().set(liveBroadcasterKey(broadcasterId.trim()), room.getRoomId());
        }
        return room;
    }

    @Transactional
    public ChatRoom stopLiveRoom(String roomId) {
        ChatRoom room = requireRoom(roomId);
        if (room.getStatus() == RoomStatus.ENDED) {
            return room;
        }
        if (room.getStatus() != RoomStatus.LIVE) {
            return room;
        }

        long now = now();
        room.setStatus(RoomStatus.STOPPED);
        room.setUpdatedAt(now);

        persistRoom(room, "ROOM_LIVE_STOPPED");
        syncRedisRoom(room);

        return room;
    }

    @Transactional
    public ChatRoom endLiveRoom(String roomId) {
        ChatRoom room = requireRoom(roomId);
        long now = now();
        room.setStatus(RoomStatus.ENDED);
        room.setEndedAt(room.getEndedAt() == null ? now : room.getEndedAt());
        room.setUpdatedAt(now);

        persistRoom(room, "ROOM_LIVE_ENDED");
        syncRedisRoom(room);

        String broadcasterId = room.getBroadcasterId();
        if (broadcasterId != null && !broadcasterId.isBlank()) {
            String key = liveBroadcasterKey(broadcasterId.trim());
            String currentRoomId = stringRedisTemplate.opsForValue().get(key);
            if (room.getRoomId().equals(currentRoomId)) {
                stringRedisTemplate.delete(key);
            }
        }
        return room;
    }

    @Transactional
    public ChatRoom updateRoomName(String roomId, String broadcasterId, String name) {
        ChatRoom room = requireRoom(roomId);
        String normalizedBroadcasterId = requireNonBlank(broadcasterId, "broadcasterId");
        if (room.getBroadcasterId() == null || !room.getBroadcasterId().trim().equals(normalizedBroadcasterId)) {
            throw new IllegalStateException("only the broadcaster can update this room: " + roomId);
        }

        String normalizedName = requireNonBlank(name, "name");
        room.setName(normalizedName);
        room.setUpdatedAt(now());

        persistRoom(room, "ROOM_RENAMED");
        syncRedisRoom(room);
        return room;
    }

    @Transactional
    public void deleteChatRoom(String roomId) {
        ChatRoom existing = requireRoom(roomId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", existing.getRoomId());
        payload.put("name", existing.getName());
        payload.put("broadcasterId", existing.getBroadcasterId());
        payload.put("status", existing.getStatus());
        persistOutboxEvent(existing, "ROOM_DELETED", payload);
        roomRepository.deleteById(existing.getRoomId());
        if (existing.getBroadcasterId() != null && !existing.getBroadcasterId().isBlank()) {
            stringRedisTemplate.delete(liveBroadcasterKey(existing.getBroadcasterId()));
        }
        hashOperations.delete(CHAT_ROOMS, roomId);
    }

    public String getRtmpUrl() {
        return rtmpUrl;
    }

    private ChatRoom requireRoom(String roomId) {
        String normalizedRoomId = requireNonBlank(roomId, "roomId");
        ChatRoom existing = findRoomById(normalizedRoomId);
        if (existing == null) {
            throw new NoSuchElementException("chat room not found: " + normalizedRoomId);
        }
        return existing;
    }

    private ChatRoom normalize(ChatRoom room) {
        if (room == null) {
            return null;
        }
        if (room.getStatus() == null) {
            room.setStatus(RoomStatus.DRAFT);
        }
        return room;
    }

    private void ensureBroadcasterHasNoActiveRoom(String broadcasterId) {
        if (roomRepository.findFirstByBroadcasterIdAndStatusIn(broadcasterId, List.of(RoomStatus.READY, RoomStatus.LIVE, RoomStatus.STOPPED)).isPresent()) {
            throw new IllegalStateException("broadcaster already has an active room: " + broadcasterId);
        }

        String liveRoomId = stringRedisTemplate.opsForValue().get(liveBroadcasterKey(broadcasterId));
        if (liveRoomId != null && !liveRoomId.isBlank()) {
            throw new IllegalStateException("broadcaster already has an active room: " + broadcasterId);
        }
    }

    private void ensureBroadcasterAvailableForLive(String roomId, String broadcasterId) {
        String liveRoomId = stringRedisTemplate.opsForValue().get(liveBroadcasterKey(broadcasterId));
        if (liveRoomId != null && !liveRoomId.isBlank() && !roomId.equals(liveRoomId)) {
            throw new IllegalStateException("broadcaster already has a live room: " + broadcasterId);
        }

        roomRepository.findFirstByBroadcasterIdAndStatusInAndRoomIdNot(
                broadcasterId,
                List.of(RoomStatus.READY, RoomStatus.LIVE, RoomStatus.STOPPED),
                roomId
        ).ifPresent(room -> {
            throw new IllegalStateException("broadcaster already has an active room: " + broadcasterId);
        });
    }

    private void persistRoom(ChatRoom room, String eventType) {
        roomRepository.save(RoomMapper.toEntity(room));
        persistOutboxEvent(room, eventType, roomSnapshotPayload(room));
    }

    private void persistOutboxEvent(ChatRoom room, String eventType, Map<String, Object> payload) {
        persistOutboxEvent(room.getRoomId(), eventType, payload);
    }

    private void persistOutboxEvent(String roomId, String eventType, Map<String, Object> payload) {
        try {
            roomOutboxEventRepository.save(RoomOutboxEventEntity.builder()
                    .aggregateType("room")
                    .aggregateId(roomId)
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(payload))
                    .createdAt(now())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("failed to persist room outbox event", e);
        }
    }

    private Map<String, Object> roomSnapshotPayload(ChatRoom room) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", room.getRoomId());
        payload.put("name", room.getName());
        payload.put("broadcasterId", room.getBroadcasterId());
        payload.put("status", room.getStatus());
        payload.put("createdAt", room.getCreatedAt());
        payload.put("updatedAt", room.getUpdatedAt());
        payload.put("startedAt", room.getStartedAt());
        payload.put("endedAt", room.getEndedAt());
        return payload;
    }

    private void syncRedisRoom(ChatRoom room) {
        if (room == null || room.getRoomId() == null) {
            return;
        }
        hashOperations.put(CHAT_ROOMS, room.getRoomId(), room);
    }

    private List<ChatRoom> loadLegacyRoomsFromRedis() {
        List<ChatRoom> rooms = new ArrayList<>(hashOperations.values(CHAT_ROOMS));
        rooms.replaceAll(this::normalize);
        return rooms;
    }

    private ChatRoom loadLegacyRoomFromRedis(String roomId) {
        ChatRoom room = hashOperations.get(CHAT_ROOMS, roomId);
        return normalize(room);
    }

    private String liveBroadcasterKey(String broadcasterId) {
        if (broadcasterId == null || broadcasterId.isBlank()) {
            return LIVE_BROADCASTER_PREFIX;
        }
        return LIVE_BROADCASTER_PREFIX + broadcasterId.trim();
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
