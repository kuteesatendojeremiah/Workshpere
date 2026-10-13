import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TaskDetailsPage } from "./TaskDetailsPage";
import { ApiError } from "../api/client";
import type {
  CommentResponse,
  LabelResponse,
  TaskResponse,
} from "../api/types";

vi.mock("../api/tasksApi", () => ({
  tasksApi: { get: vi.fn(), update: vi.fn(), assign: vi.fn(), delete: vi.fn() },
}));
vi.mock("../api/commentsApi", () => ({
  commentsApi: { list: vi.fn(), create: vi.fn(), delete: vi.fn() },
}));
vi.mock("../api/labelsApi", () => ({
  labelsApi: {
    listForProject: vi.fn(),
    listForTask: vi.fn(),
    attach: vi.fn(),
    detach: vi.fn(),
  },
}));
vi.mock("../api/projectsApi", () => ({
  projectsApi: { listMembers: vi.fn() },
}));
vi.mock("../api/organizationsApi", () => ({
  organizationsApi: { listMembers: vi.fn() },
}));
vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ user: { id: "me", displayName: "Me" }, logout: vi.fn() }),
}));

const { tasksApi } = await import("../api/tasksApi");
const { commentsApi } = await import("../api/commentsApi");
const { labelsApi } = await import("../api/labelsApi");
const { projectsApi } = await import("../api/projectsApi");
const { organizationsApi } = await import("../api/organizationsApi");

function page<T>(content: T[]) {
  return {
    content,
    page: 0,
    size: 50,
    totalElements: content.length,
    totalPages: 1,
  };
}

