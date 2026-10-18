import { createContext, useContext } from 'react'
import type { AuthResponse, User } from '../api/types'

export type AuthState =
  | { status: 'checking' }
  /** Havia uma sessão salva, mas a API não respondeu: mantém o token e oferece tentar de novo. */
  | { status: 'unavailable' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: User }

export interface AuthContextValue {
  state: AuthState
  signIn: (response: AuthResponse) => void
  signOut: () => void
  retry: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth precisa estar dentro de <AuthProvider>')
  return context
}
