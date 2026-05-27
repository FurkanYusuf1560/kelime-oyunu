package com.furkan.kelimeoyunu.websocket;

import java.util.List;

import com.furkan.kelimeoyunu.room.GameState;

public record RoomEvent(
		RoomEventType type,
		String roomCode,
		String username,
		String host,
		int maxPlayers,
		GameState gameStatus,
		String selectedLetter,
		String finishedBy,
		int remainingSeconds,
		List<String> players) {
}
