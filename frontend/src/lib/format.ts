const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const time = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' })
const dateTime = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

export const formatMoney = (value: number) => currency.format(value)
export const formatTime = (iso: string) => time.format(new Date(iso))
export const formatDateTime = (iso: string) => dateTime.format(new Date(iso))

/**
 * Converte o preço digitado em número. Aceita "12,50", "1.250,00" e "12.50".
 * Retorna NaN quando o texto não é um número (o back-end recusa e mostra o erro).
 */
export function parsePrice(text: string): number {
  const value = text.trim()
  if (value === '') return NaN
  const normalized = value.includes(',') ? value.replace(/\./g, '').replace(',', '.') : value
  return Number(normalized)
}
