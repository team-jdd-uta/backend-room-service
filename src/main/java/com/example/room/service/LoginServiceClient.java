package com.example.room.service;

import com.example.room.dto.InternalUserResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class LoginServiceClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String loginServiceBaseUrl;
    private final String internalToken;

    public LoginServiceClient(ObjectMapper objectMapper,
                              @Value("${login.service.base-url:}") String loginServiceBaseUrl,
                              @Value("${login.service.internal-token:}") String internalToken) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.loginServiceBaseUrl = loginServiceBaseUrl == null ? "" : loginServiceBaseUrl.trim();
        this.internalToken = internalToken == null ? "" : internalToken.trim();
    }

    public Optional<String> findUserIdByCognitoSub(String cognitoSub) {
        if (cognitoSub == null || cognitoSub.isBlank() || loginServiceBaseUrl.isBlank() || internalToken.isBlank()) {
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(loginServiceBaseUrl + "/internal/users/by-cognito-sub/" + encode(cognitoSub)))
                    .header("X-Internal-Token", internalToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("failed to resolve user by cognito sub: HTTP " + response.statusCode());
            }

            InternalUserResponse body = objectMapper.readValue(response.body(), InternalUserResponse.class);
            if (body == null || body.userId() == null || body.userId().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(body.userId().trim());
        } catch (IOException e) {
            throw new IllegalStateException("failed to resolve user by cognito sub", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("failed to resolve user by cognito sub", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
