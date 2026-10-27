#!/usr/bin/env bash
# Turns on branch protection for main, so changes can only arrive through a reviewed pull request whose
# CI checks passed (spec section 27: "protected main branch", "pull requests required").
#
# This changes settings on GitHub, so it is deliberately a script you run yourself, once:
#     gh auth login            # needs admin rights on the repository
#     ./scripts/protect-main.sh [owner/repo]
#
# After this, `git push origin main` is rejected; work on a branch and open a pull request.
set -euo pipefail

REPO="${1:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
echo "Protecting branch main of ${REPO}"

gh api --method PUT "repos/${REPO}/branches/main/protection" --input - <<JSON
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["backend", "frontend", "images", "secrets-scan", "dependency-review"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": true
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON

echo "Done. Required checks: backend, frontend, images, secrets-scan, dependency-review."
