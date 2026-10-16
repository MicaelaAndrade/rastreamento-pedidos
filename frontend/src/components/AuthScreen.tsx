import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { authApi } from '../api/endpoints'
import { useAuth } from '../auth/useAuth'
import { ROUTE, STATUS_LABEL } from '../lib/status'

type Mode = 'login' | 'register'

export function AuthScreen() {
  const { signIn } = useAuth()
  const [mode, setMode] = useState<Mode>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const mutation = useMutation({
    mutationFn: () => {
      const cleanEmail = email.trim()
      return mode === 'login'
        ? authApi.login({ email: cleanEmail, password })
        : authApi.register({ name: name.trim(), email: cleanEmail, password })
    },
    onSuccess: signIn,
  })

  const error = mutation.error instanceof ApiError ? mutation.error : null

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  function switchMode(next: Mode) {
    setMode(next)
    mutation.reset()
  }

  return (
    <main className="auth">
      <section className="auth-intro" aria-hidden="true">
        <p className="brand">Despacho</p>
        <ol className="auth-route">
          {ROUTE.map((status) => (
            <li key={status}>{STATUS_LABEL[status]}</li>
          ))}
        </ol>
        <p className="auth-tagline">Do pedido recebido à porta do cliente, num só painel.</p>
      </section>

      <section className="auth-card">
        <div className="auth-tabs" role="tablist">
          <button role="tab" aria-selected={mode === 'login'} onClick={() => switchMode('login')}>
            Entrar
          </button>
          <button role="tab" aria-selected={mode === 'register'} onClick={() => switchMode('register')}>
            Criar conta
          </button>
        </div>

        <form onSubmit={handleSubmit} noValidate>
          {mode === 'register' && (
            <label className="field">
              <span>Nome</span>
              <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required />
            </label>
          )}
          <label className="field">
            <span>E-mail</span>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
          </label>
          <div className="field">
            <label htmlFor="password">Senha</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              aria-describedby={mode === 'register' ? 'password-hint' : undefined}
              minLength={8}
              required
            />
            {mode === 'register' && <small id="password-hint">Mínimo de 8 caracteres.</small>}
          </div>

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

          <button type="submit" className="btn btn-primary btn-block" disabled={mutation.isPending}>
            {mutation.isPending ? 'Aguarde…' : mode === 'login' ? 'Entrar' : 'Criar conta e entrar'}
          </button>
        </form>
      </section>
    </main>
  )
}
