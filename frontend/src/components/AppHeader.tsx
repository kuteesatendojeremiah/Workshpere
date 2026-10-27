import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { NotificationBadge } from './NotificationBadge'

export function AppHeader({ showSwitchOrganization = false }: { showSwitchOrganization?: boolean }) {
  const { user, logout } = useAuth()

  return (
    <header className="app-header">
      <span className="app-header__title">WorkSphere</span>
      <nav className="app-header__nav" aria-label="Account">
        {showSwitchOrganization && <Link to="/">Switch organization</Link>}
        {user && <NotificationBadge />}
        {user && <span className="app-header__user">{user.displayName}</span>}
        <button type="button" className="button-secondary" onClick={() => void logout()}>
          Log out
        </button>
      </nav>
    </header>
  )
}
