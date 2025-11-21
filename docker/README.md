# Docker Compose - PostgreSQL Database

Ce répertoire contient la configuration Docker Compose pour lancer PostgreSQL en développement local.

## Démarrage rapide

### Démarrer la base de données

```bash
cd docker
docker compose up -d
```

La base de données sera accessible sur `localhost:5432`.

### Arrêter la base de données

```bash
docker compose down
```

### Arrêter ET supprimer les données

```bash
docker compose down -v
rm -rf data/*
```

## Configuration

**Credentials par défaut** (définis dans `docker-compose.yml`) :
- **Database** : `gchess_dev`
- **User** : `gchess`
- **Password** : `gchess`
- **Port** : `5432`

Ces valeurs correspondent aux paramètres par défaut de l'application (voir `DatabaseConfig.kt`).

## Persistance des données

Les données PostgreSQL sont persistées dans le répertoire `./data/` :
- ✅ Les données survivent aux redémarrages du conteneur
- ✅ Le répertoire `data/` est ignoré par git (voir `.gitignore`)
- ⚠️ Pour repartir d'une base vierge, supprimez le contenu de `./data/`

## Vérifier l'état

```bash
# Voir les logs
docker compose logs -f

# Vérifier que PostgreSQL est prêt
docker compose exec postgres pg_isready -U gchess

# Se connecter à psql
docker compose exec postgres psql -U gchess -d gchess_dev
```

## Commandes utiles

```bash
# Redémarrer PostgreSQL
docker compose restart

# Voir les processus en cours
docker compose ps

# Supprimer le conteneur (garde les données)
docker compose down

# Supprimer le conteneur ET les données
docker compose down -v
```

## Connexion depuis l'application

L'application se connecte automatiquement avec les variables d'environnement par défaut :

```bash
# Pas besoin de configurer si vous utilisez les valeurs par défaut
./gradlew run
```

Ou avec des valeurs personnalisées :

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/gchess_dev"
export DATABASE_USER="gchess"
export DATABASE_PASSWORD="gchess"
./gradlew run
```

## Migrations Liquibase

Les migrations s'exécutent automatiquement au démarrage de l'application.

Pour voir les tables créées :

```bash
docker compose exec postgres psql -U gchess -d gchess_dev -c "\dt"
```
