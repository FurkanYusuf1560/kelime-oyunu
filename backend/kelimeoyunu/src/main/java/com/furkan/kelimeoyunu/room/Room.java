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
	private final Map<String, String> players = new ConcurrentHashMap<>();
	private GameState gameState;
	private String hostUsername;
	private String selectedLetter;
	private String finishedByUsername;
	private int remainingSeconds;

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

	public String selectedLetter() {
		return selectedLetter;
	}

	public String finishedByUsername() {
		return finishedByUsername;
	}

	public int remainingSeconds() {
		return remainingSeconds;
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

	public synchronized boolean leavePlayer(String username) {
		String removedUsername = players.remove(normalizeUsername(username));
		if (removedUsername == null) {
			return false;
		}

		if (removedUsername.equals(hostUsername)) {
			hostUsername = nextHostUsername();
		}

		return true;
	}

	public boolean hasPlayers() {
		return !players.isEmpty();
	}

	public boolean hasPlayer(String username) {
		return players.containsKey(normalizeUsername(username));
	}

	public synchronized boolean startGame(String selectedLetter) {
		if (gameState != GameState.WAITING) {
			return false;
		}

		this.selectedLetter = selectedLetter;
		this.gameState = GameState.IN_PROGRESS;
		return true;
	}

	public synchronized boolean startTimer(String username, int remainingSeconds) {
		if (gameState != GameState.IN_PROGRESS || finishedByUsername != null) {
			return false;
		}

		this.finishedByUsername = username;
		this.remainingSeconds = remainingSeconds;
		return true;
	}

	public synchronized boolean updateRemainingSeconds(int remainingSeconds) {
		if (gameState != GameState.IN_PROGRESS || finishedByUsername == null) {
			return false;
		}

		this.remainingSeconds = remainingSeconds;
		return true;
	}

	public synchronized boolean finishRound() {
		if (gameState != GameState.IN_PROGRESS) {
			return false;
		}

		this.remainingSeconds = 0;
		this.gameState = GameState.FINISHED;
		return true;
	}

	public List<String> players() {
		return players.values().stream()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	private String nextHostUsername() {
		return players.values().stream()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.findFirst()
				.orElse(null);
	}

	private String normalizeUsername(String username) {
		return username.toLowerCase(Locale.ROOT);
	}
}
