#!/bin/bash
set -euo pipefail

docker network create shared_db_net 2>/dev/null && echo "Réseau shared_db_net créé" || echo "Réseau shared_db_net déjà existant"

docker network connect shared_db_net pgadmin 2>/dev/null && echo "pgadmin connecté" || echo "pgadmin déjà connecté"
docker network connect shared_db_net lab_db  2>/dev/null && echo "lab_db connecté"  || echo "lab_db déjà connecté"

echo ""
echo "Conteneurs sur shared_db_net :"
docker network inspect shared_db_net --format '{{range .Containers}}  - {{.Name}}{{println}}{{end}}'
