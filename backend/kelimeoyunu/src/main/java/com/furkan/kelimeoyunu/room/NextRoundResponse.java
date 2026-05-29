package com.furkan.kelimeoyunu.room;

import java.util.Map;

public record NextRoundResponse(
		String roomCode,
		GameState gameStatus,
		String selectedLetter,
		Map<String, Integer> totalScores) {
}
