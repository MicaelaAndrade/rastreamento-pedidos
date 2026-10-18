import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { mockFetch, renderWithProviders } from '../test/render'
import { OrdersPage } from './OrdersPage'

const USER = { id: 1, name: 'Mica', email: 'mica@exemplo.com' }

describe('OrdersPage', () => {
  it('com o painel aberto, o resto da tela fica inerte, e ao fechar o foco volta ao botão', async () => {
    const user = userEvent.setup()
    mockFetch(200, [])
    renderWithProviders(<OrdersPage user={USER} />)

    const [openButton] = await screen.findAllByRole('button', { name: 'Novo pedido' })
    await user.click(openButton)

    const app = document.querySelector('.app')!
    expect(app).toHaveAttribute('inert')
    expect(screen.getByRole('dialog', { name: 'Novo pedido' })).toBeInTheDocument()

    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(app).not.toHaveAttribute('inert')
    await waitFor(() => expect(openButton).toHaveFocus())
  })
})
