import { useStore } from './store'

const ptyListeners = new Set()

/** Terminal panes subscribe here for their PTY byte stream. */
export function onPtyData(listener) {
  ptyListeners.add(listener)
  return () => ptyListeners.delete(listener)
}

let socket = null
let retryDelay = 500
let sendQueue = []

export function sendSocket(message) {
  const frame = JSON.stringify(message)
  if (socket?.readyState === WebSocket.OPEN) {
    socket.send(frame)
  } else {
    sendQueue.push(frame)
  }
}

export function connectSocket() {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  const url =
    import.meta.env.DEV && location.port === '5173'
      ? `${protocol}://127.0.0.1:4000/ws`
      : `${protocol}://${location.host}/ws`

  socket = new WebSocket(url)
  const store = useStore.getState

  socket.onopen = () => {
    retryDelay = 500
    store().setConnected(true)
    sendQueue.forEach((frame) => socket.send(frame))
    sendQueue = []
  }

  socket.onclose = () => {
    store().setConnected(false)
    // Exponential backoff, capped, so a restarted daemon reconnects quickly
    // without a tight loop while it is genuinely down.
    setTimeout(connectSocket, retryDelay)
    retryDelay = Math.min(retryDelay * 2, 8000)
  }

  socket.onerror = () => socket?.close()

  socket.onmessage = (message) => {
    let frame
    try {
      frame = JSON.parse(message.data)
    } catch {
      return
    }
    const { channel, payload } = frame
    const s = store()

    switch (channel) {
      case 'session':
        s.upsertSession(payload)
        break
      case 'session:deleted':
        s.removeSession(payload.id)
        break
      case 'event':
        s.appendEvent(payload)
        break
      case 'event:update':
        s.updateEvent(payload)
        break
      case 'approval':
        s.upsertApproval(payload)
        break
      case 'pty:data':
      case 'pty:exit':
      case 'pty:error':
        ptyListeners.forEach((listener) => listener(channel, payload))
        break
      default:
        break
    }
  }
}
