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

