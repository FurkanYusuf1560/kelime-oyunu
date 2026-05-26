package com.furkan.kelimeoyunu.room;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RoomControllerTests {

	@Autowired
	private MockMvc mockMvc;

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

	private String createRoom() throws Exception {
		return mockMvc.perform(post("/rooms"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.roomCode", notNullValue()))
				.andReturn()
				.getResponse()
				.getContentAsString()
				.replaceFirst(".*\"roomCode\":\"([^\"]+)\".*", "$1");
	}
}
