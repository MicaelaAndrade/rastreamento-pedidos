import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { mockFetch, renderWithProviders } from '../test/render'
import { AuthScreen } from './AuthScreen'

describe('AuthScreen', () => {
  it('mostra o erro da API quando o login falha', async () => {
    const user = userEvent.setup()
    mockFetch(401, { status: 401, detail: 'E-mail ou senha inválidos.' })
    renderWithProviders(<AuthScreen />)

    await user.type(screen.getByLabelText('E-mail'), 'mica@exemplo.com')
    await user.type(screen.getByLabelText('Senha'), 'senha-errada')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.')
  })

  it('no cadastro, envia nome, e-mail e senha e guarda o token', async () => {
    const user = userEvent.setup()
    const fetchMock = mockFetch(201, {
      token: 'token-de-teste',
      expiresAt: '2026-10-16T20:00:00Z',
      user: { id: 1, name: 'Mica', email: 'mica@exemplo.com' },
    })
    renderWithProviders(<AuthScreen />)

    await user.click(screen.getByRole('tab', { name: 'Criar conta' }))
    await user.type(screen.getByLabelText('Nome'), 'Mica')
    await user.type(screen.getByLabelText('E-mail'), 'mica@exemplo.com')
    await user.type(screen.getByLabelText('Senha'), 'senha-segura')
    await user.click(screen.getByRole('button', { name: 'Criar conta e entrar' }))

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/register',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ name: 'Mica', email: 'mica@exemplo.com', password: 'senha-segura' }),
      }),
    )
    await vi.waitFor(() => expect(localStorage.getItem('rastreamento.token')).toBe('token-de-teste'))
  })
})
