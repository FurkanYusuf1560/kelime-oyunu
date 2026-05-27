package com.furkan.kelimeoyunu.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.furkan.kelimeoyunu.room.Room;

@Component
public class RoomEventPublisher {

	private final SimpMessagingTemplate messagingTemplate;

	public RoomEventPublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	public void playerJoined(Room room, String username) {
		publish(RoomEventType.PLAYER_JOINED, room, username);
	}

	public void playerLeft(Room room, String username) {
		publish(RoomEventType.PLAYER_LEFT, room, username);
	}

	public void roomState(Room room) {
		publish(RoomEventType.ROOM_STATE, room, null);
	}

	public void gameStarted(Room room, String username) {
		publish(RoomEventType.GAME_STARTED, room, username);
	}

	public void timerStarted(Room room, String username) {
		publish(RoomEventType.TIMER_STARTED, room, username);
	}

	public void timerUpdated(Room room) {
		publish(RoomEventType.TIMER_UPDATED, room, room.finishedByUsername());
	}

	public void gameEnded(Room room) {
		publish(RoomEventType.GAME_ENDED, room, room.finishedByUsername());
	}

	private void publish(RoomEventType type, Room room, String username) {
		messagingTemplate.convertAndSend("/topic/room/" + room.code(), new RoomEvent(
				type,
				room.code(),
				username,
				room.hostUsername(),
				room.maxPlayers(),
				room.gameState(),
				room.selectedLetter(),
				room.finishedByUsername(),
				room.remainingSeconds(),
				room.players()));
	}
}
