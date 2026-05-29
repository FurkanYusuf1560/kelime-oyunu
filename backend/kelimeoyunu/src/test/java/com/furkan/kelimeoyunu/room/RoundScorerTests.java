package com.furkan.kelimeoyunu.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RoundScorerTests {

	private final RoundScorer roundScorer = new RoundScorer();

	@Test
	void scoresEmptyDuplicateAndUniqueAnswers() {
		Room room = new Room("ABC123", Instant.parse("2026-05-27T00:00:00Z"));
		room.joinPlayer("furkan");
		room.joinPlayer("ada");
		room.joinPlayer("mert");
		room.startGame("A");
		room.submitAnswers("furkan", Map.of(
				"Name", " Ada ",
				"City", "Ankara",
				"Animal", " "));
		room.submitAnswers("ada", Map.of(
				"Name", "ada",
				"City", "Antalya",
				"Animal", "Ayi"));
		room.submitAnswers("mert", Map.of(
				"Name", "Ayse",
				"City", "ANKARA",
				"Animal", "Aslan"));

		Map<String, PlayerRoundScore> roundScores = roundScorer.calculate(room);

		assertThat(roundScores.get("furkan").categoryScores())
				.containsEntry("Name", 5)
				.containsEntry("City", 5)
				.containsEntry("Animal", 0);
		assertThat(roundScores.get("furkan").totalScore()).isEqualTo(10);
		assertThat(roundScores.get("ada").categoryScores())
				.containsEntry("Name", 5)
				.containsEntry("City", 10)
				.containsEntry("Animal", 10);
		assertThat(roundScores.get("ada").totalScore()).isEqualTo(25);
		assertThat(roundScores.get("mert").categoryScores())
				.containsEntry("Name", 10)
				.containsEntry("City", 5)
				.containsEntry("Animal", 10);
		assertThat(roundScores.get("mert").totalScore()).isEqualTo(25);
	}
}
