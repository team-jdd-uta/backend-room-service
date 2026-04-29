package com.example.room.service;

import com.example.room.model.ChatRoom;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ChatRoomService {

    private static final String CHAT_ROOMS = "CHAT_ROOM";

    private final RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, String, ChatRoom> hashOperations;

    public ChatRoomService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    void init() {
        this.hashOperations = redisTemplate.opsForHash();
    }

    public List<ChatRoom> findAllRoom() {
        return new ArrayList<>(hashOperations.values(CHAT_ROOMS));
    }

    public ChatRoom findRoomById(String roomId) {
        return hashOperations.get(CHAT_ROOMS, roomId);
    }

    public ChatRoom createChatRoom(String name) {
        ChatRoom chatRoom = ChatRoom.create(name);
        hashOperations.put(CHAT_ROOMS, chatRoom.getRoomId(), chatRoom);
        return chatRoom;
    }

    public void deleteChatRoom(String roomId) {
        ChatRoom existing = hashOperations.get(CHAT_ROOMS, roomId);
        if (existing == null) {
            throw new NoSuchElementException("chat room not found: " + roomId);
        }
        hashOperations.delete(CHAT_ROOMS, roomId);
    }
}

