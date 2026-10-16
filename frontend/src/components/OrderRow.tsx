import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ordersApi } from '../api/endpoints'
import type { Order, OrderStatus } from '../api/types'
import { formatDateTime, formatMoney } from '../lib/format'
import { ADVANCE_LABEL, STATUS_LABEL } from '../lib/status'
import { StatusTrack } from './StatusTrack'

export function OrderRow({ order }: { order: Order }) {
  const queryClient = useQueryClient()
  const [confirmingCancel, setConfirmingCancel] = useState(false)
  const [expanded, setExpanded] = useState(false)

  const update = useMutation({
    mutationFn: (status: OrderStatus) => ordersApi.updateStatus(order.id, status),
    onSuccess: (updated) => {
      queryClient.setQueryData(['orders', updated.id], updated)
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      setConfirmingCancel(false)
    },
  })

  // Os detalhes vêm do endpoint GET /orders/{id}, carregado só quando a linha é aberta.
  const details = useQuery({
    queryKey: ['orders', order.id],
    queryFn: () => ordersApi.get(order.id),
    enabled: expanded,
  })

  const advanceTo = order.allowedNextStatuses.find((s) => s !== 'CANCELADO')
  const canCancel = order.allowedNextStatuses.includes('CANCELADO')
  const { deliveryAddress: address } = order
  const itemsSummary = order.items.map((i) => `${i.quantity}× ${i.name}`).join(', ')

  return (
    <article className={`order order-${order.status.toLowerCase()}`} aria-labelledby={`order-${order.id}`}>
      <header className="order-head">
        <h3 id={`order-${order.id}`}>
          <span className="order-id">#{order.id}</span> {order.customer.name}
        </h3>
        <p className="order-total">{formatMoney(order.total)}</p>
      </header>

      <div className="order-info">
        <p>{itemsSummary}</p>
        <p className="muted">
          {address.street}, {address.number}
          {address.complement ? ` (${address.complement})` : ''} — {address.neighborhood}, {address.city}
        </p>
      </div>

      <StatusTrack order={order} />

      <footer className="order-actions">
        <button className="link-button" onClick={() => setExpanded((v) => !v)} aria-expanded={expanded}>
          {expanded ? 'Ocultar detalhes' : 'Ver detalhes'}
        </button>

        <div className="order-buttons">
          {canCancel && !confirmingCancel && (
            <button className="btn btn-quiet" onClick={() => setConfirmingCancel(true)} disabled={update.isPending}>
              Cancelar pedido
            </button>
          )}
          {confirmingCancel && (
            <span className="confirm" role="group" aria-label="Confirmar cancelamento">
              <span>Cancelar o pedido #{order.id}?</span>
              <button className="btn btn-quiet" onClick={() => setConfirmingCancel(false)}>
                Manter
              </button>
              <button className="btn btn-danger" onClick={() => update.mutate('CANCELADO')} disabled={update.isPending}>
                Sim, cancelar
              </button>
            </span>
          )}
          {advanceTo && !confirmingCancel && (
            <button className="btn btn-primary" onClick={() => update.mutate(advanceTo)} disabled={update.isPending}>
              {ADVANCE_LABEL[advanceTo] ?? STATUS_LABEL[advanceTo]}
            </button>
          )}
        </div>
      </footer>

      {update.isError && (
        <p className="form-error" role="alert">
          {update.error.message}
        </p>
      )}

      {expanded && (
        <div className="order-details">
          {details.isPending && <p className="muted">Carregando…</p>}
          {details.isError && <p className="form-error">{details.error.message}</p>}
          {details.data && <OrderDetails order={details.data} />}
        </div>
      )}
    </article>
  )
}

function OrderDetails({ order }: { order: Order }) {
  return (
    <>
      <table className="items-table">
        <caption>Itens</caption>
        <thead>
          <tr>
            <th scope="col">Item</th>
            <th scope="col">Qtd.</th>
            <th scope="col">Unitário</th>
            <th scope="col">Subtotal</th>
          </tr>
        </thead>
        <tbody>
          {order.items.map((item, index) => (
            <tr key={index}>
              <td>{item.name}</td>
              <td>{item.quantity}</td>
              <td>{formatMoney(item.unitPrice)}</td>
              <td>{formatMoney(item.subtotal)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="details-side">
        <h4>Cliente</h4>
        <p>
          {order.customer.name}
          <br />
          {order.customer.phone}
        </p>
        <h4>Endereço</h4>
        <p>
          {order.deliveryAddress.street}, {order.deliveryAddress.number}
          {order.deliveryAddress.complement ? ` — ${order.deliveryAddress.complement}` : ''}
          <br />
          {order.deliveryAddress.neighborhood}, {order.deliveryAddress.city}
          <br />
          CEP {order.deliveryAddress.zipCode}
        </p>
        <h4>Histórico</h4>
        <ul className="history">
          {order.statusHistory.map((change, index) => (
            <li key={index}>
              <strong>{STATUS_LABEL[change.status]}</strong> em {formatDateTime(change.changedAt)}, por{' '}
              {change.changedBy}
            </li>
          ))}
        </ul>
      </div>
    </>
  )
}
