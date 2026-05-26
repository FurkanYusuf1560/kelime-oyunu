package com.furkan.kelimeoyunu.room;

import java.util.Set;

public record JoinRoomResponse(String roomCode, String username, Set<String> players) {
}
