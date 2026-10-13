import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { OrganizationSelectorPage } from './pages/OrganizationSelectorPage'
import { DashboardPage } from './pages/DashboardPage'
import { ProjectsListPage } from './pages/ProjectsListPage'
import { ProjectOverviewPage } from './pages/ProjectOverviewPage'
import { KanbanBoardPage } from './pages/KanbanBoardPage'
import { TaskDetailsPage } from './pages/TaskDetailsPage'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <OrganizationSelectorPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/organizations/:organizationId"
              element={
                <ProtectedRoute>
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/organizations/:organizationId/projects"
              element={
                <ProtectedRoute>
                  <ProjectsListPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/organizations/:organizationId/projects/:projectId"
              element={
                <ProtectedRoute>
                  <ProjectOverviewPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/organizations/:organizationId/projects/:projectId/board"
              element={
                <ProtectedRoute>
                  <KanbanBoardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/organizations/:organizationId/projects/:projectId/tasks/:taskId"
              element={
                <ProtectedRoute>
                  <TaskDetailsPage />
                </ProtectedRoute>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
