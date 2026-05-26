# WebSocket STOMP Skill

Use:
- Spring WebSocket
- STOMP protocol
- SockJS fallback

Topics:
- /topic/room/{roomCode}

Client Events:
- /app/join
- /app/start
- /app/finish

Rules:
- Broadcast updates immediately
- Keep payloads lightweight
- Handle reconnect logic