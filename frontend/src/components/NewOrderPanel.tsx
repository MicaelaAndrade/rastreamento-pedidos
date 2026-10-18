import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { ordersApi } from '../api/endpoints'
import type { CreateOrderRequest } from '../api/types'
import { lookupCep, type CepAddress } from '../lib/cep'
import { formatMoney } from '../lib/format'
import { formatPriceDigits, maskPhone, maskZipCode, parsePastedPrice, priceDigits } from '../lib/masks'
import { useSingleSubmit } from '../lib/useSingleSubmit'

interface ItemDraft {
  key: number
  name: string
  quantity: string
  /** Preço em centavos, só dígitos ("1500" = R$ 15,00). */
  priceCents: string
}

type CepStatus = 'idle' | 'loading' | 'found' | 'partial' | 'not-found' | 'error'

const CEP_MESSAGE: Record<CepStatus, string> = {
  idle: '',
  loading: 'Buscando endereço…',
  found: 'Endereço preenchido pelo CEP. Confira e informe o número.',
  partial: 'CEP geral da cidade: informe a rua e o bairro.',
  'not-found': 'CEP não encontrado. Preencha o endereço manualmente.',
  error: 'Não foi possível buscar o CEP agora. Preencha o endereço manualmente.',
}

/**
 * Decide o valor de um campo do endereço quando um CEP é encontrado:
 * - campo vazio ou ainda com o valor que o CEP anterior preencheu: usa o do CEP novo,
 *   mesmo que venha vazio (CEP geral de cidade não tem rua nem bairro, e a rua do CEP
 *   anterior não pode ficar misturada com a cidade nova);
 * - campo que a pessoa digitou: nunca é sobrescrito.
 */
const fill = (current: string, fromCep: string, previousFromCep: string | undefined) =>
  current === '' || current === previousFromCep ? fromCep : current

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
  const streetField = useRef<HTMLInputElement>(null)
  const numberField = useRef<HTMLInputElement>(null)
  const keepEditingButton = useRef<HTMLButtonElement>(null)
  const [confirmingDiscard, setConfirmingDiscard] = useState(false)
  const focusBeforeDiscard = useRef<HTMLElement | null>(null)
  const submit = useSingleSubmit()
  /** CEP da busca em andamento: se a pessoa mudar o CEP no meio, a resposta antiga é ignorada. */
  const pendingCep = useRef('')
  const lastFromCep = useRef<CepAddress | null>(null)

  // Foco no primeiro campo só ao abrir o painel. (Junto com o efeito abaixo, ele rodaria
  // de novo a cada atualização da lista de pedidos e tiraria a pessoa do campo em que está.)
  useEffect(() => {
    firstField.current?.focus()
  }, [])

  const create = useMutation({
    mutationFn: (body: CreateOrderRequest) => ordersApi.create(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      onClose()
    },
  })

  const isDirty =
    customer.name !== '' ||
    customer.phone !== '' ||
    Object.values(address).some((value) => value !== '') ||
    items.length > 1 ||
    items.some((item) => item.name !== '' || item.priceCents !== '' || item.quantity !== '1')

  /** Fecha a confirmação e devolve o foco ao campo em que a pessoa estava. */
  const keepEditing = useCallback(() => {
    setConfirmingDiscard(false)
    const target = focusBeforeDiscard.current
    requestAnimationFrame(() => target?.focus())
  }, [])

  /** Fechar sem querer (Esc, clique fora) não pode apagar um pedido meio preenchido. */
  const requestClose = useCallback(() => {
    // Com o pedido sendo enviado, "descartar" não faria sentido: ele já está a caminho da API.
    if (create.isPending) return
    if (confirmingDiscard) keepEditing()
    else if (isDirty) {
      focusBeforeDiscard.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
      setConfirmingDiscard(true)
    } else onClose()
  }, [create.isPending, confirmingDiscard, isDirty, keepEditing, onClose])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && requestClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [requestClose])

  useEffect(() => {
    if (confirmingDiscard) keepEditingButton.current?.focus()
  }, [confirmingDiscard])


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
      const partial = !found.street || !found.neighborhood
      setCepStatus(partial ? 'partial' : 'found')
      // Leva a pessoa para o próximo campo que falta, mas só se ela ainda estiver no CEP.
      if (document.activeElement === zipField.current) {
        ;(found.street ? numberField : streetField).current?.focus()
      }
    } catch {
      if (pendingCep.current === digits) setCepStatus('error')
    }
  }

  function updateItem(key: number, patch: Partial<ItemDraft>) {
    setItems((current) => current.map((item) => (item.key === key ? { ...item, ...patch } : item)))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!submit.start()) return
    create.mutate({
      customer: { name: customer.name.trim(), phone: customer.phone.trim() },
      deliveryAddress: { ...address, complement: address.complement.trim() || null },
      items: items.map((item) => ({
        name: item.name.trim(),
        quantity: Number(item.quantity),
        unitPrice: Number(item.priceCents) / 100,
      })),
    }, { onSettled: submit.done })
  }

  const error = create.error instanceof ApiError ? create.error : null

  return (
    <div className="panel-backdrop" onClick={requestClose}>
      <section
        className="panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="new-order-title"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="panel-head">
          <h2 id="new-order-title">Novo pedido</h2>
          <button className="link-button" onClick={requestClose} disabled={create.isPending}>
            Fechar
          </button>
        </header>

        {confirmingDiscard && (
          <div className="discard" role="alertdialog" aria-labelledby="discard-title">
            <p id="discard-title">Descartar este pedido? O que você preencheu será perdido.</p>
            <div className="order-buttons">
              <button ref={keepEditingButton} className="btn btn-quiet" onClick={keepEditing}>
                Continuar editando
              </button>
              <button className="btn btn-danger" onClick={onClose}>
                Descartar
              </button>
            </div>
          </div>
        )}

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
                <input
                  ref={streetField}
                  value={address.street}
                  onChange={(e) => setAddress({ ...address, street: e.target.value })}
                  required
                />
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
                      max={999}
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
                      onChange={(e) => {
                        e.currentTarget.setCustomValidity('')
                        updateItem(item.key, { priceCents: priceDigits(e.target.value) })
                      }}
                      onBlur={(e) => e.currentTarget.setCustomValidity('')}
                      onPaste={(e) => {
                        // Colar "25,90" ou "25.9" deve dar R$ 25,90, e não ler os dígitos como centavos.
                        const pasted = parsePastedPrice(e.clipboardData.getData('text'))
                        if (pasted === null) return
                        e.preventDefault()
                        const input = e.currentTarget
                        if ('tooLarge' in pasted) {
                          // Não corta o número em silêncio: mantém o valor anterior e explica.
                          input.setCustomValidity('O preço máximo de um item é R$ 9.999.999,99.')
                          input.reportValidity()
                          return
                        }
                        input.setCustomValidity('')
                        updateItem(item.key, { priceCents: pasted.digits })
                      }}
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
                  {error.fieldErrors.map((f, i) => (
                    <li key={`${f.field}-${i}`}>{f.message}</li>
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
