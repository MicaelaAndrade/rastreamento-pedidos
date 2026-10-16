export type OrderStatus =
  | 'RECEBIDO'
  | 'EM_PREPARO'
  | 'SAIU_PARA_ENTREGA'
  | 'ENTREGUE'
  | 'CANCELADO'

export interface User {
  id: number
  name: string
  email: string
}

export interface AuthResponse {
  token: string
  expiresAt: string
  user: User
}

export interface Customer {
  name: string
  phone: string
}

export interface DeliveryAddress {
  street: string
  number: string
  complement?: string | null
  neighborhood: string
  city: string
  zipCode: string
}

export interface OrderItem {
  name: string
  quantity: number
  unitPrice: number
  subtotal: number
}

export interface StatusChange {
  status: OrderStatus
  changedAt: string
  changedBy: string
}

export interface Order {
  id: number
  status: OrderStatus
  customer: Customer
  deliveryAddress: DeliveryAddress
  items: OrderItem[]
  total: number
  createdAt: string
  updatedAt: string
  statusHistory: StatusChange[]
  allowedNextStatuses: OrderStatus[]
}

export interface NewOrderItem {
  name: string
  quantity: number
  unitPrice: number
}

export interface CreateOrderRequest {
  customer: Customer
  deliveryAddress: DeliveryAddress
  items: NewOrderItem[]
}

/** Formato de erro da API (RFC 9457 — Problem Details). */
export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
  errors?: { field: string; message: string }[]
}
