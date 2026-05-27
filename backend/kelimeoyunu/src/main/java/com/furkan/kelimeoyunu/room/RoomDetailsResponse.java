package com.furkan.kelimeoyunu.room;

import java.util.List;

public record RoomDetailsResponse(String roomCode, List<String> players, String host, GameState gameStatus) {
}
