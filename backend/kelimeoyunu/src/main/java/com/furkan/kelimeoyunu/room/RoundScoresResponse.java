package com.furkan.kelimeoyunu.room;

import java.util.Map;

public record RoundScoresResponse(
		String roomCode,
		Map<String, PlayerRoundScore> roundScores,
		Map<String, Integer> totalScores) {
}
