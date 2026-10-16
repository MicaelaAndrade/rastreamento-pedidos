import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { vi } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider'

export function renderWithProviders(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>{ui}</AuthProvider>
    </QueryClientProvider>,
  )
}

/** Substitui o fetch global por uma resposta fixa e devolve o mock para inspeção. */
export function mockFetch(status: number, body: unknown) {
  const response = new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)
}
