import { api } from './client'
import type { AuthResponse, CreateOrderRequest, Order, OrderStatus, User } from './types'

export const authApi = {
  register: (body: { name: string; email: string; password: string }) =>
    api<AuthResponse>('/auth/register', { method: 'POST', body: JSON.stringify(body) }),
  login: (body: { email: string; password: string }) =>
    api<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  me: () => api<User>('/auth/me'),
}

export const ordersApi = {
  list: () => api<Order[]>('/orders'),
  get: (id: number) => api<Order>(`/orders/${id}`),
  create: (body: CreateOrderRequest) =>
    api<Order>('/orders', { method: 'POST', body: JSON.stringify(body) }),
  updateStatus: (id: number, status: OrderStatus) =>
    api<Order>(`/orders/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
}
