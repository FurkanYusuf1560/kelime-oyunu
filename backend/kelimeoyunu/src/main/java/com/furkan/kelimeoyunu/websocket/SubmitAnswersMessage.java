package com.furkan.kelimeoyunu.websocket;

import java.util.Map;

public record SubmitAnswersMessage(String roomCode, String username, Map<String, String> answers) {
}