function task(overrides: Partial<TaskResponse> = {}): TaskResponse {
  return {
    id: "task-1",
    projectId: "proj-1",
    title: "Do the thing",
    description: "Some words",
    status: "TODO",
    position: 0,
    priority: "MEDIUM",
    assigneeId: null,
    dueDate: null,
    version: 3,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function comment(overrides: Partial<CommentResponse>): CommentResponse {
  return {
    id: "c1",
    taskId: "task-1",
    authorId: "me",
    authorDisplayName: "Me",
    body: "First comment",
    createdAt: "2026-01-02T00:00:00Z",
    ...overrides,
  };
}

const bug: LabelResponse = {
  id: "l1",
  projectId: "proj-1",
  name: "Bug",
  color: "#ff0000",
};
const design: LabelResponse = {
  id: "l2",
  projectId: "proj-1",
  name: "Design",
  color: "#00ff00",
};

function member(
  userId: string,
  displayName: string,
  role = "ORGANIZATION_OWNER",
) {
  return {
    userId,
    email: `${userId}@example.com`,
    displayName,
    role,
    joinedAt: "2026-01-01T00:00:00Z",
  };
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter
        initialEntries={["/organizations/org-1/projects/proj-1/tasks/task-1"]}
      >
        <Routes>
          <Route
            path="/organizations/:organizationId/projects/:projectId/tasks/:taskId"
            element={<TaskDetailsPage />}
          />
          <Route
            path="/organizations/:organizationId/projects/:projectId/board"
            element={<p>Board page</p>}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("TaskDetailsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(organizationsApi.listMembers).mockResolvedValue(
      page([member("me", "Me")]),
    );
    vi.mocked(projectsApi.listMembers).mockResolvedValue(
      page([
        {
          userId: "me",
          email: "me@example.com",
          displayName: "Me",
          joinedAt: "2026-01-01T00:00:00Z",
        },
        {
          userId: "bob",
          email: "bob@example.com",
          displayName: "Bob",
          joinedAt: "2026-01-01T00:00:00Z",
        },
      ]),
    );
    vi.mocked(tasksApi.get).mockResolvedValue(task());
    vi.mocked(commentsApi.list).mockResolvedValue(page([comment({})]));
    vi.mocked(labelsApi.listForTask).mockResolvedValue([bug]);
    vi.mocked(labelsApi.listForProject).mockResolvedValue([bug, design]);
  });

  it("shows a loading state, then the task details", async () => {
    renderPage();

    expect(screen.getByText("Loading task…")).toBeInTheDocument();
    expect(
      await screen.findByRole("heading", { name: "Do the thing" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Description")).toHaveValue("Some words");
    expect(screen.getByText("First comment")).toBeInTheDocument();
  });

  it("shows an error when the task cannot be loaded", async () => {
    vi.mocked(tasksApi.get).mockRejectedValue(
      new ApiError(404, {
        code: "TASK_NOT_FOUND",
        message: "Task not found",
        details: [],
        timestamp: "2026-01-01T00:00:00Z",
        path: "/api/v1/tasks/task-1",
      }),
    );

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Task not found",
    );
  });

  it("saves edits, sending the current version for optimistic locking", async () => {
    vi.mocked(tasksApi.update).mockResolvedValue(
      task({ title: "Renamed", version: 4 }),
    );

    renderPage();
    const user = userEvent.setup();

    const titleInput = await screen.findByLabelText("Title");
    await user.clear(titleInput);
    await user.type(titleInput, "Renamed");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(tasksApi.update).toHaveBeenCalledWith("task-1", {
        title: "Renamed",
        description: "Some words",
        priority: "MEDIUM",
        dueDate: null,
        version: 3,
      }),
    );
    expect(await screen.findByText("Saved.")).toBeInTheDocument();
  });

  it("explains a version conflict and offers to reload", async () => {
    vi.mocked(tasksApi.update).mockRejectedValue(
      new ApiError(409, {
        code: "CONCURRENT_MODIFICATION",
        message: "Version mismatch",
        details: [],
        timestamp: "2026-01-01T00:00:00Z",
        path: "/api/v1/tasks/task-1",
      }),
    );

    renderPage();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Save changes" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "changed by someone else",
    );
    expect(
      screen.getByRole("button", { name: "Reload task" }),
    ).toBeInTheDocument();
  });

  it("assigns the task to a project member", async () => {
    vi.mocked(tasksApi.assign).mockResolvedValue(
      task({ assigneeId: "bob", version: 4 }),
    );

    renderPage();
    const user = userEvent.setup();

    await user.selectOptions(
      await screen.findByLabelText("Assigned to"),
      "bob",
    );

    await waitFor(() =>
      expect(tasksApi.assign).toHaveBeenCalledWith("task-1", "bob", 3),
    );
  });

  it("adds a comment and clears the box", async () => {
    vi.mocked(commentsApi.create).mockResolvedValue(
      comment({ id: "c2", body: "Hello" }),
    );

    renderPage();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("Add a comment"), "Hello");
    await user.click(screen.getByRole("button", { name: "Post comment" }));

    await waitFor(() =>
      expect(commentsApi.create).toHaveBeenCalledWith("task-1", "Hello"),
    );
    await waitFor(() =>
      expect(screen.getByLabelText("Add a comment")).toHaveValue(""),
    );
  });

  it("shows an empty state when there are no comments", async () => {
    vi.mocked(commentsApi.list).mockResolvedValue(page([]));

    renderPage();

    expect(await screen.findByText("No comments yet.")).toBeInTheDocument();
  });

  it("lets an author delete their own comment", async () => {
    vi.mocked(commentsApi.delete).mockResolvedValue(undefined);

    renderPage();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Delete comment by Me" }),
    );

    await waitFor(() =>
      expect(commentsApi.delete).toHaveBeenCalledWith("task-1", "c1"),
    );
  });

  it("hides the delete button on someone else's comment for a regular member", async () => {
    vi.mocked(organizationsApi.listMembers).mockResolvedValue(
      page([member("me", "Me", "EMPLOYEE_MEMBER")]),
    );
    vi.mocked(commentsApi.list).mockResolvedValue(
      page([
        comment({
          id: "c9",
          authorId: "bob",
          authorDisplayName: "Bob",
          body: "From Bob",
        }),
      ]),
    );

    renderPage();

    await screen.findByText("From Bob");
    expect(
      screen.queryByRole("button", { name: /Delete comment/ }),
    ).not.toBeInTheDocument();
  });

  it("attaches an available label and removes an attached one", async () => {
    vi.mocked(labelsApi.attach).mockResolvedValue(design);
    vi.mocked(labelsApi.detach).mockResolvedValue(undefined);

    renderPage();
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText("Add a label"), "l2");
    await waitFor(() =>
      expect(labelsApi.attach).toHaveBeenCalledWith("task-1", "l2"),
    );

    await user.click(screen.getByRole("button", { name: "Remove label Bug" }));
    await waitFor(() =>
      expect(labelsApi.detach).toHaveBeenCalledWith("task-1", "l1"),
    );
  });

  it("asks for confirmation before deleting, then returns to the board", async () => {
    vi.mocked(tasksApi.delete).mockResolvedValue(undefined);

    renderPage();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Delete task" }),
    );
    expect(tasksApi.delete).not.toHaveBeenCalled();

    await user.click(
      screen.getByRole("button", { name: "Yes, delete this task" }),
    );

    await waitFor(() => expect(tasksApi.delete).toHaveBeenCalledWith("task-1"));
    expect(await screen.findByText("Board page")).toBeInTheDocument();
  });
});
