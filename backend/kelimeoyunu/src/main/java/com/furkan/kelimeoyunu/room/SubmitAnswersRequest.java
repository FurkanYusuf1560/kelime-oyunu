package com.furkan.kelimeoyunu.room;

import java.util.Map;

public record SubmitAnswersRequest(String username, Map<String, String> answers) {
}
