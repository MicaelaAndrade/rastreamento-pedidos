import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect, useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { makeOrder } from '../test/fixtures'
import { renderWithProviders } from '../test/render'
import { NewOrderPanel } from './NewOrderPanel'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const VIACEP_FRANCA = {
  cep: '14400-120',
  logradouro: 'Rua General Telles',
  bairro: 'Centro',
  localidade: 'Franca',
  uf: 'SP',
}

/** Simula a ViaCEP e a nossa API, respondendo conforme a URL chamada. */
function mockServices(viacep: () => Promise<Response>, orderDelayMs = 0) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input)
    if (url.startsWith('https://viacep.com.br/')) return viacep()
    if (url === '/api/orders') {
      await new Promise((resolve) => setTimeout(resolve, orderDelayMs))
      return json(201, makeOrder())
    }
    throw new Error(`URL inesperada no teste: ${url}`)
  })
}

const field = (name: string) => screen.getByLabelText(name, { exact: true }) as HTMLInputElement

describe('NewOrderPanel', () => {
  it('preenche rua, bairro e cidade a partir do CEP e leva para o campo Número', async () => {
    const user = userEvent.setup()
    const fetchMock = mockServices(async () => json(200, VIACEP_FRANCA))
    renderWithProviders(<NewOrderPanel onClose={() => {}} />)

    await user.type(field('CEP'), '14400120')

    expect(field('CEP')).toHaveValue('14400-120')
    await waitFor(() => expect(field('Rua')).toHaveValue('Rua General Telles'))
    expect(field('Bairro')).toHaveValue('Centro')
    expect(field('Cidade')).toHaveValue('Franca')
    expect(field('Número')).toHaveFocus()
    expect(screen.getByText(/Endereço preenchido pelo CEP/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('https://viacep.com.br/ws/14400120/json/', expect.anything())
  })

  it('não apaga o que a pessoa já digitou no endereço', async () => {
    const user = userEvent.setup()
    mockServices(async () => json(200, VIACEP_FRANCA))
    renderWithProviders(<NewOrderPanel onClose={() => {}} />)

    await user.type(field('Bairro'), 'Jardim Petráglia')
    await user.type(field('CEP'), '14400120')

    await waitFor(() => expect(field('Rua')).toHaveValue('Rua General Telles'))
    expect(field('Bairro')).toHaveValue('Jardim Petráglia')
  })

  it('avisa quando o CEP não existe e deixa preencher à mão', async () => {
    const user = userEvent.setup()
    mockServices(async () => json(200, { erro: true }))
    renderWithProviders(<NewOrderPanel onClose={() => {}} />)

    await user.type(field('CEP'), '99999999')

    expect(await screen.findByText(/CEP não encontrado/)).toBeInTheDocument()
    await user.type(field('Rua'), 'Rua Nova')
    expect(field('Rua')).toHaveValue('Rua Nova')
  })

  it('se a ViaCEP estiver fora do ar, avisa e o formulário continua funcionando', async () => {
    const user = userEvent.setup()
    mockServices(async () => {
      throw new TypeError('Failed to fetch')
    })
    renderWithProviders(<NewOrderPanel onClose={() => {}} />)

    await user.type(field('CEP'), '14400120')

    expect(await screen.findByText(/Não foi possível buscar o CEP agora/)).toBeInTheDocument()
    expect(field('Rua')).toBeEnabled()
  })

  it('envia o pedido com telefone, CEP e preço formatados pelas máscaras', async () => {
    const user = userEvent.setup()
    const fetchMock = mockServices(async () => json(200, VIACEP_FRANCA))
    const onClose = vi.fn()
    renderWithProviders(<NewOrderPanel onClose={onClose} />)

    await user.type(field('Nome'), 'Fernanda Alves')
    await user.type(field('Telefone'), '16999990000')
    await user.type(field('CEP'), '14400120')
    await waitFor(() => expect(field('Número')).toHaveFocus())
    await user.type(field('Número'), '510')
    await user.type(field('Item 1'), 'Pastel')
    await user.clear(field('Qtd.'))
    await user.type(field('Qtd.'), '2')
    await user.type(field('Preço (R$)'), '1250')

    expect(field('Telefone')).toHaveValue('(16) 99999-0000')
    expect(field('Preço (R$)')).toHaveValue('12,50')
    expect(screen.getByText('R$ 25,00')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Criar pedido' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/orders')!
    expect(JSON.parse(String(init?.body))).toEqual({
      customer: { name: 'Fernanda Alves', phone: '(16) 99999-0000' },
      deliveryAddress: {
        zipCode: '14400-120',
        street: 'Rua General Telles',
        number: '510',
        complement: null,
        neighborhood: 'Centro',
        city: 'Franca',
      },
      items: [{ name: 'Pastel', quantity: 2, unitPrice: 12.5 }],
    })
  })

  it('não tira o foco de quem está digitando quando a tela de pedidos se atualiza', async () => {
    const user = userEvent.setup()
    mockServices(async () => json(200, VIACEP_FRANCA))
    let refreshList = () => {}
    // Simula a lista de pedidos re-renderizando (ela se atualiza a cada 15 s) com um onClose novo.
    function OrdersPageStub() {
      const [, setRenders] = useState(0)
      useEffect(() => {
        refreshList = () => setRenders((n) => n + 1)
      }, [])
      return <NewOrderPanel onClose={() => {}} />
    }
    renderWithProviders(<OrdersPageStub />)

    await user.click(field('Rua'))
    act(() => refreshList())

    expect(field('Rua')).toHaveFocus()
  })

  it('trocar para um CEP geral de cidade limpa a rua e o bairro do CEP anterior', async () => {
    const user = userEvent.setup()
    const responses = [
      { logradouro: 'Avenida Paulista', bairro: 'Bela Vista', localidade: 'São Paulo' },
      { logradouro: '', bairro: '', localidade: 'Franca' },
    ]
    mockServices(async () => json(200, responses.shift()))
    renderWithProviders(<NewOrderPanel onClose={() => {}} />)

    await user.type(field('CEP'), '01310100')
    await waitFor(() => expect(field('Rua')).toHaveValue('Avenida Paulista'))
    await user.clear(field('CEP'))
    await user.type(field('CEP'), '14400000')

    await waitFor(() => expect(field('Cidade')).toHaveValue('Franca'))
    expect(field('Rua')).toHaveValue('')
    expect(field('Bairro')).toHaveValue('')
    expect(screen.getByText(/CEP geral da cidade/)).toBeInTheDocument()
    expect(field('Rua')).toHaveFocus()
  })

  it('Esc com o formulário preenchido pede confirmação em vez de fechar', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    renderWithProviders(<NewOrderPanel onClose={onClose} />)

    await user.type(field('Nome'), 'Fernanda')
    await user.keyboard('{Escape}')

    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByRole('alertdialog')).toHaveTextContent('Descartar este pedido?')

    await user.click(screen.getByRole('button', { name: 'Continuar editando' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(field('Nome')).toHaveValue('Fernanda')

    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: 'Descartar' }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('Esc com o formulário vazio fecha direto', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    renderWithProviders(<NewOrderPanel onClose={onClose} />)

    await user.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('clique duplo em "Criar pedido" envia um único pedido', async () => {
    const user = userEvent.setup()
    // A API demora a responder: o segundo clique chega enquanto o primeiro envio está em andamento.
    const fetchMock = mockServices(async () => json(200, VIACEP_FRANCA), 300)
    renderWithProviders(<NewOrderPanel onClose={() => {}} />)

    await user.type(field('Nome'), 'Fernanda')
    await user.type(field('Telefone'), '16999990000')
    await user.type(field('CEP'), '14400120')
    await waitFor(() => expect(field('Número')).toHaveFocus())
    await user.type(field('Número'), '1')
    await user.type(field('Item 1'), 'Pastel')
    await user.type(field('Preço (R$)'), '1000')

    // Dois cliques no mesmo instante, sem a tela re-renderizar entre eles (como um clique duplo rápido).
    const button = screen.getByRole('button', { name: 'Criar pedido' })
    fireEvent.click(button)
    fireEvent.click(button)

    await new Promise((resolve) => setTimeout(resolve, 400))
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/orders')).toHaveLength(1)
  })

  it('apagar o preço com Backspace deixa o campo vazio', async () => {
    const user = userEvent.setup()
    renderWithProviders(<NewOrderPanel onClose={() => {}} />)

    await user.type(field('Preço (R$)'), '5')
    expect(field('Preço (R$)')).toHaveValue('0,05')
    await user.type(field('Preço (R$)'), '{Backspace}')

    expect(field('Preço (R$)')).toHaveValue('')
  })

  it('colar "25,9" no preço dá R$ 25,90', async () => {
    const user = userEvent.setup()
    renderWithProviders(<NewOrderPanel onClose={() => {}} />)

    await user.click(field('Preço (R$)'))
    await user.paste('25,9')

    expect(field('Preço (R$)')).toHaveValue('25,90')
  })
})
