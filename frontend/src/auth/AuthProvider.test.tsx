import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from '../App'
import { mockFetch, renderWithProviders } from '../test/render'

const TOKEN_KEY = 'rastreamento.token'

describe('sessão salva ao recarregar a página', () => {
  it('se a API estiver fora do ar, mantém o login e oferece tentar de novo', async () => {
    localStorage.setItem(TOKEN_KEY, 'token-salvo')
    mockFetch(503, { status: 503, detail: 'Serviço indisponível' })

    renderWithProviders(<App />)

    expect(await screen.findByText(/Não foi possível conectar à API/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument()
    expect(localStorage.getItem(TOKEN_KEY)).toBe('token-salvo')
  })

  it('se o token não vale mais (401), volta para o login', async () => {
    localStorage.setItem(TOKEN_KEY, 'token-vencido')
    mockFetch(401, { status: 401, detail: 'Faça login.' })

    renderWithProviders(<App />)

    expect(await screen.findByRole('tab', { name: 'Entrar' })).toBeInTheDocument()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })
})
