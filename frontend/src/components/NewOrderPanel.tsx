import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { ordersApi } from '../api/endpoints'
import type { CreateOrderRequest } from '../api/types'
import { lookupCep, type CepAddress } from '../lib/cep'
import { formatMoney } from '../lib/format'
import { formatPriceDigits, maskPhone, maskZipCode, priceDigits } from '../lib/masks'

interface ItemDraft {
  key: number
  name: string
  quantity: string
  /** Preço em centavos, só dígitos ("1500" = R$ 15,00). */
  priceCents: string
}

type CepStatus = 'idle' | 'loading' | 'found' | 'not-found' | 'error'

const CEP_MESSAGE: Record<CepStatus, string> = {
  idle: '',
  loading: 'Buscando endereço…',
  found: 'Endereço preenchido pelo CEP. Confira e informe o número.',
  'not-found': 'CEP não encontrado. Preencha o endereço manualmente.',
  error: 'Não foi possível buscar o CEP agora. Preencha o endereço manualmente.',
}

/**
 * Usa o valor do CEP só se o campo estiver vazio ou ainda com o valor que o próprio CEP preencheu antes.
 * Assim, trocar o CEP atualiza o endereço, mas nunca apaga o que a pessoa digitou.
 */
const fill = (current: string, fromCep: string, previousFromCep: string | undefined) =>
  fromCep && (current === '' || current === previousFromCep) ? fromCep : current

let nextKey = 1
const emptyItem = (): ItemDraft => ({ key: nextKey++, name: '', quantity: '1', priceCents: '' })

