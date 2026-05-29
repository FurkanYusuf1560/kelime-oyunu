package com.furkan.kelimeoyunu.room;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
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
	private final Map<String, Map<String, String>> answersByPlayer = new ConcurrentHashMap<>();
	private final Map<String, Integer> totalScoresByPlayer = new ConcurrentHashMap<>();
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
		String normalizedUsername = normalizeUsername(username);
		String removedUsername = players.remove(normalizedUsername);
		if (removedUsername == null) {
			return false;
		}
		answersByPlayer.remove(normalizedUsername);
		totalScoresByPlayer.remove(normalizedUsername);

		if (removedUsername.equals(hostUsername)) {
			hostUsername = nextHostUsername();
		}

		return true;
	}

	public synchronized boolean startNextRound(String selectedLetter, Map<String, PlayerRoundScore> roundScores) {
		if (gameState != GameState.FINISHED) {
			return false;
		}

		if (roundScores != null) {
			roundScores.forEach((username, score) -> {
				if (hasPlayer(username) && score != null) {
					totalScoresByPlayer.merge(normalizeUsername(username), score.totalScore(), Integer::sum);
				}
			});
		}
		answersByPlayer.clear();
		this.finishedByUsername = null;
		this.remainingSeconds = 0;
		this.selectedLetter = selectedLetter;
		this.gameState = GameState.IN_PROGRESS;
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

	public synchronized boolean submitAnswers(String username, Map<String, String> answers) {
		String normalizedUsername = normalizeUsername(username);
		if (gameState != GameState.IN_PROGRESS || answersByPlayer.containsKey(normalizedUsername)) {
			return false;
		}

		answersByPlayer.put(normalizedUsername, normalizeAnswers(answers));
		return true;
	}

	public boolean hasSubmittedAnswers(String username) {
		return answersByPlayer.containsKey(normalizeUsername(username));
	}

	public Map<String, Map<String, String>> answersByPlayer() {
		Map<String, Map<String, String>> snapshot = new HashMap<>();
		answersByPlayer.forEach((username, answers) -> snapshot.put(username, Map.copyOf(answers)));
		return Collections.unmodifiableMap(snapshot);
	}

	public Map<String, String> answersForPlayer(String username) {
		Map<String, String> answers = answersByPlayer.get(normalizeUsername(username));
		if (answers == null) {
			return Map.of();
		}

		return Map.copyOf(answers);
	}

	public List<String> submittedPlayers() {
		return answersByPlayer.keySet().stream()
				.map(players::get)
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	public Map<String, Integer> totalScores() {
		Map<String, Integer> snapshot = new HashMap<>();
		players.forEach((username, displayUsername) ->
				snapshot.put(displayUsername, totalScoresByPlayer.getOrDefault(username, 0)));
		return Collections.unmodifiableMap(snapshot);
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

	private Map<String, String> normalizeAnswers(Map<String, String> answers) {
		Map<String, String> normalizedAnswers = new HashMap<>();
		if (answers == null) {
			return normalizedAnswers;
		}

		answers.forEach((category, answer) -> {
			if (category != null && !category.isBlank()) {
				normalizedAnswers.put(category.trim(), answer == null ? "" : answer.trim());
			}
		});
		return normalizedAnswers;
	}

	private String normalizeUsername(String username) {
		return username.toLowerCase(Locale.ROOT);
	}
}
