import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { errorMessage } from './format'

interface Props {
  title: string
  subtitle: string
  identityLabel: string
  identityType: 'email' | 'text'
  identityPlaceholder?: string
  hint?: ReactNode
  onLogin: (identity: string, password: string) => Promise<void>
}

/** Shared login screen: shop (e-mail) and admin (username). */
export function LoginPage({ title, subtitle, identityLabel, identityType, identityPlaceholder, hint, onLogin }: Props) {
  const [identity, setIdentity] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await onLogin(identity.trim(), password)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <form className="card login" onSubmit={submit}>
        <h1>{title}</h1>
        <p className="muted">{subtitle}</p>
        {error && <div className="banner err">{error}</div>}
        <label>
          {identityLabel}
          <input
            type={identityType}
            value={identity}
            placeholder={identityPlaceholder}
            autoFocus
            required
            autoComplete="username"
            onChange={(e) => setIdentity(e.target.value)}
          />
        </label>
        <label>
          Lozinka
          <input
            type="password"
            value={password}
            required
            autoComplete="current-password"
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button className="btn primary" type="submit" disabled={busy}>
          {busy ? 'Prijava…' : 'Prijavi se'}
        </button>
        {hint && <div className="hint">{hint}</div>}
      </form>
    </div>
  )
}
