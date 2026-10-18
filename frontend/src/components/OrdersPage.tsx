import { useQuery } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import { ordersApi } from '../api/endpoints'
import type { OrderStatus, User } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { STATUS_LABEL } from '../lib/status'
import { NewOrderPanel } from './NewOrderPanel'
import { OrderRow } from './OrderRow'

type Filter = OrderStatus | 'TODOS'
const FILTERS: Filter[] = ['TODOS', 'RECEBIDO', 'EM_PREPARO', 'SAIU_PARA_ENTREGA', 'ENTREGUE', 'CANCELADO']

export function OrdersPage({ user }: { user: User }) {
  const { signOut } = useAuth()
  const [filter, setFilter] = useState<Filter>('TODOS')
  const [creating, setCreating] = useState(false)
  const newOrderButton = useRef<HTMLButtonElement>(null)
  const opener = useRef<HTMLElement | null>(null)

  function openPanel() {
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    setCreating(true)
  }

  /** Ao fechar o painel, o foco volta para o botão que o abriu (ou para o "Novo pedido" do topo). */
  const closePanel = useCallback(() => {
    setCreating(false)
    requestAnimationFrame(() => {
      const target = opener.current?.isConnected ? opener.current : newOrderButton.current
      target?.focus()
    })
  }, [])

  // Atualiza a lista a cada 15 s, para acompanhar mudanças feitas por outras pessoas.
  const orders = useQuery({ queryKey: ['orders'], queryFn: ordersApi.list, refetchInterval: 15_000 })

  const all = orders.data ?? []
  const countOf = (f: Filter) => (f === 'TODOS' ? all.length : all.filter((o) => o.status === f).length)
  const visible = filter === 'TODOS' ? all : all.filter((o) => o.status === filter)

  return (
    <>
      {/* Com o painel aberto, o resto da tela fica inerte: o Tab não sai do painel. */}
      <div className="app" inert={creating}>
        <header className="topbar">
          <p className="brand">Despacho</p>
          <div className="topbar-user">
            <span>{user.name}</span>
            <button className="link-button" onClick={signOut}>
              Sair
            </button>
          </div>
        </header>

        <main className="orders">
          <div className="orders-head">
            <h1>Pedidos</h1>
            <button ref={newOrderButton} className="btn btn-primary" onClick={openPanel}>
              Novo pedido
            </button>
          </div>

          <nav className="filters" aria-label="Filtrar por status">
            {FILTERS.map((f) => (
              <button key={f} className="filter" aria-pressed={filter === f} onClick={() => setFilter(f)}>
                {f === 'TODOS' ? 'Todos' : STATUS_LABEL[f]}
                <span className="filter-count">{countOf(f)}</span>
              </button>
            ))}
          </nav>

          {orders.isPending && <p className="empty">Carregando pedidos…</p>}

          {orders.isError && (
            <div className="empty" role="alert">
              <p>Não foi possível carregar os pedidos: {orders.error.message}</p>
              <button className="btn btn-quiet" onClick={() => orders.refetch()}>
                Tentar de novo
              </button>
            </div>
          )}

          {orders.isSuccess && visible.length === 0 && (
            <div className="empty">
              {all.length === 0 ? (
                <>
                  <p>Nenhum pedido ainda. Crie o primeiro para acompanhar a entrega por aqui.</p>
                  <button className="btn btn-primary" onClick={openPanel}>
                    Novo pedido
                  </button>
                </>
              ) : (
                <p>Nenhum pedido com status “{STATUS_LABEL[filter as OrderStatus]}”.</p>
              )}
            </div>
          )}

          <div className="order-list">
            {visible.map((order) => (
              <OrderRow key={order.id} order={order} />
            ))}
          </div>
        </main>
      </div>
      {creating && <NewOrderPanel onClose={closePanel} />}
    </>
  )
}
