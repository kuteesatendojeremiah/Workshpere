import { expect, test, type Locator, type Page } from '@playwright/test'

/** Spec 24 E2E: register -> log in -> create org -> create project -> create task -> complete it. */

// dnd-kit's PointerSensor needs real pointer events and a few intermediate moves to start a drag.
async function dragCardToColumn(page: Page, card: Locator, columnName: string) {
  const target = page.getByRole('region', { name: columnName }).locator('.board-column__dropzone')
  const from = (await card.boundingBox())!
  const to = (await target.boundingBox())!
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2)
  await page.mouse.down()
  await page.mouse.move(from.x + from.width / 2 + 10, from.y + from.height / 2 + 10, { steps: 5 })
  await page.mouse.move(to.x + to.width / 2, to.y + 40, { steps: 20 })
  await page.mouse.up()
}

test('a new user can register, log in, create a project and task, and complete it', async ({ page }) => {
  const unique = Date.now()
  const email = `e2e-${unique}@example.com`
  const password = 'correct-horse-battery'

  // Register
  await page.goto('/register')
  await page.getByLabel('Name').fill('E2E User')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await page.getByRole('link', { name: 'Go to login' }).click()

  // Log in
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()

  // Organization
  await page.getByRole('button', { name: '+ New organization' }).click()
  await page.getByLabel('Organization name').fill(`E2E Org ${unique}`)
  await page.getByRole('button', { name: 'Create', exact: true }).click()
  await expect(page).toHaveURL(/\/organizations\/[0-9a-f-]+$/)

  // Project
  await page.getByRole('link', { name: 'View all' }).click()
  await page.getByRole('button', { name: '+ New project' }).click()
  await page.getByLabel('Project name').fill('E2E Project')
  await page.getByRole('button', { name: 'Create', exact: true }).click()
  await page.getByRole('link', { name: 'E2E Project' }).click()
  await page.getByRole('link', { name: 'View board' }).click()

  // Task
  await page.getByLabel('New task').fill('Ship it')
  await page.getByRole('button', { name: 'Add task' }).click()
  const card = page.locator('.task-card', { hasText: 'Ship it' })
  await expect(page.getByRole('region', { name: 'To Do' }).getByText('Ship it')).toBeVisible()

  // Complete it: the board only allows one column forward at a time.
  for (const column of ['In Progress', 'In Review', 'Done']) {
    await dragCardToColumn(page, card, column)
    await expect(page.getByRole('region', { name: column }).getByText('Ship it')).toBeVisible()
  }

  // It persisted on the server, not just in React state.
  await page.reload()
  await expect(page.getByRole('region', { name: 'Done' }).getByText('Ship it')).toBeVisible()
  await expect(page.getByRole('region', { name: 'To Do' }).getByText('Ship it')).toHaveCount(0)
})
