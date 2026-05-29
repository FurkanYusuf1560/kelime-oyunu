package com.furkan.kelimeoyunu.room;

import java.util.Map;

public record PlayerRoundScore(Map<String, Integer> categoryScores, int totalScore) {
}
