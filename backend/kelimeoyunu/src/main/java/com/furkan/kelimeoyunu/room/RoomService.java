package com.furkan.kelimeoyunu.room;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.furkan.kelimeoyunu.websocket.RoomEventPublisher;

@Service
public class RoomService {

	private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 6;
	private static final String LETTER_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final int COUNTDOWN_SECONDS = 90;

	private final Map<String, Room> rooms = new ConcurrentHashMap<>();
	private final Map<String, ScheduledFuture<?>> timerTasks = new ConcurrentHashMap<>();
	private final SecureRandom random = new SecureRandom();
	private final Clock clock;
	private final RoomEventPublisher roomEventPublisher;
	private final TaskScheduler taskScheduler;
	private final int countdownSeconds;

	@Autowired
	public RoomService(RoomEventPublisher roomEventPublisher, TaskScheduler roomTaskScheduler) {
		this(Clock.systemUTC(), roomEventPublisher, roomTaskScheduler, COUNTDOWN_SECONDS);
	}

	RoomService(Clock clock) {
		this(clock, null, null, COUNTDOWN_SECONDS);
	}

	RoomService(Clock clock, RoomEventPublisher roomEventPublisher) {
		this(clock, roomEventPublisher, null, COUNTDOWN_SECONDS);
	}

	RoomService(Clock clock, RoomEventPublisher roomEventPublisher, TaskScheduler taskScheduler, int countdownSeconds) {
		this.clock = clock;
		this.roomEventPublisher = roomEventPublisher;
		this.taskScheduler = taskScheduler;
		this.countdownSeconds = countdownSeconds;
	}

	public Room createRoom() {
		while (true) {
			String code = generateRoomCode();
			Room room = new Room(code, Instant.now(clock));

			if (rooms.putIfAbsent(code, room) == null) {
				return room;
			}
		}
	}

	public Room joinRoom(String roomCode, String username) {
		if (roomCode == null || roomCode.isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
		}

		if (username == null || username.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
		}

		Room room = rooms.get(roomCode.trim().toUpperCase());
		if (room == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
		}

		String normalizedUsername = username.trim();
		JoinRoomResult result = room.joinPlayer(normalizedUsername);

		if (result == JoinRoomResult.DUPLICATE_USERNAME) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists in room");
		}

		if (result == JoinRoomResult.ROOM_FULL) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is full");
		}

		publishPlayerJoined(room, normalizedUsername);

		return room;
	}

	public Room leaveRoom(String roomCode, String username) {
		Room room = getRoom(roomCode);

		if (username == null || username.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
		}

		String normalizedUsername = username.trim();
		if (!room.leavePlayer(normalizedUsername)) {
			return room;
		}

		publishPlayerLeft(room, normalizedUsername);
		if (!room.hasPlayers()) {
			rooms.remove(room.code(), room);
			cancelTimer(room.code());
		}

		return room;
	}

	public Room getRoom(String roomCode) {
		if (roomCode == null || roomCode.isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
		}

		Room room = rooms.get(roomCode.trim().toUpperCase());
		if (room == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
		}

		return room;
	}

	public Room synchronizeRoom(String roomCode) {
		Room room = getRoom(roomCode);
		publishRoomState(room);
		return room;
	}

	public Room registerRealtimePresence(String roomCode, String username) {
		if (username == null || username.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
		}

		Room room = getRoom(roomCode);
		if (!room.hasPlayer(username.trim())) {
			return joinRoom(roomCode, username);
		}

		publishRoomState(room);
		return room;
	}

	public Room startGame(String roomCode, String username) {
		Room room = getRoom(roomCode);

		if (username == null || username.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
		}

		String normalizedUsername = username.trim();
		if (!room.hasPlayer(normalizedUsername)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only room players can start the game");
		}

		if (!normalizedUsername.equalsIgnoreCase(room.hostUsername())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only host can start the game");
		}

		if (!room.startGame(generateLetter())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Game already started");
		}

		publishGameStarted(room, normalizedUsername);
		return room;
	}

	public Room playerFinished(String roomCode, String username) {
		Room room = getRoom(roomCode);

		if (username == null || username.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
		}

		String normalizedUsername = username.trim();
		if (!room.hasPlayer(normalizedUsername)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only room players can finish");
		}

		if (room.gameState() != GameState.IN_PROGRESS) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Game is not in progress");
		}

		if (!room.startTimer(normalizedUsername, countdownSeconds)) {
			publishRoomState(room);
			return room;
		}

		publishTimerStarted(room, normalizedUsername);
		scheduleCountdown(room);
		return room;
	}

	private String generateRoomCode() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);

		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(CODE_CHARACTERS.charAt(random.nextInt(CODE_CHARACTERS.length())));
		}

		return code.toString();
	}

	private String generateLetter() {
		return String.valueOf(LETTER_CHARACTERS.charAt(random.nextInt(LETTER_CHARACTERS.length())));
	}

	private void scheduleCountdown(Room room) {
		if (taskScheduler == null) {
			return;
		}

		AtomicInteger remainingSeconds = new AtomicInteger(countdownSeconds);
		ScheduledFuture<?> timerTask = taskScheduler.scheduleAtFixedRate(() -> tickCountdown(room.code(), remainingSeconds),
				Instant.now(clock).plusSeconds(1),
				Duration.ofSeconds(1));
		ScheduledFuture<?> previousTask = timerTasks.put(room.code(), timerTask);
		if (previousTask != null) {
			previousTask.cancel(false);
		}
	}

	private void tickCountdown(String roomCode, AtomicInteger remainingSeconds) {
		Room room = rooms.get(roomCode);
		if (room == null) {
			cancelTimer(roomCode);
			return;
		}

		int secondsLeft = remainingSeconds.decrementAndGet();
		if (secondsLeft > 0) {
			room.updateRemainingSeconds(secondsLeft);
			publishTimerUpdated(room);
			return;
		}

		room.finishRound();
		publishGameEnded(room);
		cancelTimer(roomCode);
	}

	private void cancelTimer(String roomCode) {
		ScheduledFuture<?> timerTask = timerTasks.remove(roomCode);
		if (timerTask != null) {
			timerTask.cancel(false);
		}
	}

	private void publishPlayerJoined(Room room, String username) {
		if (roomEventPublisher != null) {
			roomEventPublisher.playerJoined(room, username);
		}
	}

	private void publishPlayerLeft(Room room, String username) {
		if (roomEventPublisher != null) {
			roomEventPublisher.playerLeft(room, username);
		}
	}

	private void publishRoomState(Room room) {
		if (roomEventPublisher != null) {
			roomEventPublisher.roomState(room);
		}
	}

	private void publishGameStarted(Room room, String username) {
		if (roomEventPublisher != null) {
			roomEventPublisher.gameStarted(room, username);
		}
	}

	private void publishTimerStarted(Room room, String username) {
		if (roomEventPublisher != null) {
			roomEventPublisher.timerStarted(room, username);
		}
	}

	private void publishTimerUpdated(Room room) {
		if (roomEventPublisher != null) {
			roomEventPublisher.timerUpdated(room);
		}
	}

	private void publishGameEnded(Room room) {
		if (roomEventPublisher != null) {
			roomEventPublisher.gameEnded(room);
		}
	}
}
