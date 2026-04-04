#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# push-develop.sh — Stage, commit et push sur develop
#
# Usage :
#   ./scripts/push-develop.sh "message de commit"
# ─────────────────────────────────────────────────────────────
set -e

MESSAGE="${1}"

if [ -z "$MESSAGE" ]; then
  echo "Usage : ./scripts/push-develop.sh \"message de commit\""
  exit 1
fi

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "develop" ]; then
  echo "Erreur : tu es sur la branche '$BRANCH'. Passe sur develop d'abord."
  echo "  git checkout develop"
  exit 1
fi

git add .
git commit -m "$MESSAGE"
git push origin develop

echo "✓ Poussé sur develop : $MESSAGE"
