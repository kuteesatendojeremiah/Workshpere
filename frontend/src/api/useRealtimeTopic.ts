import { useEffect, useRef } from 'react'
import { subscribeToTopic, type RealtimeEvent } from './realtime'

/** Subscribes while the component is mounted and `destination` is set; always cleans up. */
export function useRealtimeTopic(destination: string | null, onEvent: (event: RealtimeEvent) => void): void {
  // Keep the latest callback without re-subscribing every render.
  const handler = useRef(onEvent)
  useEffect(() => {
    handler.current = onEvent
  })

  useEffect(() => {
    if (!destination) {
      return undefined
    }
    return subscribeToTopic(destination, (event) => handler.current(event))
  }, [destination])
}
