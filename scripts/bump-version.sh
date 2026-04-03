#!/bin/bash
# ─────────────────────────────────────────────────────────────
# bump-version.sh — Met à jour la version dans tous les fichiers
#                   du projet, commit, et crée le tag Git.
#
# Usage :
#   ./scripts/bump-version.sh 1.2.0
#
# Ce script doit être exécuté depuis la racine du projet,
# sur la branche main, avec un working tree propre.
# ─────────────────────────────────────────────────────────────

set -e

# ── Validation de l'argument ──────────────────────────────────
VERSION=$1

if [ -z "$VERSION" ]; then
  echo "Erreur : version requise."
  echo "Usage : ./scripts/bump-version.sh 1.2.0"
  exit 1
fi

# Vérifie le format SemVer (X.Y.Z)
if ! echo "$VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "Erreur : format invalide '$VERSION'. Attendu : X.Y.Z (ex: 1.2.0)"
  exit 1
fi

TAG="v$VERSION"

# ── Vérifie qu'on est sur main et que le repo est propre ──────
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "main" ]; then
  echo "Erreur : tu dois être sur la branche main (branche actuelle : $BRANCH)"
  exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Erreur : le working tree n'est pas propre. Commit ou stash tes changements."
  exit 1
fi

# Vérifie que le tag n'existe pas déjà
if git tag | grep -q "^$TAG$"; then
  echo "Erreur : le tag $TAG existe déjà."
  exit 1
fi

echo "──────────────────────────────────────────"
echo "  Bump version → $VERSION  (tag: $TAG)"
echo "──────────────────────────────────────────"

# ── 1. Frontend — package.json ────────────────────────────────
echo "→ package.json (frontend)..."
npm --prefix ldc_frontend version "$VERSION" --no-git-tag-version --allow-same-version

# ── 2. Backend — pom.xml ─────────────────────────────────────
echo "→ pom.xml (backend)..."
mvn -f ldc_backend/pom.xml \
  versions:set \
  -DnewVersion="$VERSION" \
  -DgenerateBackupPoms=false \
  --no-transfer-progress \
  --batch-mode

# ── 3. Commit + Tag ───────────────────────────────────────────
echo "→ Commit + tag Git..."
git add ldc_frontend/package.json ldc_frontend/package-lock.json ldc_backend/pom.xml
git commit -m "chore: release $TAG"
git tag -a "$TAG" -m "Release $TAG"

# ── 4. Push ───────────────────────────────────────────────────
echo "→ Push vers origin..."
git push origin main
git push origin "$TAG"

echo ""
echo "✓ Version $TAG publiée."
echo "  GitHub Actions va déclencher le pipeline de production."
echo "  Surveille : https://github.com/$(git remote get-url origin | sed 's/.*github.com[:/]//' | sed 's/.git$//')/actions"
