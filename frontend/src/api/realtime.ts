import { Client } from '@stomp/stompjs'
import { brokerUrl } from './brokerUrl'
import { getAccessToken } from './tokenStore'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface RealtimeEvent {
  type: string
  [key: string]: unknown
}

/**
 * Opens a WebSocket (STOMP) connection, subscribes to one destination, and returns a function
 * that closes it. The access token goes in the STOMP CONNECT frame because browsers cannot set
 * an Authorization header on a WebSocket. Events only say "something changed" (ids, no data):
 * callers should re-fetch over REST, where authorization is enforced.
 */
export function subscribeToTopic(destination: string, onEvent: (event: RealtimeEvent) => void): () => void {
  const client = new Client({
    brokerURL: brokerUrl(BASE_URL, window.location),
    // The token is read at (re)connect time so a reconnect after login uses the current one.
    beforeConnect: () => {
      client.connectHeaders = { Authorization: `Bearer ${getAccessToken() ?? ''}` }
    },
    reconnectDelay: 5000,
  })

  client.onConnect = () => {
    client.subscribe(destination, (message) => {
      try {
        onEvent(JSON.parse(message.body) as RealtimeEvent)
      } catch {
        // Ignore a malformed event; the next REST fetch is the source of truth anyway.
      }
    })
  }

  client.activate()
  return () => void client.deactivate()
}
