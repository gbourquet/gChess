#!/usr/bin/env bash
# ============================================================
# Émission du premier certificat Let's Encrypt
# ============================================================
# À lancer UNE SEULE FOIS sur le serveur, depuis /opt/gchess.
# Les renouvellements suivants sont automatiques (service certbot).
#
# Problème de l'œuf et de la poule : nginx refuse de démarrer si
# ssl_certificate pointe sur un fichier absent, mais certbot a
# besoin de nginx pour répondre au challenge HTTP-01. On pose donc
# un certificat auto-signé jetable, on démarre nginx, puis on le
# remplace par le vrai.
# ============================================================
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "Erreur : .env introuvable. Copiez .env.example en .env et complétez-le." >&2
  exit 1
fi

# shellcheck disable=SC1091
source .env

: "${DOMAIN:?DOMAIN doit être défini dans .env}"
: "${LETSENCRYPT_EMAIL:?LETSENCRYPT_EMAIL doit être défini dans .env}"

CERT_PATH="/etc/letsencrypt/live/${DOMAIN}"

echo "==> Domaine : ${DOMAIN}"

if docker compose run --rm --entrypoint sh certbot -c "[ -s ${CERT_PATH}/privkey.pem ]" 2>/dev/null; then
  echo "==> Un certificat existe déjà pour ${DOMAIN}. Rien à faire."
  echo "    (pour repartir de zéro : docker compose run --rm --entrypoint sh certbot -c 'rm -rf ${CERT_PATH}')"
  exit 0
fi

echo "==> Génération d'un certificat auto-signé temporaire"
docker compose run --rm --entrypoint sh certbot -c "\
  mkdir -p ${CERT_PATH} && \
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout ${CERT_PATH}/privkey.pem \
    -out ${CERT_PATH}/fullchain.pem \
    -subj '/CN=${DOMAIN}'"

echo "==> Démarrage de nginx"
docker compose up -d nginx

# Laisse à nginx le temps d'ouvrir le port 80 avant le challenge.
sleep 5

echo "==> Suppression du certificat temporaire"
docker compose run --rm --entrypoint sh certbot -c "rm -rf ${CERT_PATH} /etc/letsencrypt/archive/${DOMAIN} /etc/letsencrypt/renewal/${DOMAIN}.conf"

echo "==> Demande du certificat Let's Encrypt"
docker compose run --rm --entrypoint certbot certbot \
  certonly --webroot -w /var/www/certbot \
  --email "${LETSENCRYPT_EMAIL}" \
  --agree-tos --no-eff-email \
  --non-interactive \
  -d "${DOMAIN}"

echo "==> Rechargement de nginx"
docker compose exec nginx nginx -s reload

echo
echo "✅ Certificat en place pour ${DOMAIN}"
echo "   Vérification : curl -I https://${DOMAIN}/health"
