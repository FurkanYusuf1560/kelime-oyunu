import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useCallback, useEffect, useRef, useState } from 'react'
import './App.css'

const GAME_CATEGORIES = ['Name', 'City', 'Country', 'Animal', 'Plant', 'Object']
const ROUND_SECONDS = 90

function App() {
  const [createdRoomCode, setCreatedRoomCode] = useState('')
  const [createStatus, setCreateStatus] = useState('idle')
  const [createError, setCreateError] = useState('')
  const [joinForm, setJoinForm] = useState({ roomCode: '', username: '' })
  const [joinStatus, setJoinStatus] = useState('idle')
  const [joinMessage, setJoinMessage] = useState('')
  const [gameRoom, setGameRoom] = useState(null)
  const [roomConnection, setRoomConnection] = useState(null)
  const [startMessage, setStartMessage] = useState('')

  const handleRoomUpdate = useCallback((event, connection) => {
    if (!event.roomCode || event.roomCode !== connection.roomCode) {
      return
    }

    setGameRoom({
      roomCode: event.roomCode,
      currentUsername: connection.username,
      hostUsername: event.hostUsername ?? event.host,
      maxPlayers: event.maxPlayers,
      gameState: event.gameState ?? event.gameStatus,
      selectedLetter: event.selectedLetter ?? null,
      players: event.players ?? [],
    })
    setJoinMessage('')
    setJoinStatus('success')
  }, [])

  const handleConnectionError = useCallback((message) => {
    setJoinMessage(message)
    setJoinStatus((current) => (current === 'success' ? current : 'error'))
  }, [])

  const roomSocket = useRoomSocket(roomConnection, {
    onRoomUpdate: handleRoomUpdate,
    onConnectionError: handleConnectionError,
  })

  async function handleCreateRoom(event) {
    event.preventDefault()
    setCreateStatus('loading')
    setCreateError('')
    setCreatedRoomCode('')

    try {
      const response = await fetch('/rooms', { method: 'POST' })

      if (!response.ok) {
        throw new Error('Room could not be created.')
      }

      const data = await response.json()
      setCreatedRoomCode(data.roomCode)
      setJoinForm((current) => ({ ...current, roomCode: data.roomCode }))
      setCreateStatus('success')
    } catch (error) {
      setCreateError(error.message)
      setCreateStatus('error')
    }
  }

  async function handleJoinRoom(event) {
    event.preventDefault()
    setJoinStatus('loading')
    setJoinMessage('')

    try {
      const roomCode = joinForm.roomCode.trim()
      const username = joinForm.username.trim()

      if (!roomCode || !username) {
        throw new Error('Room code and username are required.')
      }

      const response = await fetch(`/rooms/${encodeURIComponent(roomCode)}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username }),
      })

      if (response.status === 404) {
        throw new Error('Room not found.')
      }

      if (response.status === 409) {
        throw new Error('This username is already in the room.')
      }

      if (!response.ok) {
        throw new Error('Room could not be joined.')
      }

      const data = await response.json()
      setGameRoom({
        roomCode: data.roomCode,
        currentUsername: data.username,
        hostUsername: data.hostUsername,
        maxPlayers: data.maxPlayers,
        gameState: data.gameState,
        selectedLetter: data.selectedLetter ?? null,
        players: data.players,
      })
      setRoomConnection({ roomCode: data.roomCode, username: data.username })
      setJoinMessage('')
      setJoinStatus('success')
    } catch (error) {
      setJoinMessage(error.message)
      setJoinStatus('error')
    }
  }

  function updateJoinForm(field, value) {
    setJoinForm((current) => ({ ...current, [field]: value }))
  }

  function handleLeaveRoom() {
    roomSocket.leaveRoom()
    setRoomConnection(null)
    setGameRoom(null)
    setStartMessage('')
    setJoinStatus('idle')
    setJoinMessage('')
  }

  function handleStartGame() {
    roomSocket.startGame()
    setStartMessage('')
  }

  if (gameRoom) {
    return (
      <GameRoomPage
        room={gameRoom}
        connectionStatus={roomSocket.status}
        connectionError={roomSocket.error}
        startMessage={startMessage}
        onLeaveRoom={handleLeaveRoom}
        onStartGame={handleStartGame}
      />
    )
  }

  return (
    <main className="home-page">
      <section className="intro">
        <p className="eyebrow">Kelime Oyunu</p>
        <h1>Start a word room in seconds.</h1>
        <p className="intro-copy">
          Create a private room code, invite your friends, then join with a
          username before the game begins.
        </p>
      </section>

      <section className="room-actions" aria-label="Room actions">
        <form className="room-panel create-panel" onSubmit={handleCreateRoom}>
          <div>
            <p className="panel-kicker">Host</p>
            <h2>Create Room</h2>
            <p className="panel-copy">Generate a fresh code for a new room.</p>
          </div>

          <button
            type="submit"
            className="primary-button"
            disabled={createStatus === 'loading'}
          >
            {createStatus === 'loading' ? 'Creating...' : 'Create Room'}
          </button>

          <output
            className={`result-box ${createStatus === 'error' ? 'error' : ''}`}
            aria-live="polite"
          >
            {createdRoomCode ? (
              <>
                <span>Room code</span>
                <strong>{createdRoomCode}</strong>
              </>
            ) : (
              <span>
                {createError || 'Your room code will appear here.'}
              </span>
            )}
          </output>
        </form>

        <form className="room-panel join-panel" onSubmit={handleJoinRoom}>
          <div>
            <p className="panel-kicker">Player</p>
            <h2>Join Room</h2>
            <p className="panel-copy">Enter a room code and pick a username.</p>
          </div>

          <label>
            <span>Room code</span>
            <input
              type="text"
              value={joinForm.roomCode}
              onChange={(event) => updateJoinForm('roomCode', event.target.value)}
              placeholder="ABC123"
              maxLength={12}
              required
            />
          </label>

          <label>
            <span>Username</span>
            <input
              type="text"
              value={joinForm.username}
              onChange={(event) => updateJoinForm('username', event.target.value)}
              placeholder="furkan"
              maxLength={24}
              required
            />
          </label>

          <button
            type="submit"
            className="secondary-button"
            disabled={joinStatus === 'loading'}
          >
            {joinStatus === 'loading' ? 'Joining...' : 'Join Room'}
          </button>

          <output
            className={`join-message ${joinStatus === 'error' ? 'error' : ''}`}
            aria-live="polite"
          >
            {joinMessage}
          </output>
        </form>
      </section>
    </main>
  )
}

function useRoomSocket(roomConnection, { onRoomUpdate, onConnectionError }) {
  const clientRef = useRef(null)
  const roomConnectionRef = useRef(null)
  const intentionalDisconnectRef = useRef(false)
  const [status, setStatus] = useState('idle')
  const [error, setError] = useState('')

  useEffect(() => {
    roomConnectionRef.current = roomConnection
  }, [roomConnection])

  useEffect(() => {
    if (!roomConnection) {
      intentionalDisconnectRef.current = true
      clientRef.current?.deactivate()
      clientRef.current = null
      return undefined
    }

    intentionalDisconnectRef.current = false

    const client = new Client({
      reconnectDelay: 3000,
      webSocketFactory: () => new SockJS('/ws'),
      debug: () => {},
      onConnect: () => {
        setStatus('connected')
        setError('')

        client.subscribe(`/topic/room/${roomConnection.roomCode}`, (message) => {
          try {
            const event = JSON.parse(message.body)
            onRoomUpdate(event, roomConnection)
          } catch {
            setError('Room update could not be read.')
          }
        })

        client.publish({
          destination: '/app/join',
          body: JSON.stringify(roomConnection),
        })
      },
      onStompError: (frame) => {
        const message = frame.headers.message || 'Room connection failed.'
        setStatus('error')
        setError(message)
        onConnectionError(message)
      },
      onWebSocketClose: () => {
        if (!intentionalDisconnectRef.current) {
          setStatus('reconnecting')
          setError('Connection lost. Reconnecting...')
        }
      },
      onWebSocketError: () => {
        setStatus('error')
        setError('Room connection failed.')
        onConnectionError('Room connection failed.')
      },
    })

    clientRef.current = client
    client.activate()

    return () => {
      intentionalDisconnectRef.current = true
      client.deactivate()
    }
  }, [onConnectionError, onRoomUpdate, roomConnection])

  const leaveRoom = useCallback(() => {
    const client = clientRef.current
    const currentRoom = roomConnectionRef.current

    intentionalDisconnectRef.current = true

    if (client?.connected && currentRoom) {
      client.publish({
        destination: '/app/leave',
        body: JSON.stringify(currentRoom),
      })
    }

    client?.deactivate()
    clientRef.current = null
    setStatus('idle')
    setError('')
  }, [])

  const startGame = useCallback(() => {
    const client = clientRef.current
    const currentRoom = roomConnectionRef.current

    if (client?.connected && currentRoom) {
      client.publish({
        destination: '/app/start',
        body: JSON.stringify(currentRoom),
      })
    }
  }, [])

  const visibleStatus = roomConnection && status === 'idle' ? 'connecting' : status

  return { error, leaveRoom, startGame, status: visibleStatus }
}

function GameRoomPage({
  room,
  connectionStatus,
  connectionError,
  startMessage,
  onLeaveRoom,
  onStartGame,
}) {
  const isHost = room.currentUsername === room.hostUsername
  const isInProgress = room.gameState === 'IN_PROGRESS'
  const playerCount = room.players.length
  const maxPlayers = room.maxPlayers || playerCount

  return (
    <main className="min-h-svh bg-[var(--bg)] px-5 py-6 text-[var(--text)] sm:px-8 lg:px-10">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-6">
        <header className="flex flex-col gap-4 rounded-lg border border-[var(--border)] bg-[var(--panel-bg)] p-5 sm:flex-row sm:items-center sm:justify-between sm:p-6">
          <div>
            <p className="text-xs font-bold uppercase text-[var(--accent)]">
              {room.gameState === 'WAITING' ? 'Waiting Room' : room.gameState}
            </p>
            <h1 className="mt-2 text-3xl font-semibold leading-tight text-[var(--text-h)] sm:text-4xl">
              Room {room.roomCode}
            </h1>
            <ConnectionStatus status={connectionStatus} error={connectionError} />
          </div>

          <div className="flex flex-wrap gap-3">
            {isHost && !isInProgress && (
              <button
                type="button"
                onClick={onStartGame}
                className="min-h-11 rounded-md bg-[var(--button-bg)] px-5 text-sm font-bold text-white transition hover:-translate-y-0.5 disabled:opacity-60 dark:text-slate-950"
              >
                Start Game
              </button>
            )}
            <button
              type="button"
              onClick={onLeaveRoom}
              className="min-h-11 rounded-md border border-[var(--border)] px-5 text-sm font-bold text-[var(--text-h)] transition hover:-translate-y-0.5"
            >
              Leave
            </button>
          </div>
        </header>

        <section className="grid gap-6 lg:grid-cols-[1fr_360px]">
          {isInProgress ? (
            <GameScreen
              key={`${room.roomCode}-${room.selectedLetter}`}
              selectedLetter={room.selectedLetter}
            />
          ) : (
            <PlayersPanel
              playerCount={playerCount}
              maxPlayers={maxPlayers}
              players={room.players}
              hostUsername={room.hostUsername}
              currentUsername={room.currentUsername}
            />
          )}

          <aside className="flex flex-col gap-4">
            <RoomInfoCard label="Room code" value={room.roomCode} />
            <RoomInfoCard label="Host" value={room.hostUsername} />
            <RoomInfoCard label="You" value={room.currentUsername} />
            {isInProgress && (
              <RoomInfoCard label="Players" value={`${playerCount} / ${maxPlayers}`} />
            )}

            <div className="rounded-lg border border-[var(--border)] bg-[var(--panel-bg)] p-5">
              <p className="text-xs font-bold uppercase text-[var(--accent)]">
                Status
              </p>
              <p className="mt-3 text-lg font-semibold text-[var(--text-h)]">
                {isInProgress
                  ? 'Round in progress.'
                  : isHost
                    ? 'Ready when you are.'
                    : 'Waiting for the host to start.'}
              </p>
              <p className="mt-2 text-sm">
                {isInProgress
                  ? 'Submit your answers before the timer closes.'
                  : isHost
                  ? 'Share the room code, wait for players, then start the game.'
                  : 'Keep this page open while the host prepares the game.'}
              </p>
              {startMessage && (
                <p className="mt-4 rounded-md bg-[var(--result-bg)] p-3 text-sm font-bold text-[var(--success)]">
                  {startMessage}
                </p>
              )}
            </div>
          </aside>
        </section>
      </div>
    </main>
  )
}

function PlayersPanel({ playerCount, maxPlayers, players, hostUsername, currentUsername }) {
  return (
    <div className="rounded-lg border border-[var(--border)] bg-[var(--panel-bg)] p-5 sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase text-[var(--accent)]">
            Players
          </p>
          <h2 className="mt-2 text-2xl font-semibold text-[var(--text-h)]">
            Connected players
          </h2>
        </div>
        <div className="rounded-md bg-[var(--result-bg)] px-4 py-3 text-sm font-bold text-[var(--text-h)]">
          {playerCount} / {maxPlayers} players
        </div>
      </div>

      <PlayerList
        players={players}
        hostUsername={hostUsername}
        currentUsername={currentUsername}
      />
    </div>
  )
}

function GameScreen({ selectedLetter }) {
  const [answers, setAnswers] = useState(() => createEmptyAnswers())
  const [submitted, setSubmitted] = useState(false)

  function updateAnswer(category, value) {
    setAnswers((current) => ({ ...current, [category]: value }))
  }

  function handleFinish(event) {
    event.preventDefault()
    setSubmitted(true)
  }

  return (
    <form
      className="rounded-lg border border-[var(--border)] bg-[var(--panel-bg)] p-5 sm:p-6"
      onSubmit={handleFinish}
    >
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <LetterDisplay letter={selectedLetter} />
        <Countdown initialSeconds={ROUND_SECONDS} isPaused={submitted} />
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        {GAME_CATEGORIES.map((category) => (
          <label
            className="grid gap-2 text-sm font-bold text-[var(--text-h)]"
            key={category}
          >
            <span>{category}</span>
            <input
              className="min-h-12 rounded-md border border-[var(--border)] bg-[var(--input-bg)] px-4 text-[var(--text-h)] outline-none transition focus:border-[var(--accent)] focus:ring-4 focus:ring-[var(--accent-bg)] disabled:opacity-70"
              disabled={submitted}
              maxLength={32}
              onChange={(event) => updateAnswer(category, event.target.value)}
              type="text"
              value={answers[category]}
            />
          </label>
        ))}
      </div>

      <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="min-h-6 text-sm font-semibold text-[var(--success)]">
          {submitted ? 'Answers submitted.' : ''}
        </p>
        <button
          className="min-h-11 rounded-md bg-[var(--button-bg)] px-6 text-sm font-bold text-white transition hover:-translate-y-0.5 disabled:cursor-default disabled:opacity-60 disabled:hover:translate-y-0 dark:text-slate-950"
          disabled={submitted}
          type="submit"
        >
          Finish
        </button>
      </div>
    </form>
  )
}

function LetterDisplay({ letter }) {
  return (
    <section aria-label="Selected letter">
      <p className="text-xs font-bold uppercase text-[var(--accent)]">
        Selected letter
      </p>
      <p className="mt-2 font-mono text-6xl font-bold leading-none text-[var(--text-h)]">
        {letter || '?'}
      </p>
    </section>
  )
}

function Countdown({ initialSeconds, isPaused }) {
  const [secondsLeft, setSecondsLeft] = useState(initialSeconds)

  useEffect(() => {
    if (isPaused || secondsLeft <= 0) {
      return undefined
    }

    const timerId = window.setInterval(() => {
      setSecondsLeft((current) => Math.max(current - 1, 0))
    }, 1000)

    return () => window.clearInterval(timerId)
  }, [isPaused, secondsLeft])

  const minutes = String(Math.floor(secondsLeft / 60)).padStart(2, '0')
  const seconds = String(secondsLeft % 60).padStart(2, '0')

  return (
    <section
      aria-label="Countdown timer"
      className="min-w-40 rounded-lg border border-[var(--border)] bg-[var(--result-bg)] p-4 text-center"
    >
      <p className="text-xs font-bold uppercase text-[var(--accent)]">
        Countdown
      </p>
      <p className="mt-2 font-mono text-3xl font-bold leading-none text-[var(--text-h)]">
        {minutes}:{seconds}
      </p>
    </section>
  )
}

function createEmptyAnswers() {
  return Object.fromEntries(GAME_CATEGORIES.map((category) => [category, '']))
}

function ConnectionStatus({ status, error }) {
  const statusText = {
    connected: 'Live',
    connecting: 'Connecting',
    reconnecting: 'Reconnecting',
    error: 'Offline',
    idle: 'Offline',
  }[status]

  const statusClass =
    status === 'connected'
      ? 'bg-[var(--accent-bg)] text-[var(--accent)]'
      : 'bg-[var(--result-bg)] text-[var(--danger)]'

  return (
    <div className="mt-3 flex flex-wrap items-center gap-2">
      <span className={`rounded-full px-3 py-1 text-xs font-bold ${statusClass}`}>
        {statusText}
      </span>
      {error && (
        <span className="text-sm font-semibold text-[var(--danger)]">
          {error}
        </span>
      )}
    </div>
  )
}

function PlayerList({ players, hostUsername, currentUsername }) {
  return (
    <ul className="mt-6 grid gap-3 sm:grid-cols-2">
      {players.map((player) => {
        const isHost = player === hostUsername
        const isCurrentPlayer = player === currentUsername

        return (
          <li
            key={player}
            className="flex min-h-16 items-center justify-between gap-3 rounded-md border border-[var(--border)] bg-[var(--input-bg)] px-4 py-3"
          >
            <div>
              <p className="font-bold text-[var(--text-h)]">{player}</p>
              <p className="text-sm">
                {isCurrentPlayer ? 'You' : 'Connected'}
              </p>
            </div>
            <div className="flex flex-wrap justify-end gap-2">
              {isHost && (
                <span className="rounded-full bg-[var(--accent-bg)] px-3 py-1 text-xs font-bold text-[var(--accent)]">
                  Host
                </span>
              )}
              <span className="rounded-full bg-[var(--result-bg)] px-3 py-1 text-xs font-bold text-[var(--text-h)]">
                Ready
              </span>
            </div>
          </li>
        )
      })}
    </ul>
  )
}

function RoomInfoCard({ label, value }) {
  return (
    <div className="rounded-lg border border-[var(--border)] bg-[var(--panel-bg)] p-5">
      <p className="text-xs font-bold uppercase text-[var(--accent)]">
        {label}
      </p>
      <p className="mt-2 break-words font-mono text-2xl font-bold text-[var(--text-h)]">
        {value}
      </p>
    </div>
  )
}

export default App
