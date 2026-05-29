import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useCallback, useEffect, useRef, useState } from 'react'
import './App.css'

const GAME_CATEGORIES = ['Name', 'City', 'Country', 'Animal', 'Plant', 'Object']

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
  const [copyMessage, setCopyMessage] = useState('')

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
      finishedBy: event.finishedBy ?? null,
      remainingSeconds: event.remainingSeconds ?? 0,
      submittedPlayers: event.submittedPlayers ?? [],
      answersByPlayer: event.answersByPlayer ?? {},
      roundScores: event.roundScores ?? {},
      players: event.players ?? [],
    })
    setJoinMessage('')
    setJoinStatus('success')
    setStartMessage('')
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
      setCopyMessage('')
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
        finishedBy: null,
        remainingSeconds: 0,
        submittedPlayers: data.submittedPlayers ?? [],
        answersByPlayer: data.answersByPlayer ?? {},
        roundScores: data.roundScores ?? {},
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
    setStartMessage('Starting game...')
    roomSocket.startGame()
  }

  async function handleCopyRoomCode() {
    if (!createdRoomCode) {
      return
    }

    try {
      await navigator.clipboard.writeText(createdRoomCode)
      setCopyMessage('Room code copied.')
    } catch {
      setCopyMessage('Copy failed. Select the code manually.')
    }
  }

  if (gameRoom) {
    return (
      <GameRoomPage
        room={gameRoom}
        connectionStatus={roomSocket.status}
        connectionError={roomSocket.error}
        startMessage={startMessage}
        onFinishGame={roomSocket.finishGame}
        onLeaveRoom={handleLeaveRoom}
        onStartGame={handleStartGame}
        onSubmitAnswers={roomSocket.submitAnswers}
      />
    )
  }

  return (
    <main className="home-page page-shell">
      <section className="intro animate-in">
        <p className="eyebrow">Kelime Oyunu</p>
        <h1>Start a word room in seconds.</h1>
        <p className="intro-copy">
          Create a private room code, invite your friends, then join with a
          username before the game begins.
        </p>
      </section>

      <section className="room-actions animate-in delay-1" aria-label="Room actions">
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
            {createStatus === 'loading' ? (
              <ButtonContent label="Creating" />
            ) : (
              'Create Room'
            )}
          </button>

          <output
            className={`result-box ${createStatus === 'error' ? 'error' : ''}`}
            aria-live="polite"
          >
            {createdRoomCode ? (
              <>
                <span>Room code</span>
                <strong>{createdRoomCode}</strong>
                <button
                  className="copy-button"
                  onClick={handleCopyRoomCode}
                  type="button"
                >
                  Copy code
                </button>
                {copyMessage && <span className="assistive-message">{copyMessage}</span>}
              </>
            ) : (
              <span>
                {createStatus === 'loading'
                  ? 'Creating your room...'
                  : createError || 'Your room code will appear here.'}
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
            {joinStatus === 'loading' ? (
              <ButtonContent label="Joining" />
            ) : (
              'Join Room'
            )}
          </button>

          <FeedbackMessage status={joinStatus} message={joinMessage} />
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

  const finishGame = useCallback(() => {
    const client = clientRef.current
    const currentRoom = roomConnectionRef.current

    if (client?.connected && currentRoom) {
      client.publish({
        destination: '/app/finish',
        body: JSON.stringify(currentRoom),
      })
    }
  }, [])

  const submitAnswers = useCallback((answers) => {
    const client = clientRef.current
    const currentRoom = roomConnectionRef.current

    if (client?.connected && currentRoom) {
      client.publish({
        destination: '/app/answers',
        body: JSON.stringify({ ...currentRoom, answers }),
      })
    }
  }, [])

  const visibleStatus = roomConnection && status === 'idle' ? 'connecting' : status

  return { error, finishGame, leaveRoom, startGame, status: visibleStatus, submitAnswers }
}

function GameRoomPage({
  room,
  connectionStatus,
  connectionError,
  startMessage,
  onFinishGame,
  onLeaveRoom,
  onStartGame,
  onSubmitAnswers,
}) {
  const isHost = room.currentUsername === room.hostUsername
  const isInProgress = room.gameState === 'IN_PROGRESS'
  const isRoundEnded = room.gameState === 'FINISHED'
  const showGameScreen = isInProgress || isRoundEnded
  const playerCount = room.players.length
  const maxPlayers = room.maxPlayers || playerCount
  const isConnected = connectionStatus === 'connected'
  return (
    <main className="page-shell min-h-svh bg-[var(--bg)] px-5 py-6 text-[var(--text)] sm:px-8 lg:px-10">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-6">
        <header className="animate-in flex flex-col gap-4 rounded-lg border border-[var(--border)] bg-[var(--panel-bg)] p-5 shadow-[var(--panel-shadow)] sm:flex-row sm:items-center sm:justify-between sm:p-6">
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
            {isHost && !showGameScreen && (
              <button
                type="button"
                onClick={onStartGame}
                disabled={!isConnected}
                className="min-h-11 rounded-md bg-[var(--button-bg)] px-5 text-sm font-bold text-white transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0 dark:text-slate-950"
              >
                {isConnected ? 'Start Game' : <ButtonContent label="Connecting" />}
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

        {connectionError && (
          <div className="animate-in rounded-lg border border-[var(--danger-border)] bg-[var(--danger-bg)] px-4 py-3 text-sm font-bold text-[var(--danger)]" role="alert">
            {connectionError}
          </div>
        )}

        <section className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
          {showGameScreen ? (
            <div className="animate-in flex min-w-0 flex-col gap-6 delay-1">
              <GameScreen
                key={`${room.roomCode}-${room.selectedLetter}`}
                currentUsername={room.currentUsername}
                finishedBy={room.finishedBy}
                isRoundEnded={isRoundEnded}
                onFinishGame={onFinishGame}
                onSubmitAnswers={onSubmitAnswers}
                remainingSeconds={room.remainingSeconds}
                selectedLetter={room.selectedLetter}
                submittedPlayers={room.submittedPlayers ?? []}
                canSubmit={isConnected}
              />
              <Scoreboard
                answersByPlayer={room.answersByPlayer ?? {}}
                currentUsername={room.currentUsername}
                players={room.players}
                roundScores={room.roundScores ?? {}}
                submittedPlayers={room.submittedPlayers ?? []}
              />
            </div>
          ) : (
            <PlayersPanel
              playerCount={playerCount}
              maxPlayers={maxPlayers}
              players={room.players}
              hostUsername={room.hostUsername}
              currentUsername={room.currentUsername}
            />
          )}

          <aside className="animate-in flex min-w-0 flex-col gap-4 delay-2">
            <RoomInfoCard label="Room code" value={room.roomCode} />
            <RoomInfoCard label="Host" value={room.hostUsername} />
            <RoomInfoCard label="You" value={room.currentUsername} />
            {showGameScreen && (
              <RoomInfoCard label="Players" value={`${playerCount} / ${maxPlayers}`} />
            )}

            <div className="rounded-lg border border-[var(--border)] bg-[var(--panel-bg)] p-5 shadow-[var(--panel-shadow)]">
              <p className="text-xs font-bold uppercase text-[var(--accent)]">
                Status
              </p>
              <p className="mt-3 text-lg font-semibold text-[var(--text-h)]">
                {isRoundEnded
                  ? 'Round ended.'
                  : isInProgress
                  ? 'Round in progress.'
                  : isHost
                    ? 'Ready when you are.'
                    : 'Waiting for the host to start.'}
              </p>
              <p className="mt-2 text-sm">
                {isRoundEnded
                  ? 'Waiting for results.'
                  : isInProgress
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
    <div className="animate-in rounded-lg border border-[var(--border)] bg-[var(--panel-bg)] p-5 shadow-[var(--panel-shadow)] sm:p-6">
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

function GameScreen({
  currentUsername,
  finishedBy,
  isRoundEnded,
  onFinishGame,
  onSubmitAnswers,
  remainingSeconds,
  selectedLetter,
  submittedPlayers,
  canSubmit,
}) {
  const [answers, setAnswers] = useState(() => createEmptyAnswers())
  const [submitted, setSubmitted] = useState(false)
  const [submitError, setSubmitError] = useState('')
  const currentPlayerSubmitted = submitted || includesUsername(submittedPlayers, currentUsername)
  const isLocked = currentPlayerSubmitted || isRoundEnded || !canSubmit

  function updateAnswer(category, value) {
    setAnswers((current) => ({ ...current, [category]: value }))
  }

  function handleFinish(event) {
    event.preventDefault()
    setSubmitError('')

    if (!canSubmit) {
      setSubmitError('Connection is not ready yet. Please wait a moment.')
      return
    }

    onSubmitAnswers(answers)
    onFinishGame()
    setSubmitted(true)
  }

  return (
    <form
      className="rounded-lg border border-[var(--border)] bg-[var(--panel-bg)] p-5 shadow-[var(--panel-shadow)] sm:p-6"
      onSubmit={handleFinish}
    >
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <LetterDisplay letter={selectedLetter} />
        <Countdown
          finishedBy={finishedBy}
          isRoundEnded={isRoundEnded}
          secondsLeft={remainingSeconds}
        />
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
              disabled={isLocked}
              maxLength={32}
              onChange={(event) => updateAnswer(category, event.target.value)}
              type="text"
              value={answers[category]}
            />
          </label>
        ))}
      </div>

      <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className={`min-h-6 text-sm font-semibold ${submitError ? 'text-[var(--danger)]' : 'text-[var(--success)]'}`} role={submitError ? 'alert' : undefined}>
          {submitError || (isRoundEnded ? 'Round ended.' : currentPlayerSubmitted ? 'Answers submitted.' : !canSubmit ? 'Connecting before submissions open.' : '')}
        </p>
        <button
          className="min-h-11 rounded-md bg-[var(--button-bg)] px-6 text-sm font-bold text-white transition hover:-translate-y-0.5 disabled:cursor-default disabled:opacity-60 disabled:hover:translate-y-0 dark:text-slate-950"
          disabled={isLocked}
          type="submit"
        >
          Finish
        </button>
      </div>
    </form>
  )
}

function Scoreboard({
  answersByPlayer,
  currentUsername,
  players,
  roundScores,
  submittedPlayers,
}) {
  const categories = getScoreboardCategories(answersByPlayer, roundScores)
  const winners = getRoundWinners(players, roundScores)

  return (
    <section className="rounded-lg border border-[var(--border)] bg-[var(--panel-bg)] p-5 shadow-[var(--panel-shadow)] sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase text-[var(--accent)]">
            Scoreboard
          </p>
          <h2 className="mt-2 text-2xl font-semibold text-[var(--text-h)]">
            Round scores
          </h2>
        </div>
        <div className="rounded-md bg-[var(--accent-bg)] px-4 py-3 text-sm font-bold text-[var(--accent)]">
          {winners.length ? `Winner: ${winners.join(', ')}` : 'Winner pending'}
        </div>
      </div>

      <div className="mt-5 grid gap-3 sm:grid-cols-3">
        <ScoreLegend label="Unique answer" className="border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-200" />
        <ScoreLegend label="Duplicate answer" className="border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-200" />
        <ScoreLegend label="Empty answer" className="border-[var(--border)] bg-[var(--result-bg)] text-[var(--text)]" />
      </div>

      <div className="score-table-wrap mt-5 overflow-x-auto">
        <table className="w-full min-w-[680px] border-separate border-spacing-0 text-left text-sm">
          <thead>
            <tr>
              <th className="border-b border-[var(--border)] px-3 py-3 text-xs uppercase text-[var(--accent)]">
                Player
              </th>
              {categories.map((category) => (
                <th
                  className="border-b border-[var(--border)] px-3 py-3 text-xs uppercase text-[var(--accent)]"
                  key={category}
                >
                  {category}
                </th>
              ))}
              <th className="border-b border-[var(--border)] px-3 py-3 text-right text-xs uppercase text-[var(--accent)]">
                Total
              </th>
            </tr>
          </thead>
          <tbody>
            {players.map((player) => {
              const playerAnswers = getAnswersForPlayer(answersByPlayer, player)
              const playerScore = getScoreForPlayer(roundScores, player)
              const isWinner = winners.includes(player)
              const isSubmitted = includesUsername(submittedPlayers, player)

              return (
                <tr key={player}>
                  <th className="border-b border-[var(--border)] px-3 py-4 align-top">
                    <div className="font-bold text-[var(--text-h)]">
                      {player}
                      {player === currentUsername ? ' (You)' : ''}
                    </div>
                    <div className="mt-2 flex flex-wrap gap-2">
                      <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${isSubmitted ? 'bg-[var(--accent-bg)] text-[var(--accent)]' : 'bg-[var(--result-bg)] text-[var(--text)]'}`}>
                        {isSubmitted ? 'Submitted' : 'Waiting'}
                      </span>
                      {isWinner && (
                        <span className="rounded-full bg-[var(--button-soft-bg)] px-2.5 py-1 text-xs font-bold text-[var(--button-bg)]">
                          Round winner
                        </span>
                      )}
                    </div>
                  </th>
                  {categories.map((category) => {
                    const answer = findCategoryValue(playerAnswers, category)
                    const score = getCategoryScore(playerScore, category)
                    const scoreStyle = getScoreStyle(score, answer)

                    return (
                      <td
                        className="border-b border-[var(--border)] px-3 py-4 align-top"
                        key={category}
                      >
                        <div className={`rounded-md border px-3 py-2 ${scoreStyle.className}`}>
                          <div className="min-h-5 font-bold text-[var(--text-h)]">
                            {answer || 'Empty'}
                          </div>
                          <div className="mt-1 text-xs font-bold">
                            {scoreStyle.label} - {score} pts
                          </div>
                        </div>
                      </td>
                    )
                  })}
                  <td className="border-b border-[var(--border)] px-3 py-4 text-right align-top font-mono text-2xl font-bold text-[var(--text-h)]">
                    {playerScore.totalScore ?? 0}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function ScoreLegend({ className, label }) {
  return (
    <div className={`rounded-md border px-3 py-2 text-xs font-bold ${className}`}>
      {label}
    </div>
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

function Countdown({ finishedBy, isRoundEnded, secondsLeft }) {
  const hasStarted = Number.isFinite(Number(secondsLeft)) || Boolean(finishedBy) || isRoundEnded
  const displaySeconds = hasStarted ? Math.max(secondsLeft ?? 0, 0) : null
  const minutes = displaySeconds === null
    ? '--'
    : String(Math.floor(displaySeconds / 60)).padStart(2, '0')
  const seconds = displaySeconds === null
    ? '--'
    : String(displaySeconds % 60).padStart(2, '0')
  const statusText = isRoundEnded
    ? 'Round ended'
    : hasStarted
    ? finishedBy
      ? `Started by ${finishedBy}`
      : 'Live'
    : 'Waiting for first finish'

  return (
    <section
      aria-label="Countdown timer"
      aria-live="polite"
      className="min-w-40 rounded-lg border border-[var(--border)] bg-[var(--result-bg)] p-4 text-center"
    >
      <p className="text-xs font-bold uppercase text-[var(--accent)]">
        Countdown
      </p>
      <p className="mt-2 font-mono text-3xl font-bold leading-none text-[var(--text-h)]">
        {minutes}:{seconds}
      </p>
      <p className="mt-2 min-h-5 text-xs font-bold text-[var(--text)]">
        {statusText}
      </p>
    </section>
  )
}

function createEmptyAnswers() {
  return Object.fromEntries(GAME_CATEGORIES.map((category) => [category, '']))
}

function includesUsername(usernames = [], username = '') {
  return usernames.some((item) => normalizeKey(item) === normalizeKey(username))
}

function getAnswersForPlayer(answersByPlayer, player) {
  const matchingKey = Object.keys(answersByPlayer).find(
    (key) => normalizeKey(key) === normalizeKey(player)
  )

  return matchingKey ? answersByPlayer[matchingKey] : {}
}

function getScoreForPlayer(roundScores, player) {
  const matchingKey = Object.keys(roundScores).find(
    (key) => normalizeKey(key) === normalizeKey(player)
  )

  return matchingKey ? roundScores[matchingKey] : { categoryScores: {}, totalScore: 0 }
}

function getScoreboardCategories(answersByPlayer, roundScores) {
  const categories = new Map()

  GAME_CATEGORIES.forEach((category) => categories.set(normalizeKey(category), category))
  Object.values(answersByPlayer).forEach((answers) => {
    Object.keys(answers ?? {}).forEach((category) => {
      categories.set(normalizeKey(category), category)
    })
  })
  Object.values(roundScores).forEach((score) => {
    Object.keys(score?.categoryScores ?? {}).forEach((category) => {
      categories.set(normalizeKey(category), category)
    })
  })

  return Array.from(categories.values())
}

function findCategoryValue(answers, category) {
  const matchingKey = Object.keys(answers ?? {}).find(
    (key) => normalizeKey(key) === normalizeKey(category)
  )

  return matchingKey ? answers[matchingKey] : ''
}

function getCategoryScore(playerScore, category) {
  const scores = playerScore?.categoryScores ?? {}
  const matchingKey = Object.keys(scores).find(
    (key) => normalizeKey(key) === normalizeKey(category)
  )

  return matchingKey ? scores[matchingKey] : 0
}

function getScoreStyle(score, answer) {
  if (!answer || score === 0) {
    return {
      className: 'border-[var(--border)] bg-[var(--result-bg)] text-[var(--text)]',
      label: 'Empty',
    }
  }

  if (score === 5) {
    return {
      className: 'border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-200',
      label: 'Duplicate',
    }
  }

  return {
    className: 'border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-200',
    label: 'Unique',
  }
}

function getRoundWinners(players, roundScores) {
  const scoredPlayers = players.map((player) => ({
    player,
    score: getScoreForPlayer(roundScores, player).totalScore ?? 0,
  }))
  const highestScore = Math.max(...scoredPlayers.map(({ score }) => score), 0)

  if (highestScore <= 0) {
    return []
  }

  return scoredPlayers
    .filter(({ score }) => score === highestScore)
    .map(({ player }) => player)
}

function normalizeKey(value = '') {
  return String(value).trim().toLowerCase()
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
        {status === 'connecting' || status === 'reconnecting' ? (
          <span className="inline-flex items-center gap-2">
            <LoadingSpinner />
            {statusText}
          </span>
        ) : (
          statusText
        )}
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
            className="player-row flex min-h-16 items-center justify-between gap-3 rounded-md border border-[var(--border)] bg-[var(--input-bg)] px-4 py-3"
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
    <div className="rounded-lg border border-[var(--border)] bg-[var(--panel-bg)] p-5 shadow-[var(--panel-shadow)]">
      <p className="text-xs font-bold uppercase text-[var(--accent)]">
        {label}
      </p>
      <p className="mt-2 break-words font-mono text-2xl font-bold text-[var(--text-h)]">
        {value}
      </p>
    </div>
  )
}

function LoadingSpinner() {
  return <span className="loading-spinner" aria-hidden="true" />
}

function ButtonContent({ label }) {
  return (
    <span className="button-content">
      <LoadingSpinner />
      <span>{label}</span>
    </span>
  )
}

function FeedbackMessage({ status, message }) {
  if (status !== 'error' && !message) {
    return <output className="join-message" aria-live="polite" />
  }

  return (
    <output
      className={`join-message ${status === 'error' ? 'error' : ''}`}
      aria-live="polite"
      role={status === 'error' ? 'alert' : undefined}
    >
      {message}
    </output>
  )
}

export default App
