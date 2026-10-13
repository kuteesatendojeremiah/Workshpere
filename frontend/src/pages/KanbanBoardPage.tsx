import { useEffect, useId, useState, type FormEvent } from 'react'
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useMutation, useQueries, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useParams } from 'react-router-dom'
import { tasksApi } from '../api/tasksApi'
import { ApiError, NetworkError } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { useMyOrganizationRole } from '../auth/useMyOrganizationRole'
import {
  TASK_PRIORITIES,
  TASK_STATUSES,
  TASK_STATUS_LABELS,
  canTransitionTo,
  type TaskPriority,
  type TaskResponse,
  type TaskStatus,
} from '../api/types'

type ColumnMap = Record<TaskStatus, TaskResponse[]>

function emptyColumns(): ColumnMap {
  return { TODO: [], IN_PROGRESS: [], IN_REVIEW: [], DONE: [] }
}

export function KanbanBoardPage() {
  const { organizationId = '', projectId = '' } = useParams<{ organizationId: string; projectId: string }>()
  const hasIds = organizationId !== '' && projectId !== ''
  const queryClient = useQueryClient()
  const formId = useId()

  const { canManageProjects } = useMyOrganizationRole(organizationId)

  function columnQueryKey(status: TaskStatus) {
    return ['organizations', organizationId, 'projects', projectId, 'tasks', status]
  }

  const columnQueries = useQueries({
    queries: TASK_STATUSES.map((status) => ({
      queryKey: columnQueryKey(status),
      queryFn: () => tasksApi.listByStatus(projectId, status),
      enabled: hasIds,
    })),
  })

  const allSucceeded = columnQueries.every((q) => q.isSuccess)
  const anyPending = columnQueries.some((q) => q.isPending)
  const anyError = columnQueries.find((q) => q.isError)?.error

  const [columns, setColumns] = useState<ColumnMap>(emptyColumns)
  const [activeTask, setActiveTask] = useState<TaskResponse | null>(null)
  const [moveError, setMoveError] = useState<string | null>(null)

  useEffect(() => {
    if (!allSucceeded || activeTask) {
      return
    }
    const next = emptyColumns()
    TASK_STATUSES.forEach((status, index) => {
      next[status] = columnQueries[index].data?.content ?? []
    })
    setColumns(next)
    // Only re-sync from the server when nothing is mid-drag - re-syncing during a drag would
    // yank the card out from under the user's cursor.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allSucceeded, ...columnQueries.map((q) => q.data)])

  const [newTitle, setNewTitle] = useState('')
  const [newPriority, setNewPriority] = useState<TaskPriority>('MEDIUM')
  const [createError, setCreateError] = useState<string | null>(null)

  const createTask = useMutation({
    mutationFn: () => tasksApi.create(projectId, newTitle, newPriority),
    onSuccess: async () => {
      setNewTitle('')
      setNewPriority('MEDIUM')
      await queryClient.invalidateQueries({ queryKey: columnQueryKey('TODO') })
    },
    onError: (error: unknown) => setCreateError(describeError(error, 'Could not create the task.')),
  })

  const moveTask = useMutation({
    mutationFn: ({ task, status, position }: { task: TaskResponse; status: TaskStatus; position: number }) =>
      tasksApi.changeStatus(task.id, status, position, task.version),
    onSuccess: (_result, variables) => {
      void queryClient.invalidateQueries({ queryKey: columnQueryKey(variables.task.status) })
      void queryClient.invalidateQueries({ queryKey: columnQueryKey(variables.status) })
    },
    onError: (error: unknown) => {
      setMoveError(describeError(error, 'Could not move the task. It may have changed since you last saw it.'))
      TASK_STATUSES.forEach((status) => void queryClient.invalidateQueries({ queryKey: columnQueryKey(status) }))
    },
  })

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  function findColumnOf(taskId: string): TaskStatus | null {
    return TASK_STATUSES.find((status) => columns[status].some((task) => task.id === taskId)) ?? null
  }

  function handleDragStart(event: DragStartEvent) {
    const task = TASK_STATUSES.flatMap((status) => columns[status]).find((t) => t.id === event.active.id)
    setActiveTask(task ?? null)
  }

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    setActiveTask(null)
    if (!over) {
      return
    }

    const sourceStatus = findColumnOf(String(active.id))
    if (!sourceStatus) {
      return
    }
    const task = columns[sourceStatus].find((t) => t.id === active.id)
    if (!task) {
      return
    }

    const overId = String(over.id)
    const destStatus = (TASK_STATUSES as readonly string[]).includes(overId)
      ? (overId as TaskStatus)
      : findColumnOf(overId)
    if (!destStatus) {
      return
    }

    const destColumn = columns[destStatus]
    const overIndex = destColumn.findIndex((t) => t.id === overId)
    const destIndex = overIndex === -1 ? destColumn.length : overIndex

    if (sourceStatus === destStatus) {
      const sourceIndex = destColumn.findIndex((t) => t.id === active.id)
      if (sourceIndex === destIndex) {
        return
      }
    } else if (!canTransitionTo(sourceStatus, destStatus)) {
      setMoveError(
        `A task can't jump from "${TASK_STATUS_LABELS[sourceStatus]}" straight to "${TASK_STATUS_LABELS[destStatus]}" - move it one column at a time.`,
      )
      return
    }

    setMoveError(null)
    setColumns((prev) => {
      const next = emptyColumns()
      TASK_STATUSES.forEach((status) => (next[status] = [...prev[status]]))
      next[sourceStatus] = next[sourceStatus].filter((t) => t.id !== task.id)
      const insertAt = sourceStatus === destStatus && destIndex > next[destStatus].length ? next[destStatus].length : destIndex
      next[destStatus].splice(insertAt, 0, task)
      return next
    })

    moveTask.mutate({ task, status: destStatus, position: destIndex })
  }

  const taskPath = `/organizations/${organizationId}/projects/${projectId}/tasks`

  if (!hasIds) {
    return <Navigate to="/" replace />
  }

  return (
    <>
      <AppHeader showSwitchOrganization />
      <main className="page page--wide">
        <p className="breadcrumb">
          <Link to={`/organizations/${organizationId}/projects/${projectId}`}>&larr; Project</Link>
        </p>
        <h1>Board</h1>

        {canManageProjects && (
          <form
            className="inline-form"
            onSubmit={(event: FormEvent) => {
              event.preventDefault()
              setCreateError(null)
              createTask.mutate()
            }}
          >
            {createError && (
              <p className="form-error" role="alert">
                {createError}
              </p>
            )}
            <div className="field">
              <label htmlFor={`${formId}-title`}>New task</label>
              <input
                id={`${formId}-title`}
                type="text"
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                placeholder="Task title"
                required
              />
            </div>
            <div className="field">
              <label htmlFor={`${formId}-priority`}>Priority</label>
              <select
                id={`${formId}-priority`}
                value={newPriority}
                onChange={(e) => setNewPriority(e.target.value as TaskPriority)}
              >
                {TASK_PRIORITIES.map((priority) => (
                  <option key={priority} value={priority}>
                    {priority}
                  </option>
                ))}
              </select>
            </div>
            <button type="submit" className="button-primary" disabled={createTask.isPending}>
              {createTask.isPending ? 'Adding…' : 'Add task'}
            </button>
          </form>
        )}

        {moveError && (
          <p className="form-error" role="alert">
            {moveError}
          </p>
        )}

        {anyPending && <p role="status">Loading board…</p>}
        {anyError && (
          <p className="form-error" role="alert">
            {describeError(anyError, 'Could not load the board.')}
          </p>
        )}

        {allSucceeded && (
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragStart={handleDragStart}
            onDragEnd={handleDragEnd}
          >
            <div className="board">
              {TASK_STATUSES.map((status) => (
                <Column key={status} status={status} tasks={columns[status]} taskPath={taskPath} />
              ))}
            </div>
            <DragOverlay>{activeTask && <TaskCard task={activeTask} taskPath={taskPath} overlay />}</DragOverlay>
          </DndContext>
        )}
      </main>
    </>
  )
}

function Column({
  status,
  tasks,
  taskPath,
}: {
  status: TaskStatus
  tasks: TaskResponse[]
  taskPath: string
}) {
  const { setNodeRef } = useDroppable({ id: status })

  return (
    <section className="board-column" aria-label={TASK_STATUS_LABELS[status]}>
      <h2 className="board-column__title">
        {TASK_STATUS_LABELS[status]} <span className="badge">{tasks.length}</span>
      </h2>
      <div ref={setNodeRef} className="board-column__dropzone">
        <SortableContext items={tasks.map((t) => t.id)} strategy={verticalListSortingStrategy}>
          {tasks.length === 0 && <p className="board-column__empty">No tasks</p>}
          {tasks.map((task) => (
            <TaskCard key={task.id} task={task} taskPath={taskPath} />
          ))}
        </SortableContext>
      </div>
    </section>
  )
}

function TaskCard({ task, taskPath, overlay = false }: { task: TaskResponse; taskPath: string; overlay?: boolean }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: task.id })

  const style = overlay
    ? undefined
    : {
        transform: CSS.Translate.toString(transform),
        transition,
        opacity: isDragging ? 0.4 : 1,
      }

  return (
    <div
      ref={overlay ? undefined : setNodeRef}
      style={style}
      className="task-card"
      {...(overlay ? {} : attributes)}
      {...(overlay ? {} : listeners)}
      tabIndex={overlay ? undefined : 0}
    >
      <p className="task-card__title">
        {overlay ? task.title : <Link to={`${taskPath}/${task.id}`}>{task.title}</Link>}
      </p>
      <span className={`badge badge--priority-${task.priority.toLowerCase()}`}>{task.priority}</span>
    </div>
  )
}

function describeError(error: unknown, fallback: string): string {
  if (error instanceof ApiError || error instanceof NetworkError) {
    return error.message
  }
  return fallback
}
