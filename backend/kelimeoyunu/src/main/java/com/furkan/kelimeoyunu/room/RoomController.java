package com.furkan.kelimeoyunu.room;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomController {

	private final RoomService roomService;

	public RoomController(RoomService roomService) {
		this.roomService = roomService;
	}

	@PostMapping("/rooms")
	public ResponseEntity<RoomResponse> createRoom() {
		Room room = roomService.createRoom();
		return ResponseEntity.status(HttpStatus.CREATED).body(new RoomResponse(room.code()));
	}

	@PostMapping("/rooms/{roomCode}/join")
	public ResponseEntity<JoinRoomResponse> joinRoom(@PathVariable String roomCode, @RequestBody JoinRoomRequest request) {
		Room room = roomService.joinRoom(roomCode, request.username());
		return ResponseEntity.ok(new JoinRoomResponse(
				room.code(),
				request.username().trim(),
				room.hostUsername(),
				room.maxPlayers(),
				room.gameState(),
				room.players()));
	}

	@GetMapping("/rooms/{roomCode}")
	public ResponseEntity<RoomDetailsResponse> getRoom(@PathVariable String roomCode) {
		Room room = roomService.getRoom(roomCode);
		return ResponseEntity.ok(new RoomDetailsResponse(
				room.code(),
				room.players(),
				room.hostUsername(),
				room.gameState(),
				room.answersByPlayer(),
				room.submittedPlayers(),
				roomService.calculateRoundScores(room),
				room.totalScores()));
	}

	@PostMapping("/rooms/{roomCode}/start")
	public ResponseEntity<StartGameResponse> startGame(@PathVariable String roomCode, @RequestBody StartGameRequest request) {
		Room room = roomService.startGame(roomCode, request.username());
		return ResponseEntity.ok(new StartGameResponse(
				room.code(),
				room.gameState(),
				room.selectedLetter()));
	}

	@PostMapping("/rooms/{roomCode}/answers")
	public ResponseEntity<SubmitAnswersResponse> submitAnswers(@PathVariable String roomCode, @RequestBody SubmitAnswersRequest request) {
		Room room = roomService.submitAnswers(roomCode, request.username(), request.answers());
		return ResponseEntity.ok(new SubmitAnswersResponse(
				room.code(),
				request.username().trim(),
				room.answersForPlayer(request.username()),
				room.submittedPlayers(),
				roomService.calculateRoundScores(room),
				room.totalScores()));
	}

	@GetMapping("/rooms/{roomCode}/scores")
	public ResponseEntity<RoundScoresResponse> getRoundScores(@PathVariable String roomCode) {
		Room room = roomService.getRoom(roomCode);
		return ResponseEntity.ok(new RoundScoresResponse(room.code(), roomService.calculateRoundScores(roomCode), room.totalScores()));
	}

	@PostMapping("/rooms/{roomCode}/next-round")
	public ResponseEntity<NextRoundResponse> startNextRound(@PathVariable String roomCode, @RequestBody NextRoundRequest request) {
		Room room = roomService.startNextRound(roomCode, request.username());
		return ResponseEntity.ok(new NextRoundResponse(
				room.code(),
				room.gameState(),
				room.selectedLetter(),
				room.totalScores()));
	}
}
