import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { ordersApi } from '../api/endpoints'
import type { CreateOrderRequest } from '../api/types'
import { formatMoney, parsePrice } from '../lib/format'

interface ItemDraft {
  key: number
  name: string
  quantity: string
  unitPrice: string
}

let nextKey = 1
const emptyItem = (): ItemDraft => ({ key: nextKey++, name: '', quantity: '1', unitPrice: '' })

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

  useEffect(() => {
    firstField.current?.focus()
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

  const total = items.reduce((sum, item) => {
    const price = parsePrice(item.unitPrice)
    const quantity = Number(item.quantity)
    return Number.isFinite(price) && Number.isFinite(quantity) ? sum + price * quantity : sum
  }, 0)

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
        unitPrice: parsePrice(item.unitPrice),
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
                  onChange={(e) => setCustomer({ ...customer, phone: e.target.value })}
                  placeholder="(16) 99999-0000"
                  required
                />
              </label>
            </div>
          </fieldset>

          <fieldset>
            <legend>Endereço de entrega</legend>
            <div className="grid-address">
              <label className="field span-3">
                <span>Rua</span>
                <input value={address.street} onChange={(e) => setAddress({ ...address, street: e.target.value })} required />
              </label>
              <label className="field">
                <span>Número</span>
                <input value={address.number} onChange={(e) => setAddress({ ...address, number: e.target.value })} required />
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
              <label className="field span-3">
                <span>Cidade</span>
                <input value={address.city} onChange={(e) => setAddress({ ...address, city: e.target.value })} required />
              </label>
              <label className="field">
                <span>CEP</span>
                <input
                  value={address.zipCode}
                  onChange={(e) => setAddress({ ...address, zipCode: e.target.value })}
                  inputMode="numeric"
                  placeholder="00000-000"
                  required
                />
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
                      inputMode="decimal"
                      value={item.unitPrice}
                      onChange={(e) => updateItem(item.key, { unitPrice: e.target.value })}
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
              Total <strong>{formatMoney(total)}</strong>
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
