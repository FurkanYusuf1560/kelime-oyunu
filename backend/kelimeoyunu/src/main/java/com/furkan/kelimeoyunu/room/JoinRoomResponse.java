package com.furkan.kelimeoyunu.room;

import java.util.List;

public record JoinRoomResponse(
		String roomCode,
		String username,
		String hostUsername,
		int maxPlayers,
		GameState gameState,
		List<String> players) {
}
