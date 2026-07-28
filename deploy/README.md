# Déploiement gChess

Stack de production servie depuis **72.62.236.230**, sur le domaine
**gchess.sur-le-web.fr**.

Le serveur héberge déjà d'autres sites derrière un **nginx système**, qui
détient les ports 80 et 443. gChess se place derrière lui.

```
                 ┌──────────────── serveur 72.62.236.230 ────────────────┐
 Internet        │                                                       │
    │            │  nginx SYSTÈME ──┬─→ site perso (inchangé)            │
    └─── 443 ────┼─→ TLS, certbot   │                                    │
                 │                  └─→ 127.0.0.1:8080                   │
                 │                        │                              │
                 │                        └─→ nginx CONTENEUR            │
                 │                             ├─→ front    (Angular)    │
                 │                             ├─→ back     (Ktor)       │
                 │                             └─→ postgres              │
                 └───────────────────────────────────────────────────────┘
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
négligeable.

**Rien n'est joignable depuis l'extérieur** hormis le nginx système : la stack
est publiée sur `127.0.0.1` uniquement. Ce bind explicite compte, car Docker
insère ses règles directement dans iptables, en contournant firewalld — sans
lui, un `ports: 8080:80` exposerait le service au monde malgré le pare-feu.

## Répartition des responsabilités

| Repo | Contenu |
|---|---|
| `gbourquet/gChess` (ce repo) | image du back **et** toute la stack (`deploy/`) |
| `gbourquet/gChess-front` | image du front uniquement |
| `gbourquet/gchess-mobile` | app mobile, pointe sur le même domaine |

Le déploiement du back synchronise `deploy/` vers `/opt/gchess` sur le
serveur. Le déploiement du front ne fait que changer son image. `/opt/gchess/.env`
n'est jamais écrasé : la CI n'y réécrit que les lignes `BACK_IMAGE` et
`FRONT_IMAGE`.

---

## 1. Prérequis DNS

Un enregistrement **A** doit pointer sur le serveur avant toute demande de
certificat — Let's Encrypt valide le domaine via HTTP sur le port 80.

```
gchess.sur-le-web.fr.   A   72.62.236.230
```

Vérification :

```bash
dig +short gchess.sur-le-web.fr    # doit répondre 72.62.236.230
```

## 2. Préparation du serveur

Le serveur tourne sous **CentOS Stream 10 (Coughlan)**, noyau `6.12.x-el10`.
SELinux y est actif par défaut : voir la sous-section dédiée plus bas.

À exécuter **sur le serveur**, en root ou via `sudo`.

```bash
# --- Docker CE ---
# Le fichier de dépôt est posé directement plutôt que via `dnf config-manager`,
# dont la syntaxe a changé entre dnf4 et dnf5 (CentOS Stream 10 utilise dnf5).
# Dans ce dépôt, $releasever vaut 10 : il pointe donc sur les paquets el10.
curl -fsSL https://download.docker.com/linux/centos/docker-ce.repo \
  -o /etc/yum.repos.d/docker-ce.repo

dnf -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

# Ne pas installer podman-docker : l'émulation ne gère pas docker compose
# comme attendu ici. En cas de conflit : dnf -y remove podman-docker

# --- Utilisateur de déploiement, sans privilèges root ---
# Aucun mot de passe n'est défini : le compte est verrouillé pour
# l'authentification par mot de passe, seule la clé SSH y donne accès.
useradd --create-home --shell /bin/bash deploy
usermod -aG docker deploy

# --- Emplacement de la stack ---
mkdir -p /opt/gchess
chown deploy:deploy /opt/gchess

# --- Pare-feu (firewalld, pas ufw) ---
# Déjà en place si le nginx système sert un site : à vérifier seulement.
firewall-cmd --permanent --add-service=ssh
firewall-cmd --permanent --add-service=http
firewall-cmd --permanent --add-service=https
firewall-cmd --reload

