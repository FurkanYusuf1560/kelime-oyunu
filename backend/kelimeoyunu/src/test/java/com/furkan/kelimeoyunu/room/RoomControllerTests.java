package com.furkan.kelimeoyunu.room;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
