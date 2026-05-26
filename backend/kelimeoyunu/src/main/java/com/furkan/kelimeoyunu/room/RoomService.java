package com.furkan.kelimeoyunu.room;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomService {

	private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 6;

	private final Map<String, Room> rooms = new ConcurrentHashMap<>();
	private final SecureRandom random = new SecureRandom();
	private final Clock clock;

	public RoomService() {
		this(Clock.systemUTC());
	}

	RoomService(Clock clock) {
		this.clock = clock;
	}

	public Room createRoom() {
		while (true) {
			String code = generateRoomCode();
			Room room = new Room(code, Instant.now(clock), ConcurrentHashMap.newKeySet());

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
		if (!room.players().add(normalizedUsername)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists in room");
		}

		return room;
	}

	public Set<String> getPlayers(Room room) {
		return Set.copyOf(room.players());
	}

	private String generateRoomCode() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);

		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(CODE_CHARACTERS.charAt(random.nextInt(CODE_CHARACTERS.length())));
		}

		return code.toString();
	}
}
