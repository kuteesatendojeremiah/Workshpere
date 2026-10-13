import { useId, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { authApi } from '../api/authApi'
import { ApiError, NetworkError } from '../api/client'

interface FieldErrors {
  email?: string
  password?: string
  displayName?: string
}

export function RegisterPage() {
  const formId = useId()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')

  const [status, setStatus] = useState<'idle' | 'submitting' | 'success' | 'error'>('idle')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setStatus('submitting')
    setFieldErrors({})
    setFormError(null)

    try {
      await authApi.register({ email, password, displayName })
      setStatus('success')
    } catch (error) {
      setStatus('error')
      if (error instanceof ApiError) {
        if (error.code === 'VALIDATION_ERROR') {
          const next: FieldErrors = {}
          for (const detail of error.details) {
            if (detail.field === 'email' || detail.field === 'password' || detail.field === 'displayName') {
              next[detail.field] = detail.message
            }
          }
          setFieldErrors(next)
        } else if (error.code === 'EMAIL_ALREADY_REGISTERED') {
          setFieldErrors({ email: error.message })
        } else {
          setFormError(error.message)
        }
      } else if (error instanceof NetworkError) {
        setFormError(error.message)
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    }
  }

  if (status === 'success') {
    return (
      <main className="auth-page">
        <div className="auth-card" role="status">
          <h1>Account created</h1>
          <p>
            You can log in right away. (In a full deployment we'd also send a verification email at
            this point - that arrives in a later step of this project.)
          </p>
          <Link to="/login" className="button-primary">
            Go to login
          </Link>
        </div>
      </main>
    )
  }

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit} noValidate aria-busy={status === 'submitting'}>
        <h1>Create your account</h1>

        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}

        <div className="field">
          <label htmlFor={`${formId}-displayName`}>Name</label>
          <input
            id={`${formId}-displayName`}
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            autoComplete="name"
            required
            aria-invalid={fieldErrors.displayName ? true : undefined}
            aria-describedby={fieldErrors.displayName ? `${formId}-displayName-error` : undefined}
          />
          {fieldErrors.displayName && (
            <p className="field-error" id={`${formId}-displayName-error`}>
              {fieldErrors.displayName}
            </p>
          )}
        </div>

        <div className="field">
          <label htmlFor={`${formId}-email`}>Email</label>
          <input
            id={`${formId}-email`}
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
            aria-invalid={fieldErrors.email ? true : undefined}
            aria-describedby={fieldErrors.email ? `${formId}-email-error` : undefined}
          />
          {fieldErrors.email && (
            <p className="field-error" id={`${formId}-email-error`}>
              {fieldErrors.email}
            </p>
          )}
        </div>

        <div className="field">
          <label htmlFor={`${formId}-password`}>Password</label>
          <input
            id={`${formId}-password`}
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            minLength={8}
            required
            aria-invalid={fieldErrors.password ? true : undefined}
            aria-describedby={fieldErrors.password ? `${formId}-password-error` : undefined}
          />
          {fieldErrors.password && (
            <p className="field-error" id={`${formId}-password-error`}>
              {fieldErrors.password}
            </p>
          )}
        </div>

        <button type="submit" className="button-primary" disabled={status === 'submitting'}>
          {status === 'submitting' ? 'Creating account…' : 'Create account'}
        </button>

        <p className="auth-switch">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </main>
  )
}
