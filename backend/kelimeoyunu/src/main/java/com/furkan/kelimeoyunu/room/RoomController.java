package com.furkan.kelimeoyunu.room;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomController {

	private final RoomService roomService;

	public RoomController(RoomService roomService) {
		this.roomService = roomService;
	}

	@PostMapping("/rooms")
	@ResponseStatus(HttpStatus.CREATED)
	public RoomResponse createRoom() {
		Room room = roomService.createRoom();
		return new RoomResponse(room.code());
	}

	@PostMapping("/rooms/{roomCode}/join")
	public JoinRoomResponse joinRoom(@PathVariable String roomCode, @RequestBody JoinRoomRequest request) {
		Room room = roomService.joinRoom(roomCode, request.username());
		return new JoinRoomResponse(room.code(), request.username().trim(), roomService.getPlayers(room));
	}
}
