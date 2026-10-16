import { describe, expect, it } from 'vitest'
import { parsePrice } from './format'

describe('parsePrice', () => {
  it.each([
    ['12,50', 12.5],
    ['12.50', 12.5],
    ['1.250,00', 1250],
    ['  8 ', 8],
  ])('converte "%s" em %d', (text, expected) => {
    expect(parsePrice(text)).toBe(expected)
  })

  it.each(['', 'abc', '12,5,0'])('retorna NaN para "%s"', (text) => {
    expect(parsePrice(text)).toBeNaN()
  })
})
