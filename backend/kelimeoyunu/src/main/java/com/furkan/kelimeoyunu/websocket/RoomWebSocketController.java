package com.furkan.kelimeoyunu.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.furkan.kelimeoyunu.room.RoomService;
import com.furkan.kelimeoyunu.websocket.RoomPresenceRegistry.RoomPresence;

@Controller
public class RoomWebSocketController {

	private final RoomService roomService;
	private final RoomPresenceRegistry presenceRegistry;

	public RoomWebSocketController(RoomService roomService, RoomPresenceRegistry presenceRegistry) {
		this.roomService = roomService;
		this.presenceRegistry = presenceRegistry;
	}

	@MessageMapping("/join")
	public void join(RoomJoinMessage message, StompHeaderAccessor headers) {
		String sessionId = headers.getSessionId();
		RoomPresence currentPresence = presenceRegistry.get(sessionId);

		if (currentPresence != null && currentPresence.matches(message.roomCode(), message.username())) {
			roomService.synchronizeRoom(message.roomCode());
			return;
		}

		if (currentPresence != null) {
			leaveCurrentPresence(currentPresence);
		}

		roomService.registerRealtimePresence(message.roomCode(), message.username());
		presenceRegistry.register(sessionId, message.roomCode(), message.username().trim());
	}

	@MessageMapping("/leave")
	public void leave(RoomLeaveMessage message, StompHeaderAccessor headers) {
		roomService.leaveRoom(message.roomCode(), message.username());
		presenceRegistry.removeIfMatching(headers.getSessionId(), message.roomCode(), message.username());
	}

	@MessageMapping("/sync")
	public void sync(RoomSyncMessage message) {
		roomService.synchronizeRoom(message.roomCode());
	}

	@MessageMapping("/start")
	public void start(StartGameMessage message) {
		roomService.startGame(message.roomCode(), message.username());
	}

	@MessageMapping("/finish")
	public void finish(FinishGameMessage message) {
		roomService.playerFinished(message.roomCode(), message.username());
	}

	@EventListener
	public void disconnect(SessionDisconnectEvent event) {
		RoomPresence presence = presenceRegistry.remove(event.getSessionId());
		if (presence != null) {
			leaveCurrentPresence(presence);
		}
	}

	private void leaveCurrentPresence(RoomPresence presence) {
		try {
			roomService.leaveRoom(presence.roomCode(), presence.username());
		} catch (ResponseStatusException ignored) {
			// The room may already be gone by the time a socket disconnect arrives.
		}
	}
}
