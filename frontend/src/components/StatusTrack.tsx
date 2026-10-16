import type { Order, OrderStatus } from '../api/types'
import { formatTime } from '../lib/format'
import { ROUTE, STATUS_LABEL } from '../lib/status'

type StopState = 'done' | 'current' | 'upcoming'

/**
 * Mostra o pedido como uma rota: cada status é uma parada,
 * com o horário em que o pedido passou por ela.
 */
export function StatusTrack({ order }: { order: Pick<Order, 'status' | 'statusHistory'> }) {
  const reachedAt = new Map<OrderStatus, string>()
  for (const change of order.statusHistory) reachedAt.set(change.status, change.changedAt)

  const cancelled = order.status === 'CANCELADO'
  // Cancelado: mostra só as paradas por onde o pedido passou, e a rota termina interrompida.
  const stops = cancelled ? ROUTE.filter((s) => reachedAt.has(s)) : ROUTE
  const currentIndex = ROUTE.indexOf(order.status)

  const stateOf = (index: number): StopState => {
    if (cancelled || order.status === 'ENTREGUE') return 'done'
    if (index < currentIndex) return 'done'
    return index === currentIndex ? 'current' : 'upcoming'
  }

  return (
    <ol className="track" aria-label={`Status do pedido: ${STATUS_LABEL[order.status]}`}>
      {stops.map((status, index) => {
        const state = stateOf(index)
        const time = reachedAt.get(status)
        const cut = cancelled && index === stops.length - 1
        return (
          <li
            key={status}
            className={`stop stop-${state}${cut ? ' stop-cut' : ''}`}
            aria-current={state === 'current' ? 'step' : undefined}
          >
            <span className="stop-dot" />
            <span className="stop-label">{STATUS_LABEL[status]}</span>
            <span className="stop-time">{time ? formatTime(time) : ' '}</span>
          </li>
        )
      })}
      {cancelled && (
        <li className="stop stop-cancel">
          <span className="stop-dot" />
          <span className="stop-label">Cancelado</span>
          <span className="stop-time">
            {reachedAt.get('CANCELADO') ? formatTime(reachedAt.get('CANCELADO')!) : ' '}
          </span>
        </li>
      )}
    </ol>
  )
}
