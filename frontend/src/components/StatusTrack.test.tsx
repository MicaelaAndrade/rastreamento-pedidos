import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { makeOrder } from '../test/fixtures'
import { StatusTrack } from './StatusTrack'

describe('StatusTrack', () => {
  it('marca as paradas já percorridas e a parada atual', () => {
    render(<StatusTrack order={makeOrder({ status: 'EM_PREPARO' })} />)

    const track = screen.getByRole('list', { name: 'Status do pedido: Em preparo' })
    const stops = within(track).getAllByRole('listitem')

    expect(stops).toHaveLength(4)
    expect(stops[0]).toHaveClass('stop-done')
    expect(stops[1]).toHaveClass('stop-current')
    expect(stops[1]).toHaveAttribute('aria-current', 'step')
    expect(stops[2]).toHaveClass('stop-upcoming')
    expect(stops[3]).toHaveClass('stop-upcoming')
  })

  it('num pedido entregue, marca todas as paradas como concluídas', () => {
    const order = makeOrder({
      status: 'ENTREGUE',
      statusHistory: [
        { status: 'RECEBIDO', changedAt: '2026-10-16T17:00:00Z', changedBy: 'Mica' },
        { status: 'EM_PREPARO', changedAt: '2026-10-16T17:05:00Z', changedBy: 'Mica' },
        { status: 'SAIU_PARA_ENTREGA', changedAt: '2026-10-16T17:20:00Z', changedBy: 'Mica' },
        { status: 'ENTREGUE', changedAt: '2026-10-16T17:40:00Z', changedBy: 'Mica' },
      ],
    })
    render(<StatusTrack order={order} />)

    for (const stop of screen.getAllByRole('listitem')) expect(stop).toHaveClass('stop-done')
  })

  it('num pedido cancelado, mostra só as paradas percorridas e a parada de cancelamento', () => {
    const order = makeOrder({
      status: 'CANCELADO',
      statusHistory: [
        { status: 'RECEBIDO', changedAt: '2026-10-16T17:00:00Z', changedBy: 'Mica' },
        { status: 'CANCELADO', changedAt: '2026-10-16T17:05:00Z', changedBy: 'Mica' },
      ],
    })
    render(<StatusTrack order={order} />)

    const stops = screen.getAllByRole('listitem')

    expect(stops).toHaveLength(2)
    expect(stops[0]).toHaveTextContent('Recebido')
    expect(stops[0]).toHaveClass('stop-cut')
    expect(stops[1]).toHaveClass('stop-cancel')
    expect(stops[1]).toHaveTextContent('Cancelado')
  })
})