# --- SELinux : autoriser nginx à proxifier vers la boucle locale ---
# Sans ça, le nginx système reçoit un "Permission denied" en tentant de
# joindre 127.0.0.1:8080, et renvoie 502 sur tout gChess.
setsebool -P httpd_can_network_connect 1
```

Vérifie que le port choisi est libre avant de démarrer la stack :

```bash
ss -tlnp | grep 8080    # doit ne rien renvoyer ; sinon, ajuster HTTP_PORT dans .env
```

Vérification :

```bash
sudo -u deploy docker version
sudo -u deploy docker compose version
```

> L'appartenance au groupe `docker` équivaut à un accès root sur la machine.
> C'est inhérent au fait de piloter Docker à distance ; l'utilisateur `deploy`
> sert uniquement à ça et n'a pas de mot de passe.

### SELinux

SELinux est en mode *enforcing* par défaut sur CentOS Stream, ce qui change
deux choses par rapport à une Debian/Ubuntu :

- Le montage de la configuration nginx porte le suffixe `:z` dans
  `docker-compose.yml`, pour que le fichier soit réétiqueté en
  `container_file_t`. Sans ça, nginx ne peut pas lire sa propre conf.
- Aucun conteneur ne monte `/var/run/docker.sock` — d'où le cron ci-dessus
  pour recharger nginx.

Il n'y a **rien à désactiver**. Si un conteneur échoue avec un `Permission
denied` inexpliqué sur un fichier monté, c'est le premier endroit où regarder :

```bash
ausearch -m avc -ts recent          # refus SELinux récents
ls -Z /opt/gchess/nginx/            # étiquettes des fichiers montés
```

## 3. Clé SSH de déploiement

Génération de la paire, **sur ton poste** :

```bash
ssh-keygen -t ed25519 -f ~/.ssh/gchess_deploy -C "github-actions-gchess" -N ""
```

> ⚠️ **Ne pas utiliser `ssh-copy-id`** ici. Le compte `deploy` a été créé sans
> mot de passe, donc verrouillé pour l'authentification par mot de passe :
> `ssh-copy-id` n'a aucun moyen de s'authentifier et échouera. La clé doit être
> installée via un accès root.

Installation de la clé, en une commande depuis ton poste (adapte `root` si ton
accès administrateur porte un autre nom) :

```bash
ssh root@72.62.236.230 '
  install -d -m 700 -o deploy -g deploy /home/deploy/.ssh &&
  cat > /home/deploy/.ssh/authorized_keys &&
  chown deploy:deploy /home/deploy/.ssh/authorized_keys &&
  chmod 600 /home/deploy/.ssh/authorized_keys &&
  restorecon -R -v /home/deploy/.ssh
' < ~/.ssh/gchess_deploy.pub
```

Le `restorecon` n'est pas optionnel sur CentOS Stream : un fichier
`authorized_keys` créé à la main hérite d'un contexte SELinux que sshd refuse
de lire. La connexion échoue alors en boucle sur la clé, **sans message
d'erreur côté client** — le serveur redemande simplement un mot de passe, comme
si la clé était inconnue.

Vérification :

```bash
ssh -i ~/.ssh/gchess_deploy deploy@72.62.236.230 "docker version"
```

La **clé privée** (`~/.ssh/gchess_deploy`, en entier, y compris les lignes
`BEGIN`/`END`) devient le secret `SSH_PRIVATE_KEY`.

### Si la connexion par clé est refusée

Symptôme : le serveur redemande un mot de passe malgré la clé installée.

```bash
# Sur le serveur, en root — la cause la plus fréquente est le contexte SELinux
ls -Z /home/deploy/.ssh/authorized_keys   # attendu : ssh_home_t
restorecon -R -v /home/deploy/.ssh

# Deuxième cause : permissions trop larges sur le home ou le .ssh
ls -ld /home/deploy /home/deploy/.ssh     # attendu : 700 ou 750 pour le home

# Diagnostic côté serveur
journalctl -u sshd -n 30
```

Côté client, `ssh -vvv -i ~/.ssh/gchess_deploy deploy@72.62.236.230` indique
si la clé est bien proposée et rejetée.

## 4. Secrets GitHub

À créer dans **`gChess`** et **`gChess-front`**
(*Settings → Secrets and variables → Actions*) :

| Secret | Valeur |
|---|---|
| `SSH_HOST` | `72.62.236.230` |
| `SSH_USER` | `deploy` |
| `SSH_PRIVATE_KEY` | contenu de `~/.ssh/gchess_deploy` |
| `SSH_PORT` | *(optionnel, défaut 22)* |

Et une **variable** (onglet *Variables*, pas *Secrets*) :

| Variable | Valeur |
|---|---|
| `DOMAIN` | `gchess.sur-le-web.fr` |

Aucun identifiant de registre à stocker : le serveur s'authentifie sur GHCR
avec le `GITHUB_TOKEN` du job, transmis pour la durée du déploiement et
révoqué à la fin du workflow.

## 5. Premier démarrage

Le tout premier déploiement se fait à la main : les images n'existent pas
encore et le nginx système ne connaît pas encore le domaine.

```bash
# 1. Déclencher un build des deux images
#    (GitHub → Actions → Deploy → Run workflow, sur les 2 repos)
#    L'étape de déploiement échouera : c'est attendu, /opt/gchess est vide.

