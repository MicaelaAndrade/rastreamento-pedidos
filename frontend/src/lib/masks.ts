/**
 * Máscaras dos campos do formulário de pedido.
 * Cada função recebe o que está no campo (com ou sem formatação) e devolve o texto já formatado,
 * então dá para usá-las direto no onChange: setValor(maskPhone(e.target.value)).
 */

const onlyDigits = (value: string) => value.replace(/\D/g, '')

/** (16) 3333-4444 para fixo (10 dígitos) e (16) 99999-0000 para celular (11 dígitos). */
export function maskPhone(value: string): string {
  let d = onlyDigits(value)
  // Número colado com o código do Brasil (+55 16 99999-0000): descarta o 55.
  if (d.length > 11 && d.startsWith('55')) d = d.slice(2)
  d = d.slice(0, 11)
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
  // Sem zeros à esquerda; só zeros vira campo vazio (apagar tudo deixa o campo vazio, e não "0,00").
  // Até 9 dígitos: R$ 9.999.999,99, o limite que a API aceita.
  return onlyDigits(value).replace(/^0+/, '').slice(0, 9)
}

/**
 * Valor colado no campo de preço: lido como número decimal, e não dígito a dígito.
 * "25,9" → "2590" (R$ 25,90); "R$ 1.250,00" → "125000"; "25.90" → "2590".
 * Devolve null se o texto não for um preço reconhecível (aí o campo trata como digitação normal).
 */
export function pastedPriceToDigits(text: string): string | null {
  const clean = text.replace(/R\$|\s/g, '')
  let normalized: string
  if (/^\d{1,3}(\.\d{3})*(,\d{1,2})?$/.test(clean) || /^\d+(,\d{1,2})?$/.test(clean)) {
    normalized = clean.replace(/\./g, '').replace(',', '.') // formato brasileiro
  } else if (/^\d+(\.\d{1,2})?$/.test(clean)) {
    normalized = clean // formato com ponto decimal
  } else {
    return null
  }
  const cents = Math.round(Number(normalized) * 100)
  return Number.isFinite(cents) ? priceDigits(String(cents)) : null
}

const price = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** "1500" → "15,00" e "125000" → "1.250,00". Campo vazio continua vazio. */
export function formatPriceDigits(digits: string): string {
  return digits === '' ? '' : price.format(Number(digits) / 100)
}
