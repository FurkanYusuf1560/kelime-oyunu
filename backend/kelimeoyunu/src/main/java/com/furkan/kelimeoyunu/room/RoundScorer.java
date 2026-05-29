package com.furkan.kelimeoyunu.room;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class RoundScorer {

	public Map<String, PlayerRoundScore> calculate(Room room) {
		Map<String, Map<String, String>> answersByPlayer = room.answersByPlayer();
		Map<String, String> categories = collectCategories(answersByPlayer);
		Map<String, Map<String, Integer>> answerCountsByCategory = countAnswersByCategory(categories, answersByPlayer);
		Map<String, PlayerRoundScore> roundScores = new LinkedHashMap<>();

		for (String player : room.players()) {
			Map<String, String> playerAnswers = room.answersForPlayer(player);
			Map<String, Integer> categoryScores = new LinkedHashMap<>();
			int totalScore = 0;

			for (Map.Entry<String, String> category : categories.entrySet()) {
				String answer = findAnswer(playerAnswers, category.getKey());
				int score = scoreAnswer(answer, answerCountsByCategory.getOrDefault(category.getKey(), Map.of()));
				categoryScores.put(category.getValue(), score);
				totalScore += score;
			}

			roundScores.put(player, new PlayerRoundScore(categoryScores, totalScore));
		}

		return roundScores;
	}

	private Map<String, String> collectCategories(Map<String, Map<String, String>> answersByPlayer) {
		Map<String, String> categories = new LinkedHashMap<>();
		answersByPlayer.values().forEach(answers -> answers.keySet().stream()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.forEach(category -> categories.putIfAbsent(normalizeValue(category), category)));
		return categories;
	}

	private Map<String, Map<String, Integer>> countAnswersByCategory(
			Map<String, String> categories,
			Map<String, Map<String, String>> answersByPlayer) {
		Map<String, Map<String, Integer>> answerCountsByCategory = new HashMap<>();

		for (String categoryKey : categories.keySet()) {
			Map<String, Integer> answerCounts = new HashMap<>();
			Set<String> playersSeen = new HashSet<>();
			answersByPlayer.forEach((player, answers) -> {
				String answer = normalizeValue(findAnswer(answers, categoryKey));
				if (!answer.isBlank() && playersSeen.add(player)) {
					answerCounts.merge(answer, 1, Integer::sum);
				}
			});
			answerCountsByCategory.put(categoryKey, answerCounts);
		}

		return answerCountsByCategory;
	}

	private String findAnswer(Map<String, String> answers, String normalizedCategory) {
		if (answers == null) {
			return "";
		}

		return answers.entrySet().stream()
				.filter(entry -> normalizeValue(entry.getKey()).equals(normalizedCategory))
				.map(Map.Entry::getValue)
				.findFirst()
				.orElse("");
	}

	private int scoreAnswer(String answer, Map<String, Integer> answerCounts) {
		String normalizedAnswer = normalizeValue(answer);
		if (normalizedAnswer.isBlank()) {
			return 0;
		}

		return answerCounts.getOrDefault(normalizedAnswer, 0) > 1 ? 5 : 10;
	}

	private String normalizeValue(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}
