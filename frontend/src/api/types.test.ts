import { describe, expect, it } from 'vitest'
import { canTransitionTo } from './types'

describe('canTransitionTo', () => {
  it('allows staying in place', () => {
    expect(canTransitionTo('TODO', 'TODO')).toBe(true)
  })

  it('allows moving forward exactly one column', () => {
    expect(canTransitionTo('TODO', 'IN_PROGRESS')).toBe(true)
    expect(canTransitionTo('IN_PROGRESS', 'IN_REVIEW')).toBe(true)
  })

  it('rejects skipping a column going forward', () => {
    expect(canTransitionTo('TODO', 'IN_REVIEW')).toBe(false)
    expect(canTransitionTo('TODO', 'DONE')).toBe(false)
  })

  it('allows moving backward any distance', () => {
    expect(canTransitionTo('DONE', 'TODO')).toBe(true)
    expect(canTransitionTo('IN_REVIEW', 'IN_PROGRESS')).toBe(true)
  })
})
