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
import java.util.Map;
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

	@Test
	void submitAnswersStoresAnswersAndBroadcastsSubmission() {
		Room room = roomService.createRoom();
		roomService.joinRoom(room.code(), "furkan");
		roomService.startGame(room.code(), "furkan");

		Room updatedRoom = roomService.submitAnswers(room.code(), "furkan", Map.of(
				"Name", " Ada ",
				"City", "Ankara"));

		Assertions.assertThat(updatedRoom.answersByPlayer().get("furkan"))
				.containsEntry("Name", "Ada")
				.containsEntry("City", "Ankara");
		Assertions.assertThat(updatedRoom.submittedPlayers()).containsExactly("furkan");
		verify(roomEventPublisher).answersSubmitted(updatedRoom, "furkan");
	}

	@Test
	void submitAnswersRejectsDuplicateSubmission() {
		Room room = roomService.createRoom();
		roomService.joinRoom(room.code(), "furkan");
		roomService.startGame(room.code(), "furkan");
		roomService.submitAnswers(room.code(), "furkan", Map.of("Name", "Ada"));

		Assertions.assertThatThrownBy(() -> roomService.submitAnswers(room.code(), "furkan", Map.of("Name", "Ayse")))
				.isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
				.hasMessageContaining("Answers already submitted");
	}

	@Test
	void calculateRoundScoresComparesAllPlayerAnswers() {
		Room room = roomService.createRoom();
		roomService.joinRoom(room.code(), "furkan");
		roomService.joinRoom(room.code(), "ada");
		roomService.joinRoom(room.code(), "efe");
		roomService.startGame(room.code(), "furkan");
		roomService.submitAnswers(room.code(), "furkan", Map.of(
				"Name", " Ada ",
				"City", "Ankara",
				"Animal", ""));
		roomService.submitAnswers(room.code(), "ada", Map.of(
				"Name", "ada",
				"City", "Izmir",
				"Animal", "Ari"));
		roomService.submitAnswers(room.code(), "efe", Map.of(
				"Name", "Ali",
				"City", "",
				"Animal", "Ari"));

		Map<String, PlayerRoundScore> roundScores = roomService.calculateRoundScores(room.code());

		Assertions.assertThat(roundScores.get("furkan").categoryScores())
				.containsEntry("Name", 5)
				.containsEntry("City", 10)
				.containsEntry("Animal", 0);
		Assertions.assertThat(roundScores.get("furkan").totalScore()).isEqualTo(15);
		Assertions.assertThat(roundScores.get("ada").categoryScores())
				.containsEntry("Name", 5)
				.containsEntry("City", 10)
				.containsEntry("Animal", 5);
		Assertions.assertThat(roundScores.get("ada").totalScore()).isEqualTo(20);
		Assertions.assertThat(roundScores.get("efe").categoryScores())
				.containsEntry("Name", 10)
				.containsEntry("City", 0)
				.containsEntry("Animal", 5);
		Assertions.assertThat(roundScores.get("efe").totalScore()).isEqualTo(15);
	}

	@Test
	void startNextRoundResetsSubmissionsGeneratesLetterAndPreservesTotalScores() {
		Room room = roomService.createRoom();
		roomService.joinRoom(room.code(), "furkan");
		roomService.joinRoom(room.code(), "ada");
		roomService.startGame(room.code(), "furkan");
		String firstLetter = room.selectedLetter();
		roomService.submitAnswers(room.code(), "furkan", Map.of(
				"Name", "Ada",
				"City", "Ankara"));
		roomService.submitAnswers(room.code(), "ada", Map.of(
				"Name", "ada",
				"City", ""));
		room.finishRound();

		Room nextRoundRoom = roomService.startNextRound(room.code(), "furkan");

		Assertions.assertThat(nextRoundRoom.gameState()).isEqualTo(GameState.IN_PROGRESS);
		Assertions.assertThat(nextRoundRoom.selectedLetter()).matches("[A-Z]");
		Assertions.assertThat(nextRoundRoom.selectedLetter()).isNotNull();
		Assertions.assertThat(firstLetter).isNotNull();
		Assertions.assertThat(nextRoundRoom.answersByPlayer()).isEmpty();
		Assertions.assertThat(nextRoundRoom.submittedPlayers()).isEmpty();
		Assertions.assertThat(nextRoundRoom.finishedByUsername()).isNull();
		Assertions.assertThat(nextRoundRoom.remainingSeconds()).isZero();
		Assertions.assertThat(nextRoundRoom.totalScores())
				.containsEntry("furkan", 15)
				.containsEntry("ada", 5);
		verify(roomEventPublisher).nextRoundStarted(nextRoundRoom, "furkan");
	}

	@Test
	void startNextRoundRequiresFinishedRound() {
		Room room = roomService.createRoom();
		roomService.joinRoom(room.code(), "furkan");
		roomService.startGame(room.code(), "furkan");

		Assertions.assertThatThrownBy(() -> roomService.startNextRound(room.code(), "furkan"))
				.isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
				.hasMessageContaining("Round is not finished");
	}
}
