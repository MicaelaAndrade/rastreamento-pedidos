import { useAuth } from './auth/useAuth'
import { AuthScreen } from './components/AuthScreen'
import { OrdersPage } from './components/OrdersPage'

export default function App() {
  const { state, retry, signOut } = useAuth()

  if (state.status === 'checking') return <p className="empty">Carregando…</p>
  if (state.status === 'unavailable') {
    return (
      <div className="empty" role="alert">
        <p>Não foi possível conectar à API. Confira se ela está rodando e tente de novo.</p>
        <div className="order-buttons">
          <button className="btn btn-quiet" onClick={signOut}>
            Sair
          </button>
          <button className="btn btn-primary" onClick={retry}>
            Tentar de novo
          </button>
        </div>
      </div>
    )
  }
  if (state.status === 'anonymous') return <AuthScreen />
  return <OrdersPage user={state.user} />
}
