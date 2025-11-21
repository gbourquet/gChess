# gChess API Tests - Bruno Collection

Cette collection Bruno contient des tests end-to-end pour l'API gChess, reproduisant les scénarios des tests d'intégration automatisés.

## Installation de Bruno

Bruno est un client HTTP open-source similaire à Postman/Insomnia, mais basé sur des fichiers (git-friendly).

### Télécharger Bruno

- **Site officiel** : https://www.usebruno.com/
- **GitHub** : https://github.com/usebruno/bruno

**Installation** :
- macOS : `brew install bruno`
- Windows : Télécharger depuis le site
- Linux : AppImage ou `snap install bruno`

## Utilisation

### 1. Démarrer la base de données

```bash
cd ../docker
docker compose up -d
```

### 2. Démarrer l'application

```bash
cd ..
./gradlew run
```

Attendez que l'application soit prête (environ 10-15 secondes) :
```
Application started in 0.7 seconds.
Responding at http://0.0.0.0:8080
```

### 3. Ouvrir Bruno

1. Lancez Bruno
2. Cliquez sur "Open Collection"
3. Sélectionnez le répertoire `test/bruno`

### 4. Sélectionner l'environnement

Dans Bruno, sélectionnez l'environnement **"Local"** en haut à droite.

### 5. Exécuter les tests

#### Option 1 : Exécuter test par test

Parcourez les dossiers et cliquez sur chaque requête pour l'exécuter.

**Ordre recommandé** :
1. **1. Auth** → Register & Login (crée les tokens)
2. **2. Game** → Créer et jouer une partie
3. **3. Matchmaking** → Tester le système de matchmaking

#### Option 2 : Exécuter toute la collection

Clic droit sur "gChess API" → "Run Collection" pour exécuter tous les tests en séquence.

## Structure de la collection

```
test/bruno/
├── bruno.json                      # Configuration de la collection
├── environments/
│   └── Local.bru                   # Variables d'environnement (tokens, IDs, etc.)
├── 1. Auth/                        # Tests d'authentification
│   ├── Register White Player.bru
│   ├── Login White Player.bru
│   ├── Register Black Player.bru
│   └── Login Black Player.bru
├── 2. Game/                        # Tests de partie d'échecs
│   ├── Create Game.bru
│   ├── Get Game.bru
│   ├── Move 1 - White e2 to e4.bru
│   ├── Move 2 - Black e7 to e5.bru
│   └── Move 3 - Invalid Turn (should fail).bru
└── 3. Matchmaking/                 # Tests de matchmaking
    ├── Join Queue - Player 1.bru
    ├── Get Status - Player 1 Waiting.bru
    ├── Join Queue - Already in Queue (should fail).bru
    ├── Join Queue - Player 2 (creates match).bru
    ├── Get Status - Player 1 Matched.bru
    └── Unauthorized - No Token (should fail).bru
```

## Scénarios de test

### 1. Authentification (4 tests)
- ✅ Enregistrement de deux joueurs (Alice & Bob)
- ✅ Login et récupération des tokens JWT
- ✅ Stockage automatique des tokens dans l'environnement

### 2. Partie d'échecs (5 tests)
- ✅ Création d'une partie entre deux joueurs
- ✅ Récupération de l'état de la partie
- ✅ Coups valides (e2→e4, e7→e5)
- ✅ Rejet de coup invalide (mauvais tour)
- ✅ Validation JWT sur les endpoints protégés

### 3. Matchmaking (6 tests)
- ✅ Joueur 1 rejoint la queue (WAITING)
- ✅ Vérification du statut (queue position)
- ✅ Rejet si déjà dans la queue (409 Conflict)
- ✅ Joueur 2 rejoint → match automatique
- ✅ Création automatique de la partie
- ✅ Vérification de l'autorisation (401 sans token)

## Variables d'environnement

Les variables suivantes sont automatiquement définies lors de l'exécution des tests :

| Variable | Description | Défini par |
|----------|-------------|------------|
| `baseUrl` | URL de l'API | Manuel (Local.bru) |
| `whiteToken` | Token JWT du joueur blanc | Login White Player |
| `blackToken` | Token JWT du joueur noir | Login Black Player |
| `whitePlayerId` | ID ULID du joueur blanc | Register White Player |
| `blackPlayerId` | ID ULID du joueur noir | Register Black Player |
| `gameId` | ID ULID de la partie créée | Create Game |

## Tests automatisés

Chaque requête Bruno contient des assertions (section `tests`) qui valident :
- Les codes de statut HTTP
- La structure des réponses JSON
- Les valeurs des champs
- La logique métier (tour de jeu, matchmaking, etc.)

**Exemples d'assertions** :
```javascript
test("Status is 200", function() {
  expect(res.status).to.equal(200);
});

test("Status is MATCHED", function() {
  expect(res.body.status).to.equal("MATCHED");
});
```

## Nettoyage

Pour repartir d'une base vierge :

```bash
# Arrêter l'application (Ctrl+C)

# Supprimer les données PostgreSQL
cd docker
docker compose down -v
rm -rf data/*

# Redémarrer
docker compose up -d
cd ..
./gradlew run
```

## Équivalence avec les tests Kotlin

Ces tests Bruno reproduisent fonctionnellement :
- `src/integrationTest/kotlin/com/gchess/chess/integration/GameE2ETest.kt`
- `src/integrationTest/kotlin/com/gchess/matchmaking/integration/MatchmakingE2ETest.kt`

La différence : Bruno permet de tester manuellement l'API en local, tandis que les tests Kotlin s'exécutent automatiquement avec Testcontainers dans la CI/CD.

## Dépannage

**Problème** : "Connection refused"
→ Vérifiez que l'application est bien démarrée sur le port 8080

**Problème** : "401 Unauthorized"
→ Exécutez d'abord les tests d'authentification pour générer les tokens

**Problème** : "409 Conflict" sur Create Game
→ Les joueurs n'existent pas, exécutez d'abord Register + Login

**Problème** : Tests échouent après plusieurs exécutions
→ Redémarrez l'application ou nettoyez la base de données
