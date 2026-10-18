import { describe, expect, it } from 'vitest'
import { formatPriceDigits, maskPhone, maskZipCode, pastedPriceToDigits, priceDigits } from './masks'

describe('maskPhone', () => {
  it.each([
    ['', ''],
    ['1', '(1'],
    ['16', '(16'],
    ['169', '(16) 9'],
    ['1633334444', '(16) 3333-4444'],
    ['16999990000', '(16) 99999-0000'],
    ['(16) 99999-00001234', '(16) 99999-0000'],
  ])('formata "%s" como "%s"', (input, expected) => {
    expect(maskPhone(input)).toBe(expected)
  })

  it('aceita o valor já formatado sem alterar', () => {
    expect(maskPhone('(16) 99999-0000')).toBe('(16) 99999-0000')
  })
})

describe('maskZipCode', () => {
  it.each([
    ['144', '144'],
    ['14400', '14400'],
    ['144000', '14400-0'],
    ['14400000', '14400-000'],
    ['14400-0001', '14400-000'],
  ])('formata "%s" como "%s"', (input, expected) => {
    expect(maskZipCode(input)).toBe(expected)
  })
})

describe('preço digitado em centavos', () => {
  it('guarda só os dígitos, sem zeros à esquerda', () => {
    expect(priceDigits('0,15')).toBe('15')
    expect(priceDigits('15,001')).toBe('15001') // mais um dígito digitado: 150,01
    expect(priceDigits('abc')).toBe('')
  })

  it.each([
    ['', ''],
    ['1', '0,01'],
    ['15', '0,15'],
    ['1500', '15,00'],
    ['6078', '60,78'],
    ['125000', '1.250,00'],
  ])('mostra "%s" como "%s"', (digits, expected) => {
    expect(formatPriceDigits(digits)).toBe(expected)
  })

  it('apagar o último dígito volta uma casa (15,00 → 1,50)', () => {
    expect(formatPriceDigits(priceDigits('15,0'))).toBe('1,50')
  })
})

describe('correções da revisão', () => {
  it('apagar o preço até o fim deixa o campo vazio, e não "0,00"', () => {
    expect(priceDigits('0,0')).toBe('')
    expect(priceDigits('000')).toBe('')
    expect(formatPriceDigits(priceDigits('0,0'))).toBe('')
  })

  it.each([
    ['25,9', '2590'],
    ['25,90', '2590'],
    ['R$ 1.250,00', '125000'],
    ['1.500', '150000'],
    ['25.90', '2590'],
    ['7', '700'],
  ])('colar "%s" no preço vira %s centavos', (pasted, digits) => {
    expect(pastedPriceToDigits(pasted)).toBe(digits)
  })

  it('colar um texto que não é preço é ignorado', () => {
    expect(pastedPriceToDigits('abc')).toBeNull()
    expect(pastedPriceToDigits('12,345')).toBeNull()
  })

  it('telefone colado com +55 descarta o código do país', () => {
    expect(maskPhone('+55 16 99999-0000')).toBe('(16) 99999-0000')
    expect(maskPhone('5516999990000')).toBe('(16) 99999-0000')
  })
})
