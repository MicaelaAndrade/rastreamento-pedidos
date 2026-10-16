import type { ProblemDetail } from './types'

const TOKEN_KEY = 'rastreamento.token'

export const tokenStorage = {
  get: (): string | null => {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set: (token: string) => {
    try {
      localStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* armazenamento indisponível: a sessão vale só até recarregar a página */
    }
  },
  clear: () => {
    try {
      localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* idem */
    }
  },
}

/** Erro de API com a mensagem já pronta para mostrar na tela. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: { field: string; message: string }[]

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `Erro inesperado (HTTP ${status})`)
    this.status = status
    this.fieldErrors = problem?.errors ?? []
  }
}

/** Chamado quando a API responde 401: o token expirou ou é inválido. */
let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  const token = tokenStorage.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`/api${path}`, { ...init, headers })

  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    // Só desloga se havia sessão; um 401 no login é "senha errada", não "sessão expirada".
    if (response.status === 401 && token) onUnauthorized()
    throw new ApiError(response.status, problem)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
