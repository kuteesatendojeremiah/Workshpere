import { useId, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, NetworkError } from '../api/client'
import { filesApi } from '../api/filesApi'
import type { FileResponse } from '../api/types'

const MAX_BYTES = 10 * 1024 * 1024

function describeError(error: unknown, fallback: string): string {
  return error instanceof ApiError || error instanceof NetworkError ? error.message : fallback
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** Files attached to a task: list, upload, download and delete, with loading / empty / error states. */
export function AttachmentsSection({ taskId }: { taskId: string }) {
  const headingId = useId()
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)
  const [localError, setLocalError] = useState<string | null>(null)
  const key = ['tasks', taskId, 'attachments']

  const filesQuery = useQuery({ queryKey: key, queryFn: () => filesApi.list(taskId) })

  const upload = useMutation({
    mutationFn: (file: File) => filesApi.upload(taskId, file),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
    onSettled: () => {
      if (inputRef.current) inputRef.current.value = ''
    },
  })
  const remove = useMutation({
    mutationFn: (fileId: string) => filesApi.delete(fileId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
  })
  const download = useMutation({
    mutationFn: async (file: FileResponse) => {
      const blob = await filesApi.download(file.id)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = file.filename
      link.click()
      URL.revokeObjectURL(url)
    },
  })

  function onFileChosen(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    setLocalError(null)
    if (!file) return
    if (file.size > MAX_BYTES) {
      setLocalError('That file is larger than the 10 MB limit.')
      event.target.value = ''
      return
    }
    upload.mutate(file)
  }

  const files = filesQuery.data?.content ?? []
  const actionError = upload.error ?? remove.error ?? download.error

  return (
    <section className="task-section" aria-labelledby={headingId}>
      <h2 id={headingId}>Attachments</h2>

      <div className="form-field">
        <label htmlFor={`${headingId}-file`}>Add a file (PNG, JPEG, GIF, PDF or text, up to 10 MB)</label>
        <input
          id={`${headingId}-file`}
          ref={inputRef}
          type="file"
          accept=".png,.jpg,.jpeg,.gif,.pdf,.txt,.md,.csv,.log"
          disabled={upload.isPending}
          onChange={onFileChosen}
        />
      </div>

      {upload.isPending && <p role="status">Uploading…</p>}
      {(localError || actionError) && (
        <p className="form-error" role="alert">
          {localError ?? describeError(actionError, 'Something went wrong with that file.')}
        </p>
      )}

      {filesQuery.isPending && <p role="status">Loading attachments…</p>}
      {filesQuery.isError && (
        <p className="form-error" role="alert">
          {describeError(filesQuery.error, 'Could not load the attachments.')}
        </p>
      )}
      {filesQuery.isSuccess && files.length === 0 && <p>No attachments yet.</p>}
      {files.length > 0 && (
        <ul className="attachment-list">
          {files.map((file) => (
            <li key={file.id}>
              <span>
                {file.filename} <small>({formatSize(file.sizeBytes)})</small>
              </span>{' '}
              <button type="button" className="button-secondary" onClick={() => download.mutate(file)}>
                Download {file.filename}
              </button>{' '}
              <button
                type="button"
                className="button-secondary"
                disabled={remove.isPending}
                onClick={() => remove.mutate(file.id)}
              >
                Delete {file.filename}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
