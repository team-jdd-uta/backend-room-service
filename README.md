# backend-room-service

MariaDB 기반 채팅방 메타데이터 서비스입니다. 방 생성, 방 목록 조회, 방 단건 조회, 방 삭제를 담당하고, 현재 참여자 수는 Socket.IO Gateway가 Redis에 기록한 counter를 읽어 반환합니다. 방 정보의 source of truth는 MariaDB `rooms` 테이블이고, Redis는 참가자 수와 live broadcaster lock, 그리고 호환 캐시로만 사용합니다.

## 역할

- 채팅방 정보를 MariaDB `rooms` 테이블에 저장합니다.
- 방 생성/상태 전환 시 `room_outbox_events`에 이벤트를 적재합니다.
- 방별 참여자 수는 Redis String `sessions:count:{roomId}`에서 읽습니다.
- WebSocket, Socket.IO, 메시지 발행은 담당하지 않습니다.
- `backend-chat-service`가 TALK 메시지 검증 시 방 존재 여부와 오픈 상태를 확인하는 대상 서비스입니다.
- 방송 생성 시 broadcasterId가 있으면 `READY` 상태로 발급하고, 같은 broadcaster가 동시에 다른 `READY/LIVE` 방을 가질 수 없도록 막습니다.
- `streamKey`는 현재 `roomId`와 동일하게 발급되므로 RTMP ingest 경로와 HLS 재생 경로가 같은 키를 사용합니다.

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
| `POST` | `/rooms?name={name}&broadcasterId={userId}` | 채팅방 생성 및 라이브 세션 발급 |
| `POST` | `/rooms/{roomId}/join-token?userId={userId}` | 사용자별 입장 토큰 발급 |
| `POST` | `/rooms/{roomId}/join?userId={userId}&joinToken={token}` | 방 입장 검증 |
| `POST` | `/rooms/{roomId}/live` | 송출 시작 반영 |
| `POST` | `/rooms/{roomId}/end` | 송출 종료 반영 |
| `DELETE` | `/rooms/{roomId}` | 채팅방 삭제 |

생성 예:

```bash
curl -X POST "http://localhost:8082/rooms?name=smoke-room"
```

라이브 세션 발급 예:

```bash
curl -X POST "http://localhost:8082/rooms?name=live-room&broadcasterId=user-1"
```

프론트처럼 JSON body로도 보낼 수 있습니다.

```bash
curl -X POST "http://localhost:8082/rooms" \
  -H "Content-Type: application/json" \
  -d '{"name":"live-room","userId":"user-1"}'
```

응답에는 `roomId`, `streamKey`, `joinToken`, `rtmpUrl`, `status`가 포함됩니다.

`join-token` API는 시청자가 입장 직전에 자신의 `userId` 기준 토큰을 받아갈 때 사용합니다.

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
- 외부 경로는 `/api/room/rooms`를 사용합니다.
- Redis Cluster DNS는 `SPRING_REDIS_CLUSTER_NODES`로 주입합니다.

## 주의점

- MariaDB 데이터가 남아 있으면 Redis가 사라져도 방 정보는 다시 읽을 수 있습니다.
- 참여자 수는 Socket.IO Gateway가 관리하므로, Gateway가 죽거나 재시작되면 실제 접속자와 일시적으로 차이가 날 수 있습니다.
- `broadcasterId`가 없는 기존 생성 요청은 기존처럼 저장되지만 `DRAFT` 상태로 남아 라이브 전환/입장 토큰 흐름은 비어 있을 수 있습니다.
- RTMP 서버가 `/rooms/{roomId}/live`와 `/rooms/{roomId}/end`를 호출해 상태를 전환합니다.
