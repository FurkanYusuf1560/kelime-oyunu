package com.furkan.kelimeoyunu.room;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import com.furkan.kelimeoyunu.websocket.RoomEventPublisher;

class RoomServiceRealtimeTests {

	private final RoomEventPublisher roomEventPublisher = mock(RoomEventPublisher.class);
	private final RoomService roomService = new RoomService(
			Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneOffset.UTC),
			roomEventPublisher);

	@Test
	void joinRoomBroadcastsPlayerJoined() {
		Room room = roomService.createRoom();

		Room joinedRoom = roomService.joinRoom(room.code(), "furkan");

		verify(roomEventPublisher).playerJoined(joinedRoom, "furkan");
	}

	@Test
	void leaveRoomBroadcastsPlayerLeft() {
		Room room = roomService.createRoom();
		roomService.joinRoom(room.code(), "furkan");

		Room updatedRoom = roomService.leaveRoom(room.code(), "furkan");

		verify(roomEventPublisher).playerLeft(updatedRoom, "furkan");
	}

	@Test
	void startGameBroadcastsGameStarted() {
		Room room = roomService.createRoom();
		roomService.joinRoom(room.code(), "furkan");

		Room startedRoom = roomService.startGame(room.code(), "furkan");

		verify(roomEventPublisher).gameStarted(startedRoom, "furkan");
	}

	@Test
	void playerFinishedStartsCountdownAndEndsRound() {
		RoomEventPublisher publisher = mock(RoomEventPublisher.class);
		TaskScheduler scheduler = mock(TaskScheduler.class);
		ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
		ArgumentCaptor<Runnable> countdownCaptor = ArgumentCaptor.forClass(Runnable.class);
		doReturn(scheduledFuture)
				.when(scheduler)
				.scheduleAtFixedRate(countdownCaptor.capture(), any(Instant.class), eq(Duration.ofSeconds(1)));
		RoomService service = new RoomService(
				Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneOffset.UTC),
				publisher,
				scheduler,
				2);
		Room room = service.createRoom();
		service.joinRoom(room.code(), "furkan");
		service.startGame(room.code(), "furkan");

		Room updatedRoom = service.playerFinished(room.code(), "furkan");
		countdownCaptor.getValue().run();
		countdownCaptor.getValue().run();

		Assertions.assertThat(updatedRoom.remainingSeconds()).isZero();
		Assertions.assertThat(updatedRoom.gameState()).isEqualTo(GameState.FINISHED);
		verify(publisher).timerStarted(updatedRoom, "furkan");
		verify(publisher).timerUpdated(updatedRoom);
		verify(publisher).gameEnded(updatedRoom);
		verify(scheduledFuture).cancel(false);
	}
}