export function NewOrderPanel({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const firstField = useRef<HTMLInputElement>(null)
  const [customer, setCustomer] = useState({ name: '', phone: '' })
  const [address, setAddress] = useState({
    street: '',
    number: '',
    complement: '',
    neighborhood: '',
    city: '',
    zipCode: '',
  })
  const [items, setItems] = useState<ItemDraft[]>([emptyItem()])
  const [cepStatus, setCepStatus] = useState<CepStatus>('idle')
  const zipField = useRef<HTMLInputElement>(null)
  const numberField = useRef<HTMLInputElement>(null)
  /** CEP da busca em andamento: se a pessoa mudar o CEP no meio, a resposta antiga é ignorada. */
  const pendingCep = useRef('')
  const lastFromCep = useRef<CepAddress | null>(null)

  // Foco no primeiro campo só ao abrir o painel. (Junto com o efeito abaixo, ele rodaria
  // de novo a cada atualização da lista de pedidos e tiraria a pessoa do campo em que está.)
  useEffect(() => {
    firstField.current?.focus()
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const create = useMutation({
    mutationFn: (body: CreateOrderRequest) => ordersApi.create(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      onClose()
    },
  })

  // Soma em centavos (números inteiros) para não acumular erro de ponto flutuante.
  const totalCents = items.reduce((sum, item) => sum + Number(item.priceCents || 0) * (Number(item.quantity) || 0), 0)

  /** Formata o CEP e, quando ele fica completo, busca o endereço. A busca é só uma ajuda: se falhar, nada trava. */
  async function handleZipChange(value: string) {
    const zipCode = maskZipCode(value)
    setAddress((a) => ({ ...a, zipCode }))

    const digits = zipCode.replace('-', '')
    if (digits.length !== 8) {
      pendingCep.current = ''
      setCepStatus('idle')
      return
    }
    if (digits === pendingCep.current) return
    pendingCep.current = digits
    setCepStatus('loading')

    try {
      const found = await lookupCep(digits)
      if (pendingCep.current !== digits) return
      if (!found) {
        setCepStatus('not-found')
        return
      }
      const previous = lastFromCep.current
      setAddress((a) => ({
        ...a,
        street: fill(a.street, found.street, previous?.street),
        neighborhood: fill(a.neighborhood, found.neighborhood, previous?.neighborhood),
        city: fill(a.city, found.city, previous?.city),
      }))
      lastFromCep.current = found
      setCepStatus('found')
      // Leva a pessoa para o próximo campo que falta, mas só se ela ainda estiver no CEP.
      if (document.activeElement === zipField.current) numberField.current?.focus()
    } catch {
      if (pendingCep.current === digits) setCepStatus('error')
    }
  }

  function updateItem(key: number, patch: Partial<ItemDraft>) {
    setItems((current) => current.map((item) => (item.key === key ? { ...item, ...patch } : item)))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    create.mutate({
      customer: { name: customer.name.trim(), phone: customer.phone.trim() },
      deliveryAddress: { ...address, complement: address.complement.trim() || null },
      items: items.map((item) => ({
        name: item.name.trim(),
        quantity: Number(item.quantity),
        unitPrice: Number(item.priceCents) / 100,
      })),
    })
  }

  const error = create.error instanceof ApiError ? create.error : null

  return (
    <div className="panel-backdrop" onClick={onClose}>
      <section
        className="panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="new-order-title"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="panel-head">
          <h2 id="new-order-title">Novo pedido</h2>
          <button className="link-button" onClick={onClose}>
            Fechar
          </button>
        </header>

        <form onSubmit={handleSubmit} className="panel-form">
          <fieldset>
            <legend>Cliente</legend>
            <div className="grid-2">
              <label className="field">
                <span>Nome</span>
                <input
                  ref={firstField}
                  value={customer.name}
                  onChange={(e) => setCustomer({ ...customer, name: e.target.value })}
                  required
                />
              </label>
              <label className="field">
                <span>Telefone</span>
                <input
                  type="tel"
                  value={customer.phone}
                  onChange={(e) => setCustomer({ ...customer, phone: maskPhone(e.target.value) })}
                  placeholder="(16) 99999-0000"
                  pattern="\(\d{2}\) \d{4,5}-\d{4}"
                  title="Telefone com DDD, por exemplo (16) 99999-0000"
                  required
                />
              </label>
            </div>
          </fieldset>

          <fieldset>
            <legend>Endereço de entrega</legend>
            <div className="grid-address">
              <div className="field">
                <label htmlFor="zip-code">CEP</label>
                <input
                  id="zip-code"
                  ref={zipField}
                  value={address.zipCode}
                  onChange={(e) => handleZipChange(e.target.value)}
                  inputMode="numeric"
                  placeholder="00000-000"
                  pattern="\d{5}-\d{3}"
                  title="CEP com 8 dígitos, por exemplo 14400-000"
                  aria-describedby="zip-code-status"
                  required
                />
              </div>
              <p id="zip-code-status" className={`cep-status cep-${cepStatus}`} aria-live="polite">
                {CEP_MESSAGE[cepStatus]}
              </p>
              <label className="field span-3">
                <span>Rua</span>
                <input value={address.street} onChange={(e) => setAddress({ ...address, street: e.target.value })} required />
              </label>
              <label className="field">
                <span>Número</span>
                <input
                  ref={numberField}
                  value={address.number}
                  onChange={(e) => setAddress({ ...address, number: e.target.value })}
                  required
                />
              </label>
              <label className="field span-2">
                <span>Complemento</span>
                <input
                  value={address.complement}
                  onChange={(e) => setAddress({ ...address, complement: e.target.value })}
                  placeholder="Opcional"
                />
              </label>
              <label className="field span-2">
                <span>Bairro</span>
                <input
                  value={address.neighborhood}
                  onChange={(e) => setAddress({ ...address, neighborhood: e.target.value })}
                  required
                />
              </label>
              <label className="field span-2">
                <span>Cidade</span>
                <input value={address.city} onChange={(e) => setAddress({ ...address, city: e.target.value })} required />
              </label>
            </div>
          </fieldset>

          <fieldset>
            <legend>Itens</legend>
            <ul className="item-list">
              {items.map((item, index) => (
                <li key={item.key} className="item-row">
                  <label className="field">
                    <span>Item {index + 1}</span>
                    <input
                      value={item.name}
                      onChange={(e) => updateItem(item.key, { name: e.target.value })}
                      placeholder="Ex.: X-Burger"
                      required
                    />
                  </label>
                  <label className="field">
                    <span>Qtd.</span>
                    <input
                      type="number"
                      min={1}
                      value={item.quantity}
                      onChange={(e) => updateItem(item.key, { quantity: e.target.value })}
                      required
                    />
                  </label>
                  <label className="field">
                    <span>Preço (R$)</span>
                    <input
                      inputMode="numeric"
                      value={formatPriceDigits(item.priceCents)}
                      onChange={(e) => updateItem(item.key, { priceCents: priceDigits(e.target.value) })}
                      placeholder="0,00"
                      required
                    />
                  </label>
                  <button
                    type="button"
                    className="link-button remove-item"
                    onClick={() => setItems((current) => current.filter((i) => i.key !== item.key))}
                    disabled={items.length === 1}
                    aria-label={`Remover item ${index + 1}`}
                  >
                    Remover
                  </button>
                </li>
              ))}
            </ul>
            <button type="button" className="btn btn-quiet" onClick={() => setItems((c) => [...c, emptyItem()])}>
              Adicionar item
            </button>
          </fieldset>

          {error && (
            <div className="form-error" role="alert">
              <p>{error.message}</p>
              {error.fieldErrors.length > 0 && (
                <ul>
                  {error.fieldErrors.map((f) => (
                    <li key={f.field}>{f.message}</li>
                  ))}
                </ul>
              )}
            </div>
          )}

          <footer className="panel-foot">
            <p>
              Total <strong>{formatMoney(totalCents / 100)}</strong>
            </p>
            <button type="submit" className="btn btn-primary" disabled={create.isPending}>
              {create.isPending ? 'Criando…' : 'Criar pedido'}
            </button>
          </footer>
        </form>
      </section>
    </div>
  )
}
