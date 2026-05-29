package com.furkan.kelimeoyunu.room;

import java.util.List;
import java.util.Map;

public record SubmitAnswersResponse(
		String roomCode,
		String username,
		Map<String, String> answers,
		List<String> submittedPlayers,
		Map<String, PlayerRoundScore> roundScores,
		Map<String, Integer> totalScores) {
}
