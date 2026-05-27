package com.furkan.kelimeoyunu.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.furkan.kelimeoyunu.room.Room;

class RoomEventPublisherTests {

	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
	private final RoomEventPublisher publisher = new RoomEventPublisher(messagingTemplate);

	@Test
	void playerJoinedPublishesRoomSnapshotToRoomTopic() {
		Room room = new Room("ABC123", Instant.parse("2026-05-27T00:00:00Z"));
		room.joinPlayer("furkan");

		publisher.playerJoined(room, "furkan");

		ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
		verify(messagingTemplate).convertAndSend(eq("/topic/room/ABC123"), eventCaptor.capture());
		RoomEvent event = eventCaptor.getValue();

		assertThat(event.type()).isEqualTo(RoomEventType.PLAYER_JOINED);
		assertThat(event.roomCode()).isEqualTo("ABC123");
		assertThat(event.username()).isEqualTo("furkan");
		assertThat(event.host()).isEqualTo("furkan");
		assertThat(event.players()).containsExactly("furkan");
	}

	@Test
	void gameStartedPublishesSelectedLetterToRoomTopic() {
		Room room = new Room("ABC123", Instant.parse("2026-05-27T00:00:00Z"));
		room.joinPlayer("furkan");
		room.startGame("K");

		publisher.gameStarted(room, "furkan");

		ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
		verify(messagingTemplate).convertAndSend(eq("/topic/room/ABC123"), eventCaptor.capture());
		RoomEvent event = eventCaptor.getValue();

		assertThat(event.type()).isEqualTo(RoomEventType.GAME_STARTED);
		assertThat(event.username()).isEqualTo("furkan");
		assertThat(event.gameStatus()).isEqualTo(com.furkan.kelimeoyunu.room.GameState.IN_PROGRESS);
		assertThat(event.selectedLetter()).isEqualTo("K");
	}

	@Test
	void timerStartedPublishesRemainingSecondsToRoomTopic() {
		Room room = new Room("ABC123", Instant.parse("2026-05-27T00:00:00Z"));
		room.joinPlayer("furkan");
		room.startGame("K");
		room.startTimer("furkan", 90);

		publisher.timerStarted(room, "furkan");

		ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
		verify(messagingTemplate).convertAndSend(eq("/topic/room/ABC123"), eventCaptor.capture());
		RoomEvent event = eventCaptor.getValue();

		assertThat(event.type()).isEqualTo(RoomEventType.TIMER_STARTED);
		assertThat(event.finishedBy()).isEqualTo("furkan");
		assertThat(event.remainingSeconds()).isEqualTo(90);
	}

	@Test
	void gameEndedPublishesFinishedStateToRoomTopic() {
		Room room = new Room("ABC123", Instant.parse("2026-05-27T00:00:00Z"));
		room.joinPlayer("furkan");
		room.startGame("K");
		room.startTimer("furkan", 1);
		room.finishRound();

		publisher.gameEnded(room);

		ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
		verify(messagingTemplate).convertAndSend(eq("/topic/room/ABC123"), eventCaptor.capture());
		RoomEvent event = eventCaptor.getValue();

		assertThat(event.type()).isEqualTo(RoomEventType.GAME_ENDED);
		assertThat(event.gameStatus()).isEqualTo(com.furkan.kelimeoyunu.room.GameState.FINISHED);
		assertThat(event.remainingSeconds()).isZero();
	}
}
