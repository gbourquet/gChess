# GitHub Actions Workflows

Ce répertoire contient les workflows CI/CD pour gChess.

## Workflows Disponibles

### 🔨 CI - Build & Test (`ci.yml`)

**Déclenché par**:
- Push sur toutes les branches
- Pull requests

**Ce qu'il fait**:
1. **Build JAR**: Compile le projet, génère jOOQ, crée le fat JAR
2. **Tests**: Unit tests, architecture tests, integration tests (Testcontainers)
3. **Docker**: Build l'image et push vers GitHub Container Registry (seulement sur `master`)
4. **Security**: Scan Trivy pour vulnérabilités

**Artifacts**:
- `gchess-jar`: JAR compilé (retenu 1 jour)
- `test-reports`: Rapports de tests (si échec, retenu 7 jours)

**Images Docker**:
- `ghcr.io/gbourquet/gchess:sha-XXXXXXX` - Tag par commit
- `ghcr.io/gbourquet/gchess:latest` - Dernière version sur master
- `ghcr.io/gbourquet/gchess:test` - Version pour environnement test

---

### 🚀 CD - Deploy Test (`deploy-test.yml`)

**Déclenché par**:
- Push sur `master` (automatique)
- Manuel (workflow_dispatch)

**Ce qu'il fait**:
1. **Wait for CI**: Attend que le workflow CI soit en succès
2. **Deploy**: Trigger le deploy hook Render.com
3. **Health Checks**: Valide que le déploiement fonctionne
4. **Summary**: Génère un résumé avec liens

**Environnement**: https://gchess-test.sur-le-web.fr

**Secrets requis**:
- `RENDER_DEPLOY_HOOK_URL` - URL du deploy hook Render

**Durée typique**: 3-5 minutes (+ cold start Render si inactif)

---

### 🏭 CD - Deploy Production (`deploy-prod.yml`)

**Déclenché par**:
- Tags `v*.*.*` (ex: `v1.0.0`)
- Manuel avec version spécifique

**Ce qu'il fait**:
1. **Validate**: Vérifie que l'image Docker existe
2. **Deploy**: SSH vers le serveur, pull image, redémarre services
3. **Migrations**: Exécute les migrations database
4. **Health Checks**: Validation complète
5. **Rollback**: Rollback automatique si échec

**Environnement**: https://gchess.sur-le-web.fr

**Secrets requis**:
- `PROD_SSH_HOST` - IP/hostname du serveur
- `PROD_SSH_USER` - Utilisateur SSH
- `PROD_SSH_KEY` - Clé privée SSH
- `PROD_ENV_FILE` - Contenu .env.prod (base64)

**Durée typique**: 5-8 minutes

---

## Configuration Requise

### GitHub Repository Settings

1. **Actions → General → Workflow permissions**:
   - ✅ Read and write permissions

2. **Secrets and variables → Actions**:
   - `RENDER_DEPLOY_HOOK_URL` (pour test)
   - `PROD_SSH_HOST`, `PROD_SSH_USER`, `PROD_SSH_KEY`, `PROD_ENV_FILE` (pour prod)

### GitHub Container Registry

- Activé automatiquement
- Packages visibles sur: https://github.com/USERNAME?tab=packages

---

## Usage

### Déployer en Test

```bash
# Méthode 1: Push sur master (automatique)
git push origin master

# Méthode 2: Manuel via GitHub UI
# Actions → CD - Deploy Test → Run workflow
```

### Déployer en Production

```bash
# Méthode 1: Créer un tag (automatique)
git tag v1.0.0
git push origin v1.0.0

# Méthode 2: Manuel via GitHub UI
# Actions → CD - Deploy Production → Run workflow → Entrer version
```

### Vérifier le Status

```bash
# Via GitHub UI
# Actions → Workflow run → Jobs

# Via GitHub CLI
gh run list
gh run view <run-id>
gh run watch
```

---

## Workflow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  Developer                                                  │
└───────────────┬─────────────────────────────────────────────┘
                │
                │ git push
                ▼
┌─────────────────────────────────────────────────────────────┐
│  CI Workflow (ci.yml)                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Build JAR    │→ │ Run Tests    │→ │ Build Docker │     │
│  │ + jOOQ       │  │ (Unit/Arch/  │  │ Push GHCR    │     │
│  │              │  │  Integration)│  │              │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└───────────────┬─────────────────────────────────────────────┘
                │
                │ On master only
                ▼
┌─────────────────────────────────────────────────────────────┐
│  CD Test Workflow (deploy-test.yml)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Wait for CI  │→ │ Trigger      │→ │ Health       │     │
│  │              │  │ Render Hook  │  │ Checks       │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└───────────────┬─────────────────────────────────────────────┘
                │
                │ Render.com
                ▼
         https://gchess-test.sur-le-web.fr

                │
                │ git tag v*.*.*
                ▼
┌─────────────────────────────────────────────────────────────┐
│  CD Prod Workflow (deploy-prod.yml)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Validate     │→ │ SSH Deploy   │→ │ Health       │     │
│  │ Image Exists │  │ + Migrations │  │ Checks       │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                                              │ fail        │
│                                              ▼             │
│                                       ┌──────────────┐     │
│                                       │ Rollback     │     │
│                                       └──────────────┘     │
└───────────────┬─────────────────────────────────────────────┘
                │
                │ VPS/Cloud
                ▼
         https://gchess.sur-le-web.fr
```

---

## Troubleshooting

### CI Fails

1. **Tests échouent**: Consulter les artifacts `test-reports`
2. **Docker build échoue**: Vérifier que le JAR existe (`build/libs/`)
3. **Push GHCR échoue**: Vérifier permissions Actions (read/write)

### CD Test Fails

1. **Deploy hook timeout**: Vérifier `RENDER_DEPLOY_HOOK_URL`
2. **Health checks fail**: Consulter logs Render.com
3. **Cold start timeout**: Augmenter le timeout (service en veille)

### CD Prod Fails

1. **SSH échoue**: Vérifier `PROD_SSH_KEY` (format OpenSSH complet)
2. **Image not found**: Vérifier que CI a bien push l'image
3. **Health checks fail**: SSH au serveur, consulter logs Docker

---

## Documentation

- **Guide complet**: Voir `/DEPLOYMENT.md`
- **Plan de déploiement**: Voir `/deployment_plan.md`

---

**Note**: Les workflows utilisent l'approche "Write Once, Run Anywhere":
1. Build Gradle (local ou CI) → JAR
2. Build Docker (copie JAR) → Image
3. Run (local, test, prod) → Container

Testcontainers fonctionne nativement en CI (pas de Docker-in-Docker).
