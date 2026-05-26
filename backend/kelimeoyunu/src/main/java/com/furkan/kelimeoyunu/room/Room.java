package com.furkan.kelimeoyunu.room;

import java.time.Instant;
import java.util.Set;

public record Room(String code, Instant createdAt, Set<String> players) {
}
