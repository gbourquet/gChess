# Déploiement gChess

Comment déployer gChess sur votre propre serveur, en auto-hébergement.

Les exemples utilisent `chess.example.com` comme domaine et `203.0.113.42`
comme adresse IP : ce sont des valeurs de documentation réservées, à remplacer
partout par les vôtres.

## Architecture

Un **nginx système** détient les ports 80 et 443, termine le TLS et proxifie
vers la stack. Celle-ci n'est publiée que sur la boucle locale et gère son
propre routage applicatif.

```
                 ┌───────────────────── serveur ─────────────────────┐
 Internet        │                                                   │
    │            │  nginx SYSTÈME ──┬─→ vos autres sites (inchangés) │
    └─── 443 ────┼─→ TLS, certbot   │                                │
                 │                  └─→ 127.0.0.1:8080               │
                 │                        │                          │
                 │                        └─→ nginx CONTENEUR        │
                 │                             ├─→ front  (Angular)  │
                 │                             ├─→ back   (Ktor)     │
                 │                             └─→ postgres          │
                 └───────────────────────────────────────────────────┘
```

| Chemin | Destination |
|---|---|
| `/` | front Angular |
| `/api/…` | back Ktor |
| `/ws/…` | back Ktor (WebSocket) |
| `/health`, `/ready`, `/alive` | back Ktor (sondes) |

Front et API partagent la même origine : aucun préflight CORS, un seul
certificat, et les WebSockets passent en `wss://` sans configuration
supplémentaire.

**Pourquoi deux nginx.** Celui de l'hôte ne connaît qu'une destination et son
fichier de conf, une dizaine de lignes, n'a jamais à évoluer. Tout le routage
applicatif reste en aval, versionné dans `deploy/nginx/` et redéployé par la
CI. Le coût est un saut de proxy supplémentaire sur la boucle locale,
négligeable. Cette organisation permet aussi de cohabiter avec d'autres sites
déjà hébergés sur la même machine.

Si votre serveur est vierge, installez quand même nginx et certbot sur l'hôte :
c'est le seul chemin documenté ici, et il reste valable si vous ajoutez
d'autres sites plus tard.

**Rien n'est joignable depuis l'extérieur** hormis le nginx système : la stack
est publiée sur `127.0.0.1` uniquement. Ce bind explicite compte, car Docker
insère ses règles directement dans iptables, en contournant le pare-feu — sans
lui, un `ports: 8080:80` exposerait le service au monde malgré `firewalld` ou
`ufw`.

## Répartition des responsabilités

gChess est réparti sur trois dépôts :

| Dépôt | Contenu |
|---|---|
| back (Kotlin/Ktor) | image du back **et** toute la stack (`deploy/`) |
| front (Angular) | image du front uniquement |
| mobile (Flutter) | app mobile, pointe sur le même domaine |

Le déploiement du back synchronise `deploy/` vers `/opt/gchess` sur le serveur.
Le déploiement du front ne fait que changer son image. `/opt/gchess/.env` n'est
jamais écrasé : la CI n'y réécrit que les lignes `BACK_IMAGE` et `FRONT_IMAGE`.

---

## 1. Prérequis DNS

Un enregistrement **A** doit pointer sur le serveur avant toute demande de
certificat — Let's Encrypt valide le domaine via HTTP sur le port 80.

```
chess.example.com.   A   203.0.113.42
```

Vérification :

```bash
dig +short chess.example.com    # doit répondre l'IP du serveur
```

## 2. Préparation du serveur

À exécuter **sur le serveur**, en root ou via `sudo`. Les commandes sont
données pour les deux grandes familles de distributions.

### Docker

<details open>
<summary><strong>RHEL / CentOS Stream / AlmaLinux / Rocky / Fedora</strong></summary>

```bash
# Le fichier de dépôt est posé directement plutôt que via `dnf config-manager`,
# dont la syntaxe a changé entre dnf4 et dnf5.
curl -fsSL https://download.docker.com/linux/centos/docker-ce.repo \
  -o /etc/yum.repos.d/docker-ce.repo

dnf -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

# Ne pas installer podman-docker : l'émulation ne gère pas docker compose
# comme attendu ici. En cas de conflit : dnf -y remove podman-docker
```
</details>

<details>
<summary><strong>Debian / Ubuntu</strong></summary>

```bash
apt-get update && apt-get install -y ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Remplacer `debian` par `ubuntu` dans les deux URL sur Ubuntu.
</details>

### Utilisateur, emplacement, pare-feu

```bash
# --- Utilisateur de déploiement, sans privilèges root ---
# Aucun mot de passe n'est défini : le compte est verrouillé pour
# l'authentification par mot de passe, seule la clé SSH y donne accès.
useradd --create-home --shell /bin/bash deploy
usermod -aG docker deploy

