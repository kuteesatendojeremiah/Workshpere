import { useId, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { organizationsApi } from '../api/organizationsApi'
import { ApiError, NetworkError } from '../api/client'
import { AppHeader } from '../components/AppHeader'

export function OrganizationSelectorPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const formId = useId()

  const [showCreateForm, setShowCreateForm] = useState(false)
  const [name, setName] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)

  const organizationsQuery = useQuery({
    queryKey: ['organizations', 'mine'],
    queryFn: () => organizationsApi.listMine(),
  })

  const createOrganization = useMutation({
    mutationFn: (organizationName: string) => organizationsApi.create(organizationName),
    onSuccess: async (organization) => {
      await queryClient.invalidateQueries({ queryKey: ['organizations', 'mine'] })
      navigate(`/organizations/${organization.id}`)
    },
    onError: (error: unknown) => {
      setCreateError(
        error instanceof ApiError || error instanceof NetworkError
          ? error.message
          : 'Could not create the organization. Please try again.',
      )
    },
  })

  function handleCreate(event: FormEvent) {
    event.preventDefault()
    setCreateError(null)
    createOrganization.mutate(name)
  }

  return (
    <>
      <AppHeader />
      <main className="page">
        <h1>Your organizations</h1>

        {organizationsQuery.isPending && <p role="status">Loading your organizations…</p>}

        {organizationsQuery.isError && (
          <div className="form-error" role="alert">
            <p>
              {organizationsQuery.error instanceof ApiError || organizationsQuery.error instanceof NetworkError
                ? organizationsQuery.error.message
                : 'Could not load your organizations.'}
            </p>
            <button type="button" className="button-secondary" onClick={() => organizationsQuery.refetch()}>
              Try again
            </button>
          </div>
        )}

        {organizationsQuery.isSuccess && organizationsQuery.data.content.length === 0 && !showCreateForm && (
          <p>You don't belong to any organization yet. Create one to get started.</p>
        )}

        {organizationsQuery.isSuccess && organizationsQuery.data.content.length > 0 && (
          <ul className="org-list">
            {organizationsQuery.data.content.map((organization) => (
              <li key={organization.id}>
                <button
                  type="button"
                  className="org-list__item"
                  onClick={() => navigate(`/organizations/${organization.id}`)}
                >
                  {organization.name}
                </button>
              </li>
            ))}
          </ul>
        )}

        {!showCreateForm && (
          <button type="button" className="button-primary" onClick={() => setShowCreateForm(true)}>
            + New organization
          </button>
        )}

        {showCreateForm && (
          <form onSubmit={handleCreate} className="inline-form">
            {createError && (
              <p className="form-error" role="alert">
                {createError}
              </p>
            )}
            <div className="field">
              <label htmlFor={`${formId}-name`}>Organization name</label>
              <input
                id={`${formId}-name`}
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                autoFocus
              />
            </div>
            <div className="inline-form__actions">
              <button type="submit" className="button-primary" disabled={createOrganization.isPending}>
                {createOrganization.isPending ? 'Creating…' : 'Create'}
              </button>
              <button type="button" className="button-secondary" onClick={() => setShowCreateForm(false)}>
                Cancel
              </button>
            </div>
          </form>
        )}
      </main>
    </>
  )
}
