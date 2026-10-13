import { useEffect, useId, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { ApiError, NetworkError } from "../api/client";
import { commentsApi } from "../api/commentsApi";
import { labelsApi } from "../api/labelsApi";
import { projectsApi } from "../api/projectsApi";
import { tasksApi } from "../api/tasksApi";
import { AppHeader } from "../components/AppHeader";
import { useAuth } from "../auth/AuthContext";
import { useMyOrganizationRole } from "../auth/useMyOrganizationRole";
import {
  TASK_PRIORITIES,
  TASK_STATUS_LABELS,
  type LabelResponse,
  type TaskPriority,
  type TaskResponse,
} from "../api/types";

export function TaskDetailsPage() {
  const {
    organizationId = "",
    projectId = "",
    taskId = "",
  } = useParams<{
    organizationId: string;
    projectId: string;
    taskId: string;
  }>();
  const hasIds = organizationId !== "" && projectId !== "" && taskId !== "";
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const formId = useId();
  const { user } = useAuth();
  const { canManageProjects } = useMyOrganizationRole(organizationId);

  const projectPath = `/organizations/${organizationId}/projects/${projectId}`;
  const taskKey = ["tasks", taskId];
  const commentsKey = ["tasks", taskId, "comments"];
  const taskLabelsKey = ["tasks", taskId, "labels"];
  const boardKey = [
    "organizations",
    organizationId,
    "projects",
    projectId,
    "tasks",
  ];

  const taskQuery = useQuery({
    queryKey: taskKey,
    queryFn: () => tasksApi.get(taskId),
    enabled: hasIds,
  });
  const commentsQuery = useQuery({
    queryKey: commentsKey,
    queryFn: () => commentsApi.list(taskId),
    enabled: hasIds,
  });
  const taskLabelsQuery = useQuery({
    queryKey: taskLabelsKey,
    queryFn: () => labelsApi.listForTask(taskId),
    enabled: hasIds,
  });
  const projectLabelsQuery = useQuery({
    queryKey: ["projects", projectId, "labels"],
    queryFn: () => labelsApi.listForProject(projectId),
    enabled: hasIds,
  });
  const membersQuery = useQuery({
    queryKey: [
      "organizations",
      organizationId,
      "projects",
      projectId,
      "members",
    ],
    queryFn: () => projectsApi.listMembers(organizationId, projectId),
    enabled: hasIds,
  });

  const task = taskQuery.data;

  // ---- Edit form (title / description / priority / due date) ----
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<TaskPriority>("MEDIUM");
  const [dueDate, setDueDate] = useState("");
  const [editError, setEditError] = useState<string | null>(null);
  const [editSaved, setEditSaved] = useState(false);

  function loadForm(source: TaskResponse) {
    setTitle(source.title);
    setDescription(source.description ?? "");
    setPriority(source.priority);
    setDueDate(source.dueDate ?? "");
  }

  // Re-seed the form whenever the server copy changes (first load, or after a reload from a conflict).
  useEffect(() => {
    if (task) {
      loadForm(task);
    }
  }, [task]);

  const updateTask = useMutation({
    mutationFn: (current: TaskResponse) =>
      tasksApi.update(taskId, {
        title,
        description: description.trim() === "" ? null : description,
        priority,
        dueDate: dueDate === "" ? null : dueDate,
        version: current.version,
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData(taskKey, saved);
      setEditSaved(true);
      void queryClient.invalidateQueries({ queryKey: boardKey });
    },
    onError: (error: unknown) =>
      setEditError(describeConflict(error, "Could not save the task.")),
  });

  const assignTask = useMutation({
    mutationFn: ({
      current,
      assigneeId,
    }: {
      current: TaskResponse;
      assigneeId: string | null;
    }) => tasksApi.assign(taskId, assigneeId, current.version),
    onSuccess: (saved) => {
      queryClient.setQueryData(taskKey, saved);
      void queryClient.invalidateQueries({ queryKey: boardKey });
    },
  });

  const deleteTask = useMutation({
    mutationFn: () => tasksApi.delete(taskId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: boardKey });
      navigate(`${projectPath}/board`);
    },
  });
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  // ---- Labels ----
  const attachLabel = useMutation({
    mutationFn: (labelId: string) => labelsApi.attach(taskId, labelId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: taskLabelsKey }),
  });
  const detachLabel = useMutation({
    mutationFn: (labelId: string) => labelsApi.detach(taskId, labelId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: taskLabelsKey }),
  });

  // ---- Comments ----
  const [commentBody, setCommentBody] = useState("");
  const addComment = useMutation({
    mutationFn: () => commentsApi.create(taskId, commentBody),
    onSuccess: async () => {
      setCommentBody("");
      await queryClient.invalidateQueries({ queryKey: commentsKey });
    },
  });
  const deleteComment = useMutation({
    mutationFn: (commentId: string) => commentsApi.delete(taskId, commentId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: commentsKey }),
  });

  if (!hasIds) {
    return <Navigate to="/" replace />;
  }

  const attachedLabels = taskLabelsQuery.data ?? [];
  const availableLabels = (projectLabelsQuery.data ?? []).filter(
    (label) => !attachedLabels.some((attached) => attached.id === label.id),
  );
  const comments = commentsQuery.data?.content ?? [];
  const members = membersQuery.data?.content ?? [];

  return (
    <>
      <AppHeader showSwitchOrganization />
      <main className="page page--wide">
        <p className="breadcrumb">
          <Link to={`${projectPath}/board`}>&larr; Board</Link>
        </p>

        {taskQuery.isPending && <p role="status">Loading task…</p>}
        {taskQuery.isError && (
          <p className="form-error" role="alert">
            {describeError(taskQuery.error, "Could not load the task.")}
          </p>
        )}

        {task && (
          <>
            <h1>{task.title}</h1>
            <p>
              <span className="badge">{TASK_STATUS_LABELS[task.status]}</span>
              <span
                className={`badge badge--priority-${task.priority.toLowerCase()}`}
              >
                {task.priority}
              </span>
            </p>

            <form
              className="inline-form inline-form--wide"
              onSubmit={(event: FormEvent) => {
                event.preventDefault();
                setEditError(null);
                setEditSaved(false);
                updateTask.mutate(task);
              }}
            >
              <h2>Details</h2>
              {editError && (
                <p className="form-error" role="alert">
                  {editError}
                  {editError.includes("changed") && (
                    <>
                      {" "}
                      <button
                        type="button"
                        className="button-secondary"
                        onClick={() => {
                          setEditError(null);
                          void taskQuery.refetch();
                        }}
                      >
                        Reload task
                      </button>
                    </>
                  )}
                </p>
              )}
              {editSaved && <p role="status">Saved.</p>}
              <div className="field">
                <label htmlFor={`${formId}-title`}>Title</label>
                <input
                  id={`${formId}-title`}
                  type="text"
                  value={title}
                  maxLength={255}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor={`${formId}-description`}>Description</label>
                <textarea
                  id={`${formId}-description`}
                  value={description}
                  maxLength={4000}
                  rows={5}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </div>
              <div className="field">
                <label htmlFor={`${formId}-priority`}>Priority</label>
                <select
                  id={`${formId}-priority`}
                  value={priority}
                  onChange={(e) => setPriority(e.target.value as TaskPriority)}
                >
                  {TASK_PRIORITIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor={`${formId}-due`}>Due date</label>
                <input
                  id={`${formId}-due`}
                  type="date"
                  value={dueDate}
                  onChange={(e) => setDueDate(e.target.value)}
                />
              </div>
              <button
                type="submit"
                className="button-primary"
                disabled={updateTask.isPending}
              >
                {updateTask.isPending ? "Saving…" : "Save changes"}
              </button>
            </form>

            <section
              className="task-section"
              aria-labelledby={`${formId}-assignee-h`}
            >
              <h2 id={`${formId}-assignee-h`}>Assignee</h2>
              {assignTask.isError && (
                <p className="form-error" role="alert">
                  {describeConflict(
                    assignTask.error,
                    "Could not change the assignee.",
                  )}
                </p>
              )}
              {membersQuery.isError ? (
                <p className="form-error" role="alert">
                  {describeError(
                    membersQuery.error,
                    "Could not load project members.",
                  )}
                </p>
              ) : (
                <div className="field">
                  <label htmlFor={`${formId}-assignee`}>Assigned to</label>
                  <select
                    id={`${formId}-assignee`}
                    value={task.assigneeId ?? ""}
                    disabled={membersQuery.isPending || assignTask.isPending}
                    onChange={(e) =>
                      assignTask.mutate({
                        current: task,
                        assigneeId: e.target.value || null,
                      })
                    }
                  >
                    <option value="">Unassigned</option>
                    {members.map((member) => (
                      <option key={member.userId} value={member.userId}>
                        {member.displayName}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </section>

            <section
              className="task-section"
              aria-labelledby={`${formId}-labels-h`}
            >
              <h2 id={`${formId}-labels-h`}>Labels</h2>
              {taskLabelsQuery.isPending && (
                <p role="status">Loading labels…</p>
              )}
              {taskLabelsQuery.isError && (
                <p className="form-error" role="alert">
                  {describeError(
                    taskLabelsQuery.error,
                    "Could not load labels.",
                  )}
                </p>
              )}
              {(attachLabel.isError || detachLabel.isError) && (
                <p className="form-error" role="alert">
                  {describeError(
                    attachLabel.error ?? detachLabel.error,
                    "Could not change the labels.",
                  )}
                </p>
              )}
              {taskLabelsQuery.isSuccess && attachedLabels.length === 0 && (
                <p>No labels on this task.</p>
              )}
              <ul className="label-list">
                {attachedLabels.map((label) => (
                  <li key={label.id}>
                    <LabelChip label={label} />
                    <button
                      type="button"
                      className="button-secondary"
                      aria-label={`Remove label ${label.name}`}
                      onClick={() => detachLabel.mutate(label.id)}
                    >
                      Remove
                    </button>
                  </li>
                ))}
              </ul>
              {availableLabels.length > 0 && (
                <div className="field">
                  <label htmlFor={`${formId}-add-label`}>Add a label</label>
                  <select
                    id={`${formId}-add-label`}
                    value=""
                    onChange={(e) =>
                      e.target.value && attachLabel.mutate(e.target.value)
                    }
                  >
                    <option value="">Choose…</option>
                    {availableLabels.map((label) => (
                      <option key={label.id} value={label.id}>
                        {label.name}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </section>

            <section
              className="task-section"
              aria-labelledby={`${formId}-comments-h`}
            >
              <h2 id={`${formId}-comments-h`}>Comments</h2>
              {commentsQuery.isPending && (
                <p role="status">Loading comments…</p>
              )}
              {commentsQuery.isError && (
                <p className="form-error" role="alert">
                  {describeError(
                    commentsQuery.error,
                    "Could not load comments.",
                  )}
                </p>
              )}
              {deleteComment.isError && (
                <p className="form-error" role="alert">
                  {describeError(
                    deleteComment.error,
                    "Could not delete the comment.",
                  )}
                </p>
              )}
              {commentsQuery.isSuccess && comments.length === 0 && (
                <p>No comments yet.</p>
              )}
              <ul className="comment-list">
                {comments.map((comment) => (
                  <li key={comment.id}>
                    <p className="comment-list__meta">
                      <strong>{comment.authorDisplayName}</strong> ·{" "}
                      {new Date(comment.createdAt).toLocaleString()}
                    </p>
                    <p className="comment-list__body">{comment.body}</p>
                    {(comment.authorId === user?.id || canManageProjects) && (
                      <button
                        type="button"
                        className="button-secondary"
                        aria-label={`Delete comment by ${comment.authorDisplayName}`}
                        onClick={() => deleteComment.mutate(comment.id)}
                      >
                        Delete
                      </button>
                    )}
                  </li>
                ))}
              </ul>
              <form
                className="inline-form inline-form--wide"
                onSubmit={(event: FormEvent) => {
                  event.preventDefault();
                  addComment.mutate();
                }}
              >
                {addComment.isError && (
                  <p className="form-error" role="alert">
                    {describeError(
                      addComment.error,
                      "Could not post the comment.",
                    )}
                  </p>
                )}
                <div className="field">
                  <label htmlFor={`${formId}-comment`}>Add a comment</label>
                  <textarea
                    id={`${formId}-comment`}
                    value={commentBody}
                    rows={3}
                    onChange={(e) => setCommentBody(e.target.value)}
                    required
                  />
                </div>
                <button
                  type="submit"
                  className="button-primary"
                  disabled={addComment.isPending || commentBody.trim() === ""}
                >
                  {addComment.isPending ? "Posting…" : "Post comment"}
                </button>
              </form>
            </section>

            <section
              className="task-section"
              aria-labelledby={`${formId}-danger-h`}
            >
              <h2 id={`${formId}-danger-h`}>Delete task</h2>
              {deleteTask.isError && (
                <p className="form-error" role="alert">
                  {describeError(
                    deleteTask.error,
                    "Could not delete the task.",
                  )}
                </p>
              )}
              {confirmingDelete ? (
                <div className="button-group">
                  <button
                    type="button"
                    className="button-primary"
                    disabled={deleteTask.isPending}
                    onClick={() => deleteTask.mutate()}
                  >
                    {deleteTask.isPending
                      ? "Deleting…"
                      : "Yes, delete this task"}
                  </button>
                  <button
                    type="button"
                    className="button-secondary"
                    onClick={() => setConfirmingDelete(false)}
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  className="button-secondary"
                  onClick={() => setConfirmingDelete(true)}
                >
                  Delete task
                </button>
              )}
            </section>
          </>
        )}
      </main>
    </>
  );
}

function LabelChip({ label }: { label: LabelResponse }) {
  return (
    <span className="label-chip" style={{ borderColor: label.color }}>
      <span
        className="label-chip__swatch"
        style={{ background: label.color }}
        aria-hidden="true"
      />
      {label.name}
    </span>
  );
}

/** 409 means someone else saved first (optimistic locking) - say so in plain words. */
function describeConflict(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.status === 409) {
    return "This task was changed by someone else since you opened it. Reload it to see the latest version.";
  }
  return describeError(error, fallback);
}

function describeError(error: unknown, fallback: string): string {
  if (error instanceof ApiError || error instanceof NetworkError) {
    return error.message;
  }
  return fallback;
}
