package com.example.room.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class RoomSecurityService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String joinTokenSecret;

    public RoomSecurityService(@Value("${room.join-token-secret:room-service-dev-secret}") String joinTokenSecret) {
        this.joinTokenSecret = joinTokenSecret;
    }

    public String generateStreamKey(String roomId, String broadcasterId) {
        return safe(roomId);
    }

    public String generateJoinToken(String roomId, String userId) {
        return hmac(roomId + ":" + userId);
    }

    public boolean matchesJoinToken(String roomId, String userId, String joinToken) {
        if (joinToken == null || joinToken.isBlank()) {
            return false;
        }
        String expected = generateJoinToken(roomId, userId);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                joinToken.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(joinTokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("failed to generate room token", e);
        }
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
