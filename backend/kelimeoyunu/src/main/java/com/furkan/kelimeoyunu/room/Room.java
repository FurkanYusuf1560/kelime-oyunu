package com.furkan.kelimeoyunu.room;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Room {

	static final int DEFAULT_MAX_PLAYERS = 6;

	private final String code;
	private final Instant createdAt;
	private final int maxPlayers;
	private final GameState gameState;
	private final Map<String, String> players = new ConcurrentHashMap<>();
	private String hostUsername;

	public Room(String code, Instant createdAt) {
		this.code = code;
		this.createdAt = createdAt;
		this.maxPlayers = DEFAULT_MAX_PLAYERS;
		this.gameState = GameState.WAITING;
	}

	public String code() {
		return code;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public int maxPlayers() {
		return maxPlayers;
	}

	public GameState gameState() {
		return gameState;
	}

	public String hostUsername() {
		return hostUsername;
	}

	public synchronized JoinRoomResult joinPlayer(String username) {
		String normalizedUsername = normalizeUsername(username);

		if (players.containsKey(normalizedUsername)) {
			return JoinRoomResult.DUPLICATE_USERNAME;
		}

		if (players.size() >= maxPlayers) {
			return JoinRoomResult.ROOM_FULL;
		}

		players.put(normalizedUsername, username);
		if (hostUsername == null) {
			hostUsername = username;
		}

		return JoinRoomResult.JOINED;
	}

	public List<String> players() {
		return players.values().stream()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	private String normalizeUsername(String username) {
		return username.toLowerCase(Locale.ROOT);
	}
}