# --- Emplacement de la stack ---
mkdir -p /opt/gchess
chown deploy:deploy /opt/gchess

# --- Pare-feu ---
# RHEL et dérivés (firewalld)
firewall-cmd --permanent --add-service=ssh
firewall-cmd --permanent --add-service=http
firewall-cmd --permanent --add-service=https
firewall-cmd --reload

# Debian / Ubuntu (ufw)
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable
```

Vérifiez que le port de la stack est libre :

```bash
ss -tlnp | grep 8080    # ne doit rien renvoyer ; sinon ajuster HTTP_PORT dans .env
```

```bash
sudo -u deploy docker version
sudo -u deploy docker compose version
```

> L'appartenance au groupe `docker` équivaut à un accès root sur la machine.
> C'est inhérent au fait de piloter Docker à distance ; l'utilisateur `deploy`
> sert uniquement à ça et n'a pas de mot de passe.

### SELinux (RHEL et dérivés uniquement)

SELinux y est en mode *enforcing* par défaut, ce qui change trois choses par
rapport à une Debian/Ubuntu :

```bash
# Sans ça, le nginx système reçoit un "Permission denied" en tentant de
# joindre 127.0.0.1:8080, et renvoie 502 sur toute l'application.
setsebool -P httpd_can_network_connect 1
```

- Le montage de la configuration nginx porte le suffixe `:z` dans
  `docker-compose.yml`, pour que le fichier soit réétiqueté en
  `container_file_t`. Sans ça, nginx ne peut pas lire sa propre conf.
- Aucun conteneur ne monte `/var/run/docker.sock`, que SELinux refuse — et qui
  reviendrait de toute façon à donner un accès root au conteneur.

Il n'y a **rien à désactiver**. Si un conteneur échoue avec un `Permission
denied` inexpliqué sur un fichier monté, c'est le premier endroit où regarder :

```bash
ausearch -m avc -ts recent          # refus SELinux récents
ls -Z /opt/gchess/nginx/            # étiquettes des fichiers montés
```

## 3. Clé SSH de déploiement

Génération de la paire, **sur votre poste** :

```bash
ssh-keygen -t ed25519 -f ~/.ssh/gchess_deploy -C "github-actions-gchess" -N ""
```

> ⚠️ **Ne pas utiliser `ssh-copy-id`.** Le compte `deploy` a été créé sans mot
> de passe, donc verrouillé pour l'authentification par mot de passe :
> `ssh-copy-id` n'a aucun moyen de s'authentifier et échouera. La clé doit être
> installée via un accès root.

```bash
ssh root@203.0.113.42 '
  install -d -m 700 -o deploy -g deploy /home/deploy/.ssh &&
  cat > /home/deploy/.ssh/authorized_keys &&
  chown deploy:deploy /home/deploy/.ssh/authorized_keys &&
  chmod 600 /home/deploy/.ssh/authorized_keys &&
  command -v restorecon >/dev/null && restorecon -R -v /home/deploy/.ssh || true
' < ~/.ssh/gchess_deploy.pub
```

Le `restorecon` n'est pas optionnel sous SELinux : un `authorized_keys` créé à
la main hérite d'un contexte que sshd refuse de lire. La connexion échoue alors
en boucle sur la clé, **sans message d'erreur côté client** — le serveur
redemande simplement un mot de passe, comme si la clé était inconnue.

Vérification :

```bash
ssh -i ~/.ssh/gchess_deploy deploy@203.0.113.42 "docker version"
```

La **clé privée** (`~/.ssh/gchess_deploy`, en entier, y compris les lignes
`BEGIN`/`END`) devient le secret `SSH_PRIVATE_KEY`.

### Si la connexion par clé est refusée

Symptôme : le serveur redemande un mot de passe malgré la clé installée.

```bash
# Sur le serveur, en root — sous SELinux, le contexte est la cause n°1
ls -Z /home/deploy/.ssh/authorized_keys   # attendu : ssh_home_t
restorecon -R -v /home/deploy/.ssh

# Deuxième cause : permissions trop larges sur le home ou le .ssh
ls -ld /home/deploy /home/deploy/.ssh     # attendu : 700 ou 750 pour le home

