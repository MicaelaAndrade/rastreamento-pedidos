import type { Order } from '../api/types'

export function makeOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: 7,
    status: 'EM_PREPARO',
    customer: { name: 'Ana Souza', phone: '(16) 99999-0000' },
    deliveryAddress: {
      street: 'Rua das Flores',
      number: '120',
      complement: 'Apto 3',
      neighborhood: 'Centro',
      city: 'Franca',
      zipCode: '14400-000',
    },
    items: [{ name: 'X-Burger', quantity: 2, unitPrice: 25, subtotal: 50 }],
    total: 50,
    createdAt: '2026-10-16T17:00:00Z',
    updatedAt: '2026-10-16T17:10:00Z',
    statusHistory: [
      { status: 'RECEBIDO', changedAt: '2026-10-16T17:00:00Z', changedBy: 'Mica' },
      { status: 'EM_PREPARO', changedAt: '2026-10-16T17:10:00Z', changedBy: 'Mica' },
    ],
    allowedNextStatuses: ['SAIU_PARA_ENTREGA', 'CANCELADO'],
    ...overrides,
  }
}
