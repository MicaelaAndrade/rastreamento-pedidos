import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, setUnauthorizedHandler, tokenStorage } from '../api/client'
import { authApi } from '../api/endpoints'
import type { AuthResponse } from '../api/types'
import { AuthContext, type AuthState } from './useAuth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AuthState>(() =>
    tokenStorage.get() ? { status: 'checking' } : { status: 'anonymous' },
  )

  const signOut = useCallback(() => {
    tokenStorage.clear()
    queryClient.clear()
    setState({ status: 'anonymous' })
  }, [queryClient])

  const signIn = useCallback((response: AuthResponse) => {
    tokenStorage.set(response.token)
    setState({ status: 'authenticated', user: response.user })
  }, [])

  // Qualquer 401 da API (token expirado) leva de volta ao login.
  useEffect(() => setUnauthorizedHandler(signOut), [signOut])

  // Ao recarregar a página com um token salvo, confirma com a API que ele ainda vale.
  useEffect(() => {
    if (state.status !== 'checking') return
    authApi
      .me()
      .then((user) => setState({ status: 'authenticated', user }))
      .catch((error) => {
        // Só um 401 significa sessão inválida. Outros erros (API reiniciando, rede instável)
        // não devem apagar o login da pessoa.
        if (error instanceof ApiError && error.status === 401) signOut()
        else setState({ status: 'unavailable' })
      })
  }, [state.status, signOut])

  const retry = useCallback(() => setState({ status: 'checking' }), [])

  const value = useMemo(() => ({ state, signIn, signOut, retry }), [state, signIn, signOut, retry])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