journalctl -u sshd -n 30
```

Côté client, `ssh -vvv -i ~/.ssh/gchess_deploy deploy@203.0.113.42` indique si
la clé est bien proposée, et pourquoi elle est rejetée.

## 4. Secrets GitHub

À créer dans le dépôt **back** et dans le dépôt **front**
(*Settings → Secrets and variables → Actions*), en tant que **repository
secrets** — les *environment secrets* ne seraient pas injectés, les workflows
ne déclarant aucun environnement.

| Secret | Valeur |
|---|---|
| `SSH_HOST` | l'IP ou le nom d'hôte du serveur |
| `SSH_USER` | `deploy` |
| `SSH_PRIVATE_KEY` | contenu de `~/.ssh/gchess_deploy` |
| `SSH_PORT` | *(optionnel, défaut 22)* |
| `SSH_KNOWN_HOSTS` | *(optionnel)* sortie de `ssh-keyscan -H <hôte>`, pour épingler la clé du serveur |

Et une **variable** (onglet *Variables*, pas *Secrets*) :

| Variable | Valeur |
|---|---|
| `DOMAIN` | votre domaine, ex. `chess.example.com` |

Aucun identifiant de registre à stocker : le serveur s'authentifie sur GHCR
avec le `GITHUB_TOKEN` du job, transmis pour la durée du déploiement et révoqué
à la fin du workflow.

## 5. Premier démarrage

Le tout premier déploiement se fait à la main : les images n'existent pas
encore et le nginx système ne connaît pas encore le domaine.

```bash
# 1. Déclencher un build des deux images
#    (GitHub → Actions → Deploy → Run workflow, sur les 2 dépôts)
#    L'étape de déploiement échouera : c'est attendu, /opt/gchess est vide.

# 2. Sur le serveur, récupérer la stack
ssh -i ~/.ssh/gchess_deploy deploy@203.0.113.42
cd /opt/gchess
# (le déploiement du back y a poussé docker-compose.yml et nginx/ avant
#  d'échouer ; sinon, copier deploy/ à la main par rsync)

# 3. Configurer les secrets applicatifs
cp .env.example .env
chmod 600 .env
openssl rand -base64 32   # → JWT_SECRET
openssl rand -base64 24   # → DATABASE_PASSWORD
nano .env                 # remplacer les CHANGE_ME et renseigner DOMAIN
```

Si les images GHCR sont privées, il faut s'authentifier avec un **token GitHub
classique** limité à la portée `read:packages` — les tokens *fine-grained* ne
sont pas supportés par le Container registry. **Ne jamais écrire le token dans
une commande** : il resterait en clair dans `~/.bash_history`.

```bash
read -rs GHCR_TOKEN       # coller le token, puis Entrée (rien ne s'affiche)
echo "$GHCR_TOKEN" | docker login ghcr.io -u <votre-compte> --password-stdin
unset GHCR_TOKEN
```

Le plus simple reste de rendre les paquets publics
(*github.com/users/&lt;votre-compte&gt;/packages* → *Package settings* →
*Change visibility*) : le serveur n'a alors aucun identifiant à stocker, et les
images ne contiennent que du code déjà public — les secrets vivent dans `.env`,
injectés au démarrage.

```bash
# 4. Démarrer la stack
docker compose up -d
curl http://127.0.0.1:8080/health    # {"status":"UP"}, en local sur le serveur
```

### Brancher le nginx système

```bash
sudo cp /opt/gchess/nginx-host/gchess.conf /etc/nginx/conf.d/gchess.conf
sudo sed -i 's/chess\.example\.com/VOTRE_DOMAINE/' /etc/nginx/conf.d/gchess.conf
sudo nginx -t && sudo systemctl reload nginx

# TLS : certbot écrit lui-même listen 443, les certificats et la
# redirection HTTP→HTTPS dans le fichier.
sudo certbot --nginx -d VOTRE_DOMAINE
```

Vos autres sites ne sont pas touchés : ce fichier n'ajoute qu'un `server`
supplémentaire, sélectionné par `server_name`.

### Vérification

```bash
# Ne pas utiliser `curl -I` : il envoie un HEAD, et les routes Ktor sont
# déclarées en `get(...)` — la réponse serait un 405 trompeur.
curl -s -o /dev/null -w '%{http_code}\n' https://chess.example.com/health   # 200
curl -s -o /dev/null -w '%{http_code}\n' https://chess.example.com/         # 200

# API protégée : 401 sans JWT, c'est le comportement attendu
curl -s -o /dev/null -w '%{http_code}\n' https://chess.example.com/api/history/games

# WebSocket : doit répondre 101 Switching Protocols.
# curl affiche « FAILED » juste après — il ne sait pas parler WebSocket une
# fois le protocole changé. Seul le code 101 compte.
curl -s -o /dev/null -w '%{http_code}\n' \
  -H "Connection: Upgrade" -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
  https://chess.example.com/ws/matchmaking
```

Ensuite, **tout push sur `master` déploie automatiquement**.

## 6. Exploitation

```bash
cd /opt/gchess

