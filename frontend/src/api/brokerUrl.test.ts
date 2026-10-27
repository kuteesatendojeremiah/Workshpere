import { describe, expect, it } from 'vitest'
import { brokerUrl } from './brokerUrl'

describe('brokerUrl', () => {
  it('turns a separate API address into the matching WebSocket address', () => {
    expect(brokerUrl('http://localhost:8080', { protocol: 'http:', host: 'localhost:5173' })).toBe('ws://localhost:8080/ws')
    expect(brokerUrl('https://api.example.com', { protocol: 'https:', host: 'app.example.com' })).toBe(
      'wss://api.example.com/ws',
    )
  })

  it('uses the page address when the API is served from the same origin', () => {
    expect(brokerUrl('', { protocol: 'http:', host: 'localhost:8081' })).toBe('ws://localhost:8081/ws')
  })

  it('uses wss when the page itself is served over https', () => {
    expect(brokerUrl('', { protocol: 'https:', host: 'worksphere.example.com' })).toBe('wss://worksphere.example.com/ws')
  })
})
