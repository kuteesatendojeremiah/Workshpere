import { useId, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useParams } from 'react-router-dom'
import { projectsApi } from '../api/projectsApi'
import { ApiError, NetworkError } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { useMyOrganizationRole } from '../auth/useMyOrganizationRole'

export function ProjectsListPage() {
  const { organizationId = '' } = useParams<{ organizationId: string }>()
  const formId = useId()
  const queryClient = useQueryClient()

  const [showCreateForm, setShowCreateForm] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)

  const { canManageProjects } = useMyOrganizationRole(organizationId)

  const projectsQuery = useQuery({
    queryKey: ['organizations', organizationId, 'projects'],
    queryFn: () => projectsApi.list(organizationId),
    enabled: organizationId !== '',
  })

  const createProject = useMutation({
    mutationFn: () => projectsApi.create(organizationId, name, description || undefined),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'projects'] })
      setShowCreateForm(false)
      setName('')
      setDescription('')
    },
    onError: (error: unknown) => {
      setCreateError(
        error instanceof ApiError || error instanceof NetworkError
          ? error.message
          : 'Could not create the project. Please try again.',
      )
    },
  })

  if (organizationId === '') {
    return <Navigate to="/" replace />
  }

  function handleCreate(event: FormEvent) {
    event.preventDefault()
    setCreateError(null)
    createProject.mutate()
  }

  return (
    <>
      <AppHeader showSwitchOrganization />
      <main className="page">
        <p className="breadcrumb">
          <Link to={`/organizations/${organizationId}`}>&larr; Dashboard</Link>
        </p>
        <h1>Projects</h1>

        {projectsQuery.isPending && <p role="status">Loading projects…</p>}

        {projectsQuery.isError && (
          <div className="form-error" role="alert">
            <p>
              {projectsQuery.error instanceof ApiError || projectsQuery.error instanceof NetworkError
                ? projectsQuery.error.message
                : 'Could not load projects.'}
            </p>
            <button type="button" className="button-secondary" onClick={() => projectsQuery.refetch()}>
              Try again
            </button>
          </div>
        )}

        {projectsQuery.isSuccess && projectsQuery.data.content.length === 0 && !showCreateForm && (
          <p>No projects yet. {canManageProjects && 'Create the first one to get started.'}</p>
        )}

        {projectsQuery.isSuccess && projectsQuery.data.content.length > 0 && (
          <ul className="project-list">
            {projectsQuery.data.content.map((project) => (
              <li key={project.id}>
                <Link to={`/organizations/${organizationId}/projects/${project.id}`}>{project.name}</Link>
                {project.status === 'ARCHIVED' && <span className="badge">Archived</span>}
              </li>
            ))}
          </ul>
        )}

        {canManageProjects && !showCreateForm && (
          <button type="button" className="button-primary" onClick={() => setShowCreateForm(true)}>
            + New project
          </button>
        )}

        {canManageProjects && showCreateForm && (
          <form onSubmit={handleCreate} className="inline-form">
            {createError && (
              <p className="form-error" role="alert">
                {createError}
              </p>
            )}
            <div className="field">
              <label htmlFor={`${formId}-name`}>Project name</label>
              <input
                id={`${formId}-name`}
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                autoFocus
              />
            </div>
            <div className="field">
              <label htmlFor={`${formId}-description`}>Description (optional)</label>
              <input
                id={`${formId}-description`}
                type="text"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
            <div className="inline-form__actions">
              <button type="submit" className="button-primary" disabled={createProject.isPending}>
                {createProject.isPending ? 'Creating…' : 'Create'}
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
