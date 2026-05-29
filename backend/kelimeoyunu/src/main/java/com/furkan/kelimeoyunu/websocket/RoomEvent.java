package com.furkan.kelimeoyunu.websocket;

import java.util.List;
import java.util.Map;

import com.furkan.kelimeoyunu.room.GameState;
import com.furkan.kelimeoyunu.room.PlayerRoundScore;

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
		List<String> submittedPlayers,
		List<String> players,
		Map<String, Map<String, String>> answersByPlayer,
		Map<String, PlayerRoundScore> roundScores,
		Map<String, Integer> totalScores) {
}
