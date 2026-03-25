#!/bin/bash
# .claude-watch.sh — DEBUG MODE

WATCH_DIR="/c/DEV_APP_LAB/DEPLOY/app_v3_claude_production"
HASH_STATE="/tmp/.claude_watch_hashes"
PROCESSED="/tmp/.claude_watch_processed"

# Reset état au démarrage
> "$HASH_STATE"
> "$PROCESSED"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Claude Watcher — DEBUG MODE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Test 1 : est-ce que find trouve des fichiers ?
echo "[DEBUG] Scan initial des fichiers..."
file_count=$(find "$WATCH_DIR" -type f \
  \( -name "*.ts" -o -name "*.java" -o -name "*.html" -o -name "*.yml" -o -name "*.sh" -o -name "*.css" \) \
  ! -path "*/.git/*" ! -path "*/node_modules/*" ! -path "*/target/*" ! -path "*/dist/*" \
  2>/dev/null | wc -l)
echo "[DEBUG] Fichiers trouvés : $file_count"

# Test 2 : est-ce que md5sum fonctionne ?
test_file=$(find "$WATCH_DIR" -type f -name "*.ts" ! -path "*/node_modules/*" 2>/dev/null | head -1)
if [ -n "$test_file" ]; then
  test_hash=$(md5sum "$test_file" 2>/dev/null | cut -d' ' -f1)
  echo "[DEBUG] md5sum test sur : $test_file"
  echo "[DEBUG] Hash obtenu : $test_hash"
else
  echo "[DEBUG] AUCUN fichier .ts trouvé"
fi

echo ""
echo "[DEBUG] Initialisation des hashes (première passe)..."

# Première passe : enregistrer tous les hashes
find "$WATCH_DIR" -type f \
  \( -name "*.ts" -o -name "*.java" -o -name "*.html" -o -name "*.yml" -o -name "*.sh" -o -name "*.css" \) \
  ! -path "*/.git/*" ! -path "*/node_modules/*" ! -path "*/target/*" ! -path "*/dist/*" \
  2>/dev/null | while IFS= read -r file; do
    h=$(md5sum "$file" 2>/dev/null | cut -d' ' -f1)
    [ -n "$h" ] && echo "$h $file" >> "$HASH_STATE"
done

echo "[DEBUG] Hashes initiaux stockés : $(wc -l < "$HASH_STATE")"
echo ""
echo "[DEBUG] En attente de modifications... (modifie et sauvegarde un fichier)"
echo ""

LOOP=0
while true; do
  LOOP=$((LOOP + 1))

  find "$WATCH_DIR" -type f \
    \( -name "*.ts" -o -name "*.java" -o -name "*.html" -o -name "*.yml" -o -name "*.sh" -o -name "*.css" \) \
    ! -path "*/.git/*" ! -path "*/node_modules/*" ! -path "*/target/*" ! -path "*/dist/*" \
    2>/dev/null | while IFS= read -r file; do

    current_hash=$(md5sum "$file" 2>/dev/null | cut -d' ' -f1)
    [ -z "$current_hash" ] && continue

    stored_hash=$(grep -F " $file" "$HASH_STATE" 2>/dev/null | tail -1 | cut -d' ' -f1)

    if [ "$current_hash" != "$stored_hash" ]; then
      echo "[DEBUG] CHANGEMENT DETECTE : $file"
      echo "[DEBUG]   ancien hash : $stored_hash"
      echo "[DEBUG]   nouveau hash : $current_hash"

      # Mettre à jour le hash
      grep -vF " $file" "$HASH_STATE" > /tmp/.claude_hash_tmp 2>/dev/null || true
      echo "$current_hash $file" >> /tmp/.claude_hash_tmp
      mv /tmp/.claude_hash_tmp "$HASH_STATE"

      # Chercher //claude:
      hits=$(grep -n "//claude:" "$file" 2>/dev/null || true)
      if [ -n "$hits" ]; then
        echo "[DEBUG]   //claude: trouvé !"
        echo "$hits"

        while IFS= read -r match; do
          lineno="${match%%:*}"
          raw="${match#*:}"
          instruction=$(echo "$raw" | sed 's|.*//claude:||' | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//')
          [ -z "$instruction" ] && continue

          fingerprint="${file}:${lineno}:${instruction}"
          if ! grep -qF "$fingerprint" "$PROCESSED" 2>/dev/null; then
            echo "$fingerprint" >> "$PROCESSED"

            echo ""
            echo "┌──────────────────────────────────────────────────┐"
            echo "│  //claude: INSTRUCTION DÉTECTÉE                  │"
            echo "├──────────────────────────────────────────────────┤"
            printf "│  Fichier     : %s\n" "$file"
            printf "│  Ligne       : %s\n" "$lineno"
            printf "│  Instruction : \033[1;33m%s\033[0m\n" "$instruction"
            echo "└──────────────────────────────────────────────────┘"
            echo ""
            echo "▶ Envoi à Claude..."

            claude -p \
"Projet : $WATCH_DIR
Fichier : $file (ligne $lineno)
Instruction : $instruction

Implémente cette instruction dans le contexte du projet. Lis les fichiers nécessaires avant de modifier quoi que ce soit." \
              --dangerously-skip-permissions

            echo "✓ Terminé — $(date +%H:%M:%S)"
            echo ""
          fi
        done <<< "$hits"
      else
        echo "[DEBUG]   pas de //claude: dans ce fichier"
      fi
    fi

  done

  # Heartbeat toutes les 10 secondes
  if [ $((LOOP % 10)) -eq 0 ]; then
    echo "[DEBUG] loop #$LOOP — $(date +%H:%M:%S) — en attente..."
  fi

  sleep 1
done
