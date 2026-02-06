#!/bin/bash

# verify-pr.sh
# Usage: ./verify-pr.sh <PR_ID>

PR_ID=$1

if [ -z "$PR_ID" ]; then
  echo "Usage: ./verify-pr.sh <PR_ID>"
  exit 1
fi

# Ensure we are in the root of the repo (simple check for gradlew)
if [ ! -f "./gradlew" ]; then
    echo "Error: ./gradlew not found. Please run this from the root of the project."
    exit 1
fi

# Stash any local changes first to avoid conflicts
if [[ `git status --porcelain` ]]; then
  echo "📦 Stashing local changes..."
  git stash save "Auto-stash before verifying PR #$PR_ID"
fi

# Check if gh is installed
if command -v gh &> /dev/null; then
    echo "⬇️  Checking out PR #$PR_ID via GitHub CLI..."
    gh pr checkout $PR_ID
else
    echo "⚠️  GitHub CLI (gh) not found. Attempting to fetch via git..."
    # Fallback attempt (works for Dependabot if checking out from known remote, but 'gh' is safer)
    # Determine remote name (prefer 'github' if it exists, else 'origin')
    REMOTE_NAME="origin"
    if git remote | grep -q "github"; then
        REMOTE_NAME="github"
    fi
    
    echo "⬇️  Fetching from remote: $REMOTE_NAME..."
    git fetch $REMOTE_NAME pull/$PR_ID/head:pr-$PR_ID
    git checkout pr-$PR_ID
fi

if [ $? -ne 0 ]; then
    echo "❌ Failed to checkout PR. Please check the ID or install gh cli."
    exit 1
fi

echo "🧪 Running tests..."
./gradlew testDebugUnitTest

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo ""
    echo "✅ TESTS PASSED"
    echo "---------------------------------------------------"
    echo "You are currently on the PR branch."
    echo "To merge: gh pr merge $PR_ID --auto --squash  (if you have gh)"
    echo "To return to main: git checkout main"
else
    echo ""
    echo "❌ TESTS FAILED"
fi

exit $EXIT_CODE
