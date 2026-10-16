import { useAuth } from './auth/useAuth'
import { AuthScreen } from './components/AuthScreen'
import { OrdersPage } from './components/OrdersPage'

export default function App() {
  const { state } = useAuth()

  if (state.status === 'checking') return <p className="empty">Carregando…</p>
  if (state.status === 'anonymous') return <AuthScreen />
  return <OrdersPage user={state.user} />
}
