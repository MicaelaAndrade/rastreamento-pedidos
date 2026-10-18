import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { vi } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider'

export function renderWithProviders(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  // Como wrapper, os providers continuam valendo no rerender() dos testes.
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  )
  return render(ui, { wrapper })
}

/** Substitui o fetch global por uma resposta fixa e devolve o mock para inspeção. */
export function mockFetch(status: number, body: unknown) {
  const response = new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)
}
