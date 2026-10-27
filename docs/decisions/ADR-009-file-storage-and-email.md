# ADR-009: File attachments in object storage, and real email

## Context
Spec section 18 requires file metadata in PostgreSQL, bytes in object storage, validation of size,
type and content, no trust in filename extensions, authorization before serving private files, and
audit records for uploads and deletes. Steps 4, 5 and 6 left invitation / verification / reset tokens
"logged instead of sent" until email existed.

## Decision
**Files**
- Table `file_attachments` (V34) holds metadata; bytes go to an S3-compatible store through the
  `ObjectStorage` interface (`MinioObjectStorage`, MinIO client). The object key is
  `{organizationId}/{projectId}/{random UUID}`. The user's filename is stored as metadata only.
- Upload checks, in order: caller can see the task's project; not empty and at most 10 MB; the type
  is detected from the file's first bytes against an allow-list (PNG, JPEG, GIF, PDF, plain text); the
  filename extension and the declared Content-Type must both agree with the detected type. The
  detected type is what we store and serve. Anything else is rejected (415).
- Download streams through the API after checking access to the task on EVERY request, with
  `Content-Disposition: attachment`, the detected type, and `X-Content-Type-Options: nosniff`.
  A file the caller may not see answers 404 (indistinguishable from a missing one).
- Delete: the uploader or anyone with `MANAGE_PROJECTS`. Deleting a task deletes its attachments
  (`TaskDeletedEvent`, so the task module does not depend on the file module). Objects are removed
  after the database transaction commits; if that fails the object is orphaned and logged, never the
  reverse (a row pointing at nothing).
- Uploads and deletes are audited (`FILE_UPLOADED`, `FILE_DELETED`).

**Email**
- `EmailService` sends the three account emails over SMTP. Mail is sent after the transaction commits,
  on a separate thread, and a failure is logged without failing the request.
- Links point at the frontend (`app.mail.frontend-url`) with the raw token in the query string. The
  database stores only token hashes.
- `app.mail.log-tokens` (default off) also writes the token to the log, only for local development
  and the automated tests.

## Alternatives
- Presigned storage URLs for download: offloads bandwidth, but a leaked URL keeps working after the
  user loses access. Streaming through the API keeps authorization simple; revisit if download volume
  becomes a problem (it would also need short expiry).
- Apache Tika for type detection: more formats, but a large dependency and parser attack surface. A
  small allow-list of magic numbers is easier to reason about and test.
- Virus scanning (ClamAV): recommended before accepting arbitrary document types in production; out
  of scope here. The allow-list and forced-download headers limit the exposure.

## Consequences
- Uploads are read fully into memory (max 10 MB) so they can be inspected; fine for this limit.
- Email delivery is best effort. There is no outbox or retry for mail yet; a "resend" action is the
  recovery path.
- The MinIO image used for local development and tests (`bitnamilegacy/minio`) is unmaintained
  because MinIO stopped publishing images. Production should use a managed S3 service; only
  `app.storage.*` changes.

## Security impact
Content sniffing allow-list, extension/type agreement, size limit, generated storage keys (no path
traversal), per-request authorization, nosniff + forced download, tenant isolation tested
(`FileControllerIntegrationTest`), audit trail, no credentials in links other than single-use tokens.

## Operational impact
New services: object storage and SMTP (`docker-compose.yml`: `minio`, `mailpit`). New settings:
`STORAGE_*`, `MAIL_*`, `FRONTEND_URL`. If storage is down uploads/downloads return 503
`STORAGE_UNAVAILABLE` and everything else keeps working.
