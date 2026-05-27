package com.furkan.kelimeoyunu.room;

public record StartGameResponse(String roomCode, GameState gameStatus, String selectedLetter) {
}
