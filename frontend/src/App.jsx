import { useState } from 'react'
import './App.css'

function App() {
  const [createdRoomCode, setCreatedRoomCode] = useState('')
  const [createStatus, setCreateStatus] = useState('idle')
  const [createError, setCreateError] = useState('')
  const [joinForm, setJoinForm] = useState({ roomCode: '', username: '' })
  const [joinStatus, setJoinStatus] = useState('idle')
  const [joinMessage, setJoinMessage] = useState('')

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
      const response = await fetch(`/rooms/${roomCode}/join`, {
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
      setJoinMessage(`${data.username} joined room ${data.roomCode}.`)
      setJoinStatus('success')
    } catch (error) {
      setJoinMessage(error.message)
      setJoinStatus('error')
    }
  }

  function updateJoinForm(field, value) {
    setJoinForm((current) => ({ ...current, [field]: value }))
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

          <output className="result-box" aria-live="polite">
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

export default App