# 2. Sur le serveur, récupérer la stack
ssh -i ~/.ssh/gchess_deploy deploy@72.62.236.230
cd /opt/gchess
# (le déploiement du back y a poussé docker-compose.yml et nginx/ avant
#  d'échouer ; sinon, copier deploy/ à la main par rsync)

# 3. Configurer les secrets applicatifs
cp .env.example .env
chmod 600 .env
openssl rand -base64 32   # → JWT_SECRET
openssl rand -base64 24   # → DATABASE_PASSWORD
nano .env                 # remplacer les deux CHANGE_ME
```

Si les images GHCR sont privées, il faut s'authentifier. **Ne jamais écrire le
token dans une commande** : il resterait en clair dans `~/.bash_history`.

```bash
read -rs GHCR_TOKEN       # coller le token, puis Entrée (rien ne s'affiche)
echo "$GHCR_TOKEN" | docker login ghcr.io -u gbourquet --password-stdin
unset GHCR_TOKEN
```

Le plus simple reste de rendre les paquets publics (*github.com/users/gbourquet/
packages* → *Package settings* → *Change visibility*) : le serveur n'a alors
aucun identifiant à stocker, et les images ne contiennent que du code déjà
public — les secrets vivent dans `.env`, injectés au démarrage.

```bash
# 4. Démarrer la stack
docker compose up -d
curl -I http://127.0.0.1:8080/health          # 200, en local sur le serveur
```

### Brancher le nginx système

```bash
sudo cp /opt/gchess/nginx-host/gchess.conf /etc/nginx/conf.d/gchess.conf
sudo nginx -t && sudo systemctl reload nginx

# TLS : certbot écrit lui-même listen 443, les certificats et la
# redirection HTTP→HTTPS dans le fichier.
sudo certbot --nginx -d gchess.sur-le-web.fr
```

Les autres sites du serveur ne sont pas touchés : ce fichier n'ajoute qu'un
`server` supplémentaire, sélectionné par `server_name`.

Vérification :

```bash
curl -I https://gchess.sur-le-web.fr/health   # 200
curl -I https://gchess.sur-le-web.fr/         # 200

# WebSocket : doit répondre 101 Switching Protocols
curl -i -N -o /dev/null -w '%{http_code}\n' \
  -H "Connection: Upgrade" -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
  https://gchess.sur-le-web.fr/ws/matchmaking
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
sed -i "s|^BACK_IMAGE=.*|BACK_IMAGE=ghcr.io/gbourquet/gchess-back:<sha>|" .env
docker compose up -d back
```

Le SHA se lit dans l'historique des workflows, ou :

```bash
docker images ghcr.io/gbourquet/gchess-back
```

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
# Dump
docker compose exec -T postgres pg_dump -U gchess gchess | gzip > gchess-$(date +%F).sql.gz

# Restauration
gunzip -c gchess-2026-07-26.sql.gz | docker compose exec -T postgres psql -U gchess gchess
```

À automatiser via cron :

```cron
0 3 * * * cd /opt/gchess && docker compose exec -T postgres pg_dump -U gchess gchess | gzip > /var/backups/gchess-$(date +\%F).sql.gz
```

### Certificats

Le TLS est entièrement géré par le Certbot **système**, comme pour les autres
sites du serveur. La stack n'a ni certificat ni service certbot : rien de
spécifique à gChess n'est à surveiller ici.

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
- **Aucune sauvegarde n'est configurée par défaut.** Mettre en place le cron
  ci-dessus avant de considérer la prod comme sérieuse.
- **Le nginx système est partagé avec les autres sites.** Un `nginx -t` qui
  échoue empêche le rechargement et fige la configuration de *tous* les sites.
  Toujours tester avant de recharger.
- **`deploy/nginx-host/gchess.conf` n'est pas déployé automatiquement.** Il est
  versionné pour référence, mais la CI ne touche jamais à `/etc/nginx`. Après
  le passage de certbot, la copie sur l'hôte diverge d'ailleurs de celle du
  repo, ce qui est normal.
