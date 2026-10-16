import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { makeOrder } from '../test/fixtures'
import { mockFetch, renderWithProviders } from '../test/render'
import { OrderRow } from './OrderRow'

describe('OrderRow', () => {
  it('mostra só as ações que a API permite para o status atual', () => {
    renderWithProviders(<OrderRow order={makeOrder({ status: 'EM_PREPARO' })} />)

    expect(screen.getByRole('button', { name: 'Enviar para entrega' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancelar pedido' })).toBeInTheDocument()
  })

  it('não mostra ações para um pedido entregue', () => {
    renderWithProviders(<OrderRow order={makeOrder({ status: 'ENTREGUE', allowedNextStatuses: [] })} />)

    expect(screen.queryByRole('button', { name: /cancelar/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /entrega/i })).not.toBeInTheDocument()
  })

  it('pede confirmação antes de cancelar e envia o novo status para a API', async () => {
    const user = userEvent.setup()
    const fetchMock = mockFetch(200, makeOrder({ status: 'CANCELADO', allowedNextStatuses: [] }))
    renderWithProviders(<OrderRow order={makeOrder()} />)

    await user.click(screen.getByRole('button', { name: 'Cancelar pedido' }))
    expect(fetchMock).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Sim, cancelar' }))

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/orders/7/status',
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ status: 'CANCELADO' }) }),
    )
  })

  it('mostra a mensagem da API quando a mudança de status é recusada', async () => {
    const user = userEvent.setup()
    mockFetch(409, { status: 409, detail: 'Não é possível mudar de ENTREGUE para CANCELADO.' })
    renderWithProviders(<OrderRow order={makeOrder()} />)

    await user.click(screen.getByRole('button', { name: 'Enviar para entrega' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Não é possível mudar de ENTREGUE para CANCELADO.')
  })
})
