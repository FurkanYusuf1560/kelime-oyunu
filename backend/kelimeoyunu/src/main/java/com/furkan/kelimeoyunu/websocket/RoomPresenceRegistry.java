package com.furkan.kelimeoyunu.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class RoomPresenceRegistry {

	private final Map<String, RoomPresence> sessions = new ConcurrentHashMap<>();

	public RoomPresence get(String sessionId) {
		return sessions.get(sessionId);
	}

	public void register(String sessionId, String roomCode, String username) {
		sessions.put(sessionId, new RoomPresence(roomCode, username));
	}

	public RoomPresence remove(String sessionId) {
		return sessions.remove(sessionId);
	}

	public void removeIfMatching(String sessionId, String roomCode, String username) {
		sessions.computeIfPresent(sessionId, (key, presence) -> {
			if (presence.matches(roomCode, username)) {
				return null;
			}
			return presence;
		});
	}

	public record RoomPresence(String roomCode, String username) {

		boolean matches(String otherRoomCode, String otherUsername) {
			return roomCode.equalsIgnoreCase(otherRoomCode)
					&& username.equalsIgnoreCase(otherUsername);
		}
	}
}