docker compose ps                    # état des services
docker compose logs -f back          # logs du back
docker compose logs -f nginx

docker compose restart back
docker compose up -d --force-recreate nginx   # après modif de la conf nginx
```

### Rollback

Chaque image est taguée par SHA de commit :

```bash
cd /opt/gchess
sed -i "s|^BACK_IMAGE=.*|BACK_IMAGE=ghcr.io/<votre-compte>/gchess-back:<sha>|" .env
docker compose up -d back
```

Le SHA se lit dans l'historique des workflows, ou dans
`docker images ghcr.io/<votre-compte>/gchess-back`.

### Changer le mot de passe de la base

`POSTGRES_PASSWORD` n'est lu qu'à la **première initialisation**, quand le
volume de données est vide. Le modifier ensuite dans `.env` ne change rien à la
base : l'application présente le nouveau mot de passe, PostgreSQL attend
toujours l'ancien, et le back boucle sur `password authentication failed`.

Il faut le changer des deux côtés :

```bash
cd /opt/gchess

# Charge les valeurs du .env sans les taper (donc sans historique shell)
set -a; . ./.env; set +a

# Les connexions locales par socket unix sont en "trust" dans l'image
# officielle : l'ancien mot de passe n'est pas nécessaire. Le nouveau
# passe par stdin, donc invisible dans `ps`.
printf "ALTER USER %s PASSWORD '%s';\n" "$DATABASE_USER" "$DATABASE_PASSWORD" \
  | docker compose exec -T postgres psql -U "$DATABASE_USER" -d "$DATABASE_NAME"

docker compose restart back
docker compose logs --tail 30 back   # attendu : "Responding at http://0.0.0.0:8080"
```

Si la base est encore vide, repartir de zéro est plus court — mais **détruit
définitivement son contenu** :

```bash
docker compose down
docker volume rm gchess_pgdata
docker compose up -d
```

### Sauvegarde de la base

Les parties et les comptes vivent dans le volume `gchess_pgdata`.

```bash
cd /opt/gchess
set -a; . ./.env; set +a

# Dump
docker compose exec -T postgres pg_dump -U "$DATABASE_USER" "$DATABASE_NAME" \
  | gzip > gchess-$(date +%F).sql.gz

# Restauration
gunzip -c gchess-2026-01-31.sql.gz \
  | docker compose exec -T postgres psql -U "$DATABASE_USER" "$DATABASE_NAME"
```

À automatiser via cron :

```cron
0 3 * * * cd /opt/gchess && docker compose exec -T postgres pg_dump -U gchess gchess | gzip > /var/backups/gchess-$(date +\%F).sql.gz
```

### Certificats

Le TLS est entièrement géré par le Certbot **système**, comme pour vos autres
sites. La stack n'a ni certificat ni service certbot.

```bash
sudo certbot certificates              # dates d'expiration, tous domaines
sudo certbot renew --dry-run           # vérifier le renouvellement automatique
systemctl list-timers | grep certbot   # le timer qui s'en charge
```

Le renouvellement recharge nginx tout seul : `certbot --nginx` installe le hook
correspondant.

## 7. Points de vigilance

- **La file de matchmaking est en mémoire.** Un redéploiement du back vide la
  file : les joueurs en attente doivent relancer une recherche. Les parties en
  cours et les comptes sont en base, donc préservés.
- **Le déploiement du back coupe le service quelques secondes** (le temps du
  redémarrage et des migrations Liquibase). Les WebSockets ouvertes sont
  fermées et les clients doivent se reconnecter.
- **`JWT_SECRET` ne doit pas changer** en dehors d'une rotation volontaire :
  tous les tokens émis deviendraient invalides et chacun serait déconnecté.
  Contrairement au mot de passe de la base, il n'existe aucune procédure de
  rattrapage — sauvegardez `.env` hors du serveur.
- **Aucune sauvegarde n'est configurée par défaut.** Mettre en place le cron
  ci-dessus avant de considérer la production comme sérieuse.
- **Le nginx système est partagé avec vos autres sites.** Un `nginx -t` qui
  échoue empêche le rechargement et fige la configuration de *tous* les sites.
  Toujours tester avant de recharger.
- **`deploy/nginx-host/gchess.conf` n'est pas déployé automatiquement.** Il est
  versionné pour référence, mais la CI ne touche jamais à `/etc/nginx`. Après
  le passage de certbot, la copie sur l'hôte diverge d'ailleurs de celle du
  dépôt, ce qui est normal.
- **Le domaine de production est compilé dans le front.** Un fork doit adapter
  `src/environments/environment.ts` (Angular) et `lib/config/app_config.dart`
  (Flutter) avant de construire ses images.
