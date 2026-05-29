package com.furkan.kelimeoyunu.room;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.furkan.kelimeoyunu.websocket.RoomEventPublisher;

@SpringBootTest
@AutoConfigureMockMvc
class RoomControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RoomService roomService;

	@Test
	void createRoomReturnsRandomRoomCode() throws Exception {
		mockMvc.perform(post("/rooms"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.roomCode", matchesPattern("[A-Z2-9]{6}")));
	}

	@Test
	void joinRoomAddsPlayerToRoom() throws Exception {
		String roomCode = createRoom();

		mockMvc.perform(post("/rooms/{roomCode}/join", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomCode").value(roomCode))
				.andExpect(jsonPath("$.username").value("furkan"))
				.andExpect(jsonPath("$.hostUsername").value("furkan"))
				.andExpect(jsonPath("$.maxPlayers").value(Room.DEFAULT_MAX_PLAYERS))
				.andExpect(jsonPath("$.gameState").value("WAITING"))
				.andExpect(jsonPath("$.players", containsInAnyOrder("furkan")));
	}

	@Test
	void joinRoomRejectsDuplicateUsername() throws Exception {
		String roomCode = createRoom();

		mockMvc.perform(post("/rooms/{roomCode}/join", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/rooms/{roomCode}/join", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void joinRoomRejectsCaseInsensitiveDuplicateUsername() throws Exception {
		String roomCode = createRoom();

		mockMvc.perform(post("/rooms/{roomCode}/join", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"Furkan\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/rooms/{roomCode}/join", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void joinRoomRejectsBlankUsername() throws Exception {
		String roomCode = createRoom();

		mockMvc.perform(post("/rooms/{roomCode}/join", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"   \"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void joinRoomRejectsFullRoom() throws Exception {
		String roomCode = createRoom();

		for (int i = 1; i <= Room.DEFAULT_MAX_PLAYERS; i++) {
			mockMvc.perform(post("/rooms/{roomCode}/join", roomCode)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"username\":\"player" + i + "\"}"))
					.andExpect(status().isOk());
		}

		mockMvc.perform(post("/rooms/{roomCode}/join", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"extra\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void firstPlayerToJoinBecomesHost() {
		Room room = roomService.createRoom();

		roomService.joinRoom(room.code(), "furkan");
		roomService.joinRoom(room.code(), "ada");

		org.assertj.core.api.Assertions.assertThat(room.hostUsername()).isEqualTo("furkan");
	}

	@Test
	void leaveRoomRemovesPlayerAndReassignsHost() {
		Room room = roomService.createRoom();
		roomService.joinRoom(room.code(), "furkan");
		roomService.joinRoom(room.code(), "ada");

		roomService.leaveRoom(room.code(), "furkan");

		org.assertj.core.api.Assertions.assertThat(room.players()).containsExactly("ada");
		org.assertj.core.api.Assertions.assertThat(room.hostUsername()).isEqualTo("ada");
	}

	@Test
	void joinRoomPublishesPlayerJoinedEvent() {
		RoomEventPublisher publisher = mock(RoomEventPublisher.class);
		RoomService service = new RoomService(Clock.systemUTC(), publisher);
		Room room = service.createRoom();

		service.joinRoom(room.code(), "furkan");

		verify(publisher).playerJoined(room, "furkan");
	}

	@Test
	void leaveRoomPublishesPlayerLeftEvent() {
		RoomEventPublisher publisher = mock(RoomEventPublisher.class);
		RoomService service = new RoomService(Clock.systemUTC(), publisher);
		Room room = service.createRoom();
		service.joinRoom(room.code(), "furkan");

		service.leaveRoom(room.code(), "furkan");

		verify(publisher).playerLeft(room, "furkan");
	}

	@Test
	void synchronizeRoomPublishesRoomStateEvent() {
		RoomEventPublisher publisher = mock(RoomEventPublisher.class);
		RoomService service = new RoomService(Clock.systemUTC(), publisher);
		Room room = service.createRoom();

		service.synchronizeRoom(room.code());

		verify(publisher).roomState(room);
	}

	@Test
	void hostCanStartGame() throws Exception {
		String roomCode = createRoom();
		roomService.joinRoom(roomCode, "furkan");

		mockMvc.perform(post("/rooms/{roomCode}/start", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomCode").value(roomCode))
				.andExpect(jsonPath("$.gameStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.selectedLetter", matchesPattern("[A-Z]")));
	}

	@Test
	void nonHostCannotStartGame() throws Exception {
		String roomCode = createRoom();
		roomService.joinRoom(roomCode, "furkan");
		roomService.joinRoom(roomCode, "ada");

		mockMvc.perform(post("/rooms/{roomCode}/start", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"ada\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void startedGameCannotStartAgain() throws Exception {
		String roomCode = createRoom();
		roomService.joinRoom(roomCode, "furkan");
		roomService.startGame(roomCode, "furkan");

		mockMvc.perform(post("/rooms/{roomCode}/start", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void playerCanSubmitAnswers() throws Exception {
		String roomCode = createRoom();
		roomService.joinRoom(roomCode, "furkan");
		roomService.startGame(roomCode, "furkan");

		mockMvc.perform(post("/rooms/{roomCode}/answers", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\",\"answers\":{\"Name\":\"Ada\",\"City\":\"Ankara\"}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomCode").value(roomCode))
				.andExpect(jsonPath("$.username").value("furkan"))
				.andExpect(jsonPath("$.answers.Name").value("Ada"))
				.andExpect(jsonPath("$.answers.City").value("Ankara"))
				.andExpect(jsonPath("$.submittedPlayers", containsInAnyOrder("furkan")))
				.andExpect(jsonPath("$.roundScores.furkan.categoryScores.Name").value(10))
				.andExpect(jsonPath("$.roundScores.furkan.categoryScores.City").value(10))
				.andExpect(jsonPath("$.roundScores.furkan.totalScore").value(20));
	}

	@Test
	void playerCannotSubmitAnswersTwice() throws Exception {
		String roomCode = createRoom();
		roomService.joinRoom(roomCode, "furkan");
		roomService.startGame(roomCode, "furkan");

		mockMvc.perform(post("/rooms/{roomCode}/answers", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\",\"answers\":{\"Name\":\"Ada\"}}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/rooms/{roomCode}/answers", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\",\"answers\":{\"Name\":\"Ayse\"}}"))
				.andExpect(status().isConflict());
	}

	@Test
	void getRoundScoresReturnsCalculatedScores() throws Exception {
		String roomCode = createRoom();
		roomService.joinRoom(roomCode, "furkan");
		roomService.joinRoom(roomCode, "ada");
		roomService.startGame(roomCode, "furkan");
		roomService.submitAnswers(roomCode, "furkan", java.util.Map.of(
				"Name", "Ada",
				"City", ""));
		roomService.submitAnswers(roomCode, "ada", java.util.Map.of(
				"Name", "ada",
				"City", "Ankara"));

		mockMvc.perform(get("/rooms/{roomCode}/scores", roomCode))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomCode").value(roomCode))
				.andExpect(jsonPath("$.roundScores.furkan.categoryScores.Name").value(5))
				.andExpect(jsonPath("$.roundScores.furkan.categoryScores.City").value(0))
				.andExpect(jsonPath("$.roundScores.furkan.totalScore").value(5))
				.andExpect(jsonPath("$.roundScores.ada.categoryScores.Name").value(5))
				.andExpect(jsonPath("$.roundScores.ada.categoryScores.City").value(10))
				.andExpect(jsonPath("$.roundScores.ada.totalScore").value(15));
	}

	@Test
	void hostCanStartNextRoundAndPreserveTotalScores() throws Exception {
		String roomCode = createRoom();
		roomService.joinRoom(roomCode, "furkan");
		roomService.joinRoom(roomCode, "ada");
		roomService.startGame(roomCode, "furkan");
		roomService.submitAnswers(roomCode, "furkan", java.util.Map.of("Name", "Ada"));
		roomService.submitAnswers(roomCode, "ada", java.util.Map.of("Name", "ada"));
		roomService.getRoom(roomCode).finishRound();

		mockMvc.perform(post("/rooms/{roomCode}/next-round", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomCode").value(roomCode))
				.andExpect(jsonPath("$.gameStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.selectedLetter", matchesPattern("[A-Z]")))
				.andExpect(jsonPath("$.totalScores.furkan").value(5))
				.andExpect(jsonPath("$.totalScores.ada").value(5));

		mockMvc.perform(get("/rooms/{roomCode}", roomCode))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gameStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.submittedPlayers").isEmpty())
				.andExpect(jsonPath("$.answersByPlayer").isEmpty())
				.andExpect(jsonPath("$.totalScores.furkan").value(5))
				.andExpect(jsonPath("$.totalScores.ada").value(5));
	}

	@Test
	void nonHostCannotStartNextRound() throws Exception {
		String roomCode = createRoom();
		roomService.joinRoom(roomCode, "furkan");
		roomService.joinRoom(roomCode, "ada");
		roomService.startGame(roomCode, "furkan");
		roomService.getRoom(roomCode).finishRound();

		mockMvc.perform(post("/rooms/{roomCode}/next-round", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"ada\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void joinRoomRejectsUnknownRoomCode() throws Exception {
		mockMvc.perform(post("/rooms/{roomCode}/join", "ABC123")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void joinRoomNormalizesRoomCode() throws Exception {
		String roomCode = createRoom();

		mockMvc.perform(post("/rooms/{roomCode}/join", roomCode.toLowerCase())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"furkan\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomCode").value(roomCode));
	}

	@Test
	void getRoomReturnsRoomDetails() throws Exception {
		String roomCode = createRoom();
		roomService.joinRoom(roomCode, "furkan");
		roomService.joinRoom(roomCode, "ada");

		mockMvc.perform(get("/rooms/{roomCode}", roomCode))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomCode").value(roomCode))
				.andExpect(jsonPath("$.players", containsInAnyOrder("furkan", "ada")))
				.andExpect(jsonPath("$.host").value("furkan"))
				.andExpect(jsonPath("$.gameStatus").value("WAITING"));
	}

	@Test
	void getRoomNormalizesRoomCode() throws Exception {
		String roomCode = createRoom();

		mockMvc.perform(get("/rooms/{roomCode}", roomCode.toLowerCase()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomCode").value(roomCode));
	}

	@Test
	void getRoomRejectsUnknownRoomCode() throws Exception {
		mockMvc.perform(get("/rooms/{roomCode}", "ABC123"))
				.andExpect(status().isNotFound());
	}

	private String createRoom() throws Exception {
		Room room = roomService.createRoom();
		return room.code();
	}
}
