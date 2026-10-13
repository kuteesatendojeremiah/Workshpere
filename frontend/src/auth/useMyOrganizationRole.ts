import { useQuery } from '@tanstack/react-query'
import { organizationsApi } from '../api/organizationsApi'
import { PROJECT_MANAGING_ROLES } from '../api/types'
import { useAuth } from './AuthContext'

/** Shares its cache (same query key) with any other screen that lists this org's members,
 * e.g. DashboardPage - navigating between them doesn't re-fetch needlessly. */
export function useMyOrganizationRole(organizationId: string) {
  const { user } = useAuth()

  const membersQuery = useQuery({
    queryKey: ['organizations', organizationId, 'members'],
    queryFn: () => organizationsApi.listMembers(organizationId),
    enabled: organizationId !== '',
  })

  const myRole = membersQuery.data?.content.find((member) => member.userId === user?.id)?.role ?? null
  const canManageProjects = myRole !== null && PROJECT_MANAGING_ROLES.includes(myRole)

  return { myRole, canManageProjects, isLoading: membersQuery.isPending }
}
