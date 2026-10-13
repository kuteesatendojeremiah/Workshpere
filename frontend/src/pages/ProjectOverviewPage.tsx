import { useId, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useParams } from 'react-router-dom'
import { projectsApi } from '../api/projectsApi'
import { organizationsApi } from '../api/organizationsApi'
import { ApiError, NetworkError } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { useMyOrganizationRole } from '../auth/useMyOrganizationRole'

export function ProjectOverviewPage() {
  const { organizationId = '', projectId = '' } = useParams<{ organizationId: string; projectId: string }>()
  const formId = useId()
  const queryClient = useQueryClient()
  const hasIds = organizationId !== '' && projectId !== ''

  const { canManageProjects } = useMyOrganizationRole(organizationId)

  const projectQuery = useQuery({
    queryKey: ['organizations', organizationId, 'projects', projectId],
    queryFn: () => projectsApi.get(organizationId, projectId),
    enabled: hasIds,
  })

  const projectMembersQuery = useQuery({
    queryKey: ['organizations', organizationId, 'projects', projectId, 'members'],
    queryFn: () => projectsApi.listMembers(organizationId, projectId),
    enabled: hasIds,
  })

  const orgMembersQuery = useQuery({
    queryKey: ['organizations', organizationId, 'members'],
    queryFn: () => organizationsApi.listMembers(organizationId),
    enabled: hasIds && canManageProjects,
  })

  const invalidateProject = () =>
    queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'projects', projectId] })
  const invalidateProjectMembers = () =>
    queryClient.invalidateQueries({
      queryKey: ['organizations', organizationId, 'projects', projectId, 'members'],
    })

  const [isEditing, setIsEditing] = useState(false)
  const [editName, setEditName] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [editError, setEditError] = useState<string | null>(null)

  const updateProject = useMutation({
    mutationFn: () => projectsApi.update(organizationId, projectId, editName, editDescription || undefined),
    onSuccess: async () => {
      await invalidateProject()
      setIsEditing(false)
    },
    onError: (error: unknown) => setEditError(describeError(error, 'Could not update the project.')),
  })

  const archiveProject = useMutation({
    mutationFn: () => projectsApi.archive(organizationId, projectId),
    onSuccess: invalidateProject,
  })

  const [addMemberError, setAddMemberError] = useState<string | null>(null)
  const addMember = useMutation({
    mutationFn: (userId: string) => projectsApi.addMember(organizationId, projectId, userId),
    onSuccess: invalidateProjectMembers,
    onError: (error: unknown) => setAddMemberError(describeError(error, 'Could not add that member.')),
  })

  const removeMember = useMutation({
    mutationFn: (userId: string) => projectsApi.removeMember(organizationId, projectId, userId),
    onSuccess: invalidateProjectMembers,
  })

  if (!hasIds) {
    return <Navigate to="/" replace />
  }

  function startEditing() {
    if (projectQuery.data) {
      setEditName(projectQuery.data.name)
      setEditDescription(projectQuery.data.description ?? '')
      setEditError(null)
      setIsEditing(true)
    }
  }

  function handleEditSubmit(event: FormEvent) {
    event.preventDefault()
    setEditError(null)
    updateProject.mutate()
  }

  if (projectQuery.isPending) {
    return (
      <>
        <AppHeader showSwitchOrganization />
        <main className="page">
          <p role="status">Loading project…</p>
        </main>
      </>
    )
  }

  if (projectQuery.isError) {
    return (
      <>
        <AppHeader showSwitchOrganization />
        <main className="page">
          <div className="form-error" role="alert">
            <p>{describeError(projectQuery.error, 'Could not load this project.')}</p>
          </div>
        </main>
      </>
    )
  }

  const project = projectQuery.data
  const existingMemberIds = new Set(projectMembersQuery.data?.content.map((m) => m.userId) ?? [])
  const addableOrgMembers = (orgMembersQuery.data?.content ?? []).filter((m) => !existingMemberIds.has(m.userId))

  return (
    <>
      <AppHeader showSwitchOrganization />
      <main className="page">
        <p className="breadcrumb">
          <Link to={`/organizations/${organizationId}/projects`}>&larr; Projects</Link>
        </p>

        {!isEditing && (
          <>
            <h1>
              {project.name}
              {project.status === 'ARCHIVED' && <span className="badge">Archived</span>}
            </h1>
            {project.description && <p>{project.description}</p>}
            <p>
              <Link to={`/organizations/${organizationId}/projects/${projectId}/board`}>View board</Link>
            </p>
            {canManageProjects && (
              <div className="button-group">
                <button type="button" className="button-secondary" onClick={startEditing}>
                  Edit
                </button>
                {project.status === 'ACTIVE' && (
                  <button
                    type="button"
                    className="button-secondary"
                    onClick={() => archiveProject.mutate()}
                    disabled={archiveProject.isPending}
                  >
                    {archiveProject.isPending ? 'Archiving…' : 'Archive'}
                  </button>
                )}
              </div>
            )}
          </>
        )}

        {isEditing && (
          <form onSubmit={handleEditSubmit} className="inline-form">
            {editError && (
              <p className="form-error" role="alert">
                {editError}
              </p>
            )}
            <div className="field">
              <label htmlFor={`${formId}-name`}>Project name</label>
              <input
                id={`${formId}-name`}
                type="text"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
                required
                autoFocus
              />
            </div>
            <div className="field">
              <label htmlFor={`${formId}-description`}>Description</label>
              <input
                id={`${formId}-description`}
                type="text"
                value={editDescription}
                onChange={(e) => setEditDescription(e.target.value)}
              />
            </div>
            <div className="inline-form__actions">
              <button type="submit" className="button-primary" disabled={updateProject.isPending}>
                {updateProject.isPending ? 'Saving…' : 'Save'}
              </button>
              <button type="button" className="button-secondary" onClick={() => setIsEditing(false)}>
                Cancel
              </button>
            </div>
          </form>
        )}

        <h2>Members</h2>
        {projectMembersQuery.isPending && <p role="status">Loading members…</p>}
        {projectMembersQuery.isError && <p className="form-error">Could not load members.</p>}
        {projectMembersQuery.isSuccess && projectMembersQuery.data.content.length === 0 && (
          <p>No members yet.</p>
        )}
        {projectMembersQuery.isSuccess && projectMembersQuery.data.content.length > 0 && (
          <ul className="member-list">
            {projectMembersQuery.data.content.map((member) => (
              <li key={member.userId}>
                <span>
                  {member.displayName} <span className="member-list__email">{member.email}</span>
                </span>
                {canManageProjects && (
                  <button
                    type="button"
                    className="button-secondary"
                    onClick={() => removeMember.mutate(member.userId)}
                    disabled={removeMember.isPending}
                  >
                    Remove
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}

        {canManageProjects && addableOrgMembers.length > 0 && (
          <div className="inline-form">
            <label htmlFor={`${formId}-add-member`}>Add a member from this organization</label>
            {addMemberError && (
              <p className="form-error" role="alert">
                {addMemberError}
              </p>
            )}
            <select
              id={`${formId}-add-member`}
              value=""
              onChange={(e) => {
                if (e.target.value) {
                  setAddMemberError(null)
                  addMember.mutate(e.target.value)
                }
              }}
            >
              <option value="" disabled>
                Choose a person…
              </option>
              {addableOrgMembers.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {member.displayName} ({member.email})
                </option>
              ))}
            </select>
          </div>
        )}
      </main>
    </>
  )
}

function describeError(error: unknown, fallback: string): string {
  if (error instanceof ApiError || error instanceof NetworkError) {
    return error.message
  }
  return fallback
}
