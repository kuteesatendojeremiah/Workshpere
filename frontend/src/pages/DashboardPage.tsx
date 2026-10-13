import { useQuery } from '@tanstack/react-query'
import { Link, Navigate, useParams } from 'react-router-dom'
import { organizationsApi } from '../api/organizationsApi'
import { projectsApi } from '../api/projectsApi'
import { ApiError, NetworkError } from '../api/client'
import { AppHeader } from '../components/AppHeader'

export function DashboardPage() {
  // The route is "/organizations/:organizationId", so this is always present once matched -
  // the `= ''` fallback exists only so hooks below can be called unconditionally (never
  // conditionally, per the rules of hooks) even in that impossible case.
  const { organizationId = '' } = useParams<{ organizationId: string }>()
  const hasOrganizationId = organizationId !== ''

  const organizationQuery = useQuery({
    queryKey: ['organizations', organizationId],
    queryFn: () => organizationsApi.get(organizationId),
    enabled: hasOrganizationId,
  })

  const membersQuery = useQuery({
    queryKey: ['organizations', organizationId, 'members'],
    queryFn: () => organizationsApi.listMembers(organizationId),
    enabled: hasOrganizationId && organizationQuery.isSuccess,
  })

  const projectsQuery = useQuery({
    queryKey: ['organizations', organizationId, 'projects'],
    queryFn: () => projectsApi.list(organizationId),
    enabled: hasOrganizationId && organizationQuery.isSuccess,
  })

  if (!hasOrganizationId) {
    return <Navigate to="/" replace />
  }

  if (organizationQuery.isPending) {
    return (
      <>
        <AppHeader showSwitchOrganization />
        <main className="page">
          <p role="status">Loading organization…</p>
        </main>
      </>
    )
  }

  if (organizationQuery.isError) {
    const error = organizationQuery.error
    const message =
      error instanceof ApiError && error.code === 'ORGANIZATION_NOT_FOUND'
        ? "This organization doesn't exist, or you're not a member of it."
        : error instanceof ApiError || error instanceof NetworkError
          ? error.message
          : 'Could not load this organization.'

    return (
      <>
        <AppHeader showSwitchOrganization />
        <main className="page">
          <div className="form-error" role="alert">
            <p>{message}</p>
          </div>
        </main>
      </>
    )
  }

  const organization = organizationQuery.data

  return (
    <>
      <AppHeader showSwitchOrganization />
      <main className="page">
        <h1>{organization.name}</h1>

        <section className="stat-row" aria-label="Organization summary">
          <div className="stat-tile">
            <span className="stat-tile__value">
              {membersQuery.isPending ? '…' : membersQuery.data?.totalElements ?? '—'}
            </span>
            <span className="stat-tile__label">Members</span>
          </div>
          <div className="stat-tile">
            <span className="stat-tile__value">
              {projectsQuery.isPending ? '…' : projectsQuery.data?.totalElements ?? '—'}
            </span>
            <span className="stat-tile__label">Projects</span>
          </div>
        </section>

        <h2>
          Projects <Link to={`/organizations/${organizationId}/projects`}>View all</Link>
        </h2>
        {projectsQuery.isPending && <p role="status">Loading projects…</p>}
        {projectsQuery.isError && (
          <p className="form-error" role="alert">
            Could not load projects.
          </p>
        )}
        {projectsQuery.isSuccess && projectsQuery.data.content.length === 0 && (
          <p>No projects yet in this organization.</p>
        )}
        {projectsQuery.isSuccess && projectsQuery.data.content.length > 0 && (
          <ul className="project-list">
            {projectsQuery.data.content.slice(0, 5).map((project) => (
              <li key={project.id}>
                <Link to={`/organizations/${organizationId}/projects/${project.id}`}>{project.name}</Link>
                {project.status === 'ARCHIVED' && <span className="badge">Archived</span>}
              </li>
            ))}
          </ul>
        )}
      </main>
    </>
  )
}
