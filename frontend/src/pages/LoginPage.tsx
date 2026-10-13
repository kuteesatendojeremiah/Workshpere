import { useId, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiError, NetworkError } from '../api/client'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const formId = useId()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [status, setStatus] = useState<'idle' | 'submitting' | 'error'>('idle')
  const [formError, setFormError] = useState<string | null>(null)

  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/'

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setStatus('submitting')
    setFormError(null)

    try {
      await login(email, password)
      navigate(redirectTo, { replace: true })
    } catch (error) {
      setStatus('error')
      if (error instanceof ApiError) {
        // Deliberately the same message for "no such email" and "wrong password" - the
        // backend already collapses these into one INVALID_CREDENTIALS code to prevent
        // account enumeration, and the frontend shouldn't undo that by being more specific.
        setFormError(error.code === 'INVALID_CREDENTIALS' ? 'Incorrect email or password.' : error.message)
      } else if (error instanceof NetworkError) {
        setFormError(error.message)
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit} noValidate aria-busy={status === 'submitting'}>
        <h1>Log in</h1>

        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}

        <div className="field">
          <label htmlFor={`${formId}-email`}>Email</label>
          <input
            id={`${formId}-email`}
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
            autoFocus
          />
        </div>

        <div className="field">
          <label htmlFor={`${formId}-password`}>Password</label>
          <input
            id={`${formId}-password`}
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </div>

        <button type="submit" className="button-primary" disabled={status === 'submitting'}>
          {status === 'submitting' ? 'Logging in…' : 'Log in'}
        </button>

        <p className="auth-switch">
          Don't have an account? <Link to="/register">Create one</Link>
        </p>
      </form>
    </main>
  )
}
