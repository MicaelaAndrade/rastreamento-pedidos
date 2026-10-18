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
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ status: 'CANCELADO', expectedVersion: 1 }) }),
    )
  })

  it('mostra a mensagem da API quando a mudança de status é recusada', async () => {
    const user = userEvent.setup()
    mockFetch(409, { status: 409, detail: 'Não é possível mudar de ENTREGUE para CANCELADO.' })
    renderWithProviders(<OrderRow order={makeOrder()} />)

    await user.click(screen.getByRole('button', { name: 'Enviar para entrega' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Não é possível mudar de ENTREGUE para CANCELADO.')
  })

  it('fecha a confirmação de cancelamento se o pedido deixar de poder ser cancelado', async () => {
    const user = userEvent.setup()
    const { rerender } = renderWithProviders(<OrderRow order={makeOrder()} />)

    await user.click(screen.getByRole('button', { name: 'Cancelar pedido' }))
    expect(screen.getByRole('button', { name: 'Sim, cancelar' })).toBeInTheDocument()

    // Outra pessoa enviou o pedido para entrega; a lista atualizou.
    rerender(
      <OrderRow order={makeOrder({ status: 'SAIU_PARA_ENTREGA', allowedNextStatuses: ['ENTREGUE'], version: 2 })} />,
    )

    expect(screen.queryByRole('button', { name: 'Sim, cancelar' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar entrega' })).toBeInTheDocument()
  })

  it('os detalhes abertos acompanham as atualizações do pedido', async () => {
    const user = userEvent.setup()
    const { rerender } = renderWithProviders(<OrderRow order={makeOrder()} />)

    await user.click(screen.getByRole('button', { name: 'Ver detalhes' }))
    const changes = makeOrder().statusHistory.length

    const updated = makeOrder({
      status: 'SAIU_PARA_ENTREGA',
      allowedNextStatuses: ['ENTREGUE'],
      statusHistory: [
        ...makeOrder().statusHistory,
        { status: 'SAIU_PARA_ENTREGA', changedAt: '2026-10-16T17:20:00Z', changedBy: 'João' },
      ],
    })
    rerender(<OrderRow order={updated} />)

    const history = screen.getByText('Histórico').nextElementSibling!
    expect(history.querySelectorAll('li')).toHaveLength(changes + 1)
    expect(history).toHaveTextContent('Saiu para entrega')
  })

  it('envia a versão que a tela mostra junto com o novo status', async () => {
    const user = userEvent.setup()
    const fetchMock = mockFetch(200, makeOrder({ status: 'SAIU_PARA_ENTREGA', version: 8 }))
    renderWithProviders(<OrderRow order={makeOrder({ version: 7 })} />)

    await user.click(screen.getByRole('button', { name: 'Enviar para entrega' }))

    const [, init] = fetchMock.mock.calls[0]
    expect(JSON.parse(String(init?.body))).toEqual({ status: 'SAIU_PARA_ENTREGA', expectedVersion: 7 })
  })
})
