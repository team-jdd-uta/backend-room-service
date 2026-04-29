# room-service

## 개요

`room-service`는 채팅방 메타데이터를 Redis `CHAT_ROOM` 해시에 저장하고, 방별 참여자 수를 Redis에서 직접 읽어 반환하는 Spring Boot 서비스다.

## 로컬 실행

설치된 Gradle을 기준으로 실행한다.

```bash
gradle bootRun
```

## 환경변수

- `SERVER_PORT`
- `SPRING_REDIS_CLUSTER_NODES`

기본값은 `src/main/resources/application.properties`에 있다.

## 주요 API

- `GET /rooms`
- `GET /rooms/{roomId}`
- `GET /rooms/counts`
- `POST /rooms?name={name}`
- `DELETE /rooms/{roomId}`

## Redis 규칙

- 방 저장소: `CHAT_ROOM`
- 참여자 수: `sessions:count:{roomId}`
- Redis 직렬화는 모노리스와 호환되도록 JSON hash value를 사용한다.

