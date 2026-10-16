/**
 * Máscaras dos campos do formulário de pedido.
 * Cada função recebe o que está no campo (com ou sem formatação) e devolve o texto já formatado,
 * então dá para usá-las direto no onChange: setValor(maskPhone(e.target.value)).
 */

const onlyDigits = (value: string) => value.replace(/\D/g, '')

/** (16) 3333-4444 para fixo (10 dígitos) e (16) 99999-0000 para celular (11 dígitos). */
export function maskPhone(value: string): string {
  const d = onlyDigits(value).slice(0, 11)
  if (d.length === 0) return ''
  if (d.length <= 2) return `(${d}`
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`
}

/** 14400000 → 14400-000 */
export function maskZipCode(value: string): string {
  const d = onlyDigits(value).slice(0, 8)
  return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d
}

/**
 * Preço digitado "da direita para a esquerda", como em maquininha de cartão:
 * os dois últimos dígitos são sempre os centavos (1 → 0,01; 15 → 0,15; 1500 → 15,00).
 * Guardamos só os dígitos (= valor em centavos) e formatamos na hora de mostrar.
 */
export function priceDigits(value: string): string {
  // até 9 dígitos (R$ 9.999.999,99), o limite que a API aceita; sem zeros à esquerda
  return onlyDigits(value).replace(/^0+(?=\d)/, '').slice(0, 9)
}

const price = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** "1500" → "15,00" e "125000" → "1.250,00". Campo vazio continua vazio. */
export function formatPriceDigits(digits: string): string {
  return digits === '' ? '' : price.format(Number(digits) / 100)
}
