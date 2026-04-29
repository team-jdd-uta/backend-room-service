# backend-room-service

Redis 기반 채팅방 메타데이터 서비스입니다. 방 생성, 방 목록 조회, 방 단건 조회, 방 삭제를 담당하고, 현재 참여자 수는 Socket.IO Gateway가 Redis에 기록한 counter를 읽어 반환합니다.

## 역할

- 채팅방 정보를 Redis Hash `CHAT_ROOM`에 저장합니다.
- 방별 참여자 수를 Redis String `sessions:count:{roomId}`에서 읽습니다.
- WebSocket, Socket.IO, 메시지 발행은 담당하지 않습니다.
- `backend-chat-service`가 TALK 메시지 검증 시 방 존재 여부를 확인하는 대상 서비스입니다.

## 기술 스택

- Java 21
- Spring Boot 4
- Spring Web
- Spring Data Redis
- Redis Cluster

## 주요 API

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/rooms` | 전체 채팅방 목록 조회 |
| `GET` | `/rooms/{roomId}` | 채팅방 단건 조회 |
| `GET` | `/rooms/counts` | 채팅방별 참여자 수 조회 |
| `POST` | `/rooms?name={name}` | 채팅방 생성 |
| `DELETE` | `/rooms/{roomId}` | 채팅방 삭제 |

생성 예:

```bash
curl -X POST "http://localhost:8082/rooms?name=smoke-room"
```

조회 예:

```bash
curl http://localhost:8082/rooms
```

## 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `SERVER_PORT` | `8082` | HTTP 서버 포트 |
| `SPRING_REDIS_CLUSTER_NODES` | `localhost:7000,...,localhost:7005` | Redis Cluster 노드 목록 |

Spring property는 `src/main/resources/application.properties`에 정의되어 있습니다.

## 로컬 실행

Redis Cluster가 먼저 떠 있어야 합니다.

```bash
gradle bootRun
```

Docker 이미지 빌드:

```bash
docker build -t team9-room-service:local .
```

## Redis Key 규칙

| Key | Type | 설명 |
| --- | --- | --- |
| `CHAT_ROOM` | Hash | `roomId -> ChatRoom` |
| `sessions:count:{roomId}` | String | 현재 방 참여자 수 |

## Kubernetes 기준

- 기본 Service port는 `8082`입니다.
- Ingress에서는 `/api/chat/rooms`가 이 서비스의 `/rooms`로 rewrite됩니다.
- Redis Cluster DNS는 `SPRING_REDIS_CLUSTER_NODES`로 주입합니다.

## 주의점

- Redis 데이터가 사라지면 채팅방 목록도 사라집니다.
- 참여자 수는 Socket.IO Gateway가 관리하므로, Gateway가 죽거나 재시작되면 실제 접속자와 일시적으로 차이가 날 수 있습니다.
