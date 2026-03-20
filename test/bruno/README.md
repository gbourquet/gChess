# gChess API Tests — Collection Bruno

Collection de tests end-to-end pour l'API gChess. Les tests HTTP s'exécutent en séquence ; les tests WebSocket (matchmaking, jeu) s'utilisent manuellement depuis l'interface Bruno.

## Prérequis

1. **Bruno** installé ([usebruno.com](https://www.usebruno.com/))
2. Base de données démarrée :
   ```bash
   cd docker && docker compose up -d
   ```
3. Application démarrée :
   ```bash
   ./gradlew run
   ```

## Ouverture dans Bruno

1. Lancer Bruno
2. **Open Collection** → sélectionner `test/bruno`
3. Sélectionner l'environnement **"Local"** en haut à droite

## Structure de la collection

```
test/bruno/
├── environments/
│   └── Local.bru                          # Variables d'environnement
├── 1. Auth/                               # Tests HTTP — authentification
│   ├── Register White Player.bru  (seq 1)
│   ├── Login White Player.bru     (seq 2)
│   ├── Register Black Player.bru  (seq 3)
│   └── Login Black Player.bru     (seq 4)
├── 2. Game/                               # Tests WebSocket — partie de jeu
│   ├── Create Game.bru            (seq 1)  ← doc + prérequis
│   ├── Get Game.bru               (seq 2)  ← Connect White (GameStateSync)
│   ├── Connect - Black Player.bru (seq 3)
│   ├── Connect - Invalid Token.bru (seq 4)
│   ├── Move 1 - White e2 to e4.bru (seq 5)
│   ├── Move 2 - Black e7 to e5.bru (seq 6)
│   ├── Move 3 - Invalid Turn.bru  (seq 7)
│   ├── Move - Promotion.bru       (seq 8)
│   ├── Resign - White.bru         (seq 9)
│   ├── Draw - Offer (White).bru   (seq 10)
│   ├── Draw - Accept (Black).bru  (seq 11)
│   ├── Draw - Reject (White).bru  (seq 12)
│   └── Claim Timeout.bru          (seq 13)
├── 3. Matchmaking/                        # Tests WebSocket — matchmaking
│   ├── Connect to matchmaking - Player 1.bru
│   ├── Connect to matchmaking - Player 2.bru
│   ├── Connect to matchmaking with bad token.bru
│   └── ws/                               # Payloads de référence
│       ├── Connect to Matchmaking.bru
│       └── Send JoinQueue.bru
└── 4. History/                            # Tests HTTP — historique
    ├── Get My Games.bru           (seq 1)
    ├── Get My Games - Unauthorized.bru (seq 2)
    ├── Get Game Moves.bru         (seq 3)
    ├── Get Game Moves - Black Player.bru (seq 4)
    ├── Get Game Moves - Not Found.bru (seq 5)
    └── Get Game Moves - Forbidden.bru (seq 6)
```

## Variables d'environnement

| Variable | Définie par | Description |
|---|---|---|
| `baseUrl` | `Local.bru` (fixe) | `http://localhost:8080` |
| `whiteToken` | `1. Auth / Login White Player` | JWT du joueur blanc |
| `blackToken` | `1. Auth / Login Black Player` | JWT du joueur noir |
| `thirdToken` | Manuel | JWT d'un troisième utilisateur (test Forbidden) |
| `whitePlayerId` | `1. Auth / Register White Player` | UserId du joueur blanc |
| `blackPlayerId` | `1. Auth / Register Black Player` | UserId du joueur noir |
| `gameId` | `3. Matchmaking` (WS) **ou** `4. History / Get My Games` | Id de la partie |

## Exécution des tests

### Dossiers HTTP (automatisables)

Clic droit sur un dossier → **Run** pour exécuter toutes les requêtes en séquence.

**Ordre recommandé :**
1. `1. Auth` → génère les tokens et les playerIds
2. `3. Matchmaking` (manuellement, voir ci-dessous) → génère `gameId`
3. `4. History` → utilise `gameId` (ou le set depuis la réponse)

### Dossiers WebSocket (manuels)

Les requêtes `type: ws` ne peuvent pas être lancées via le runner. Pour chaque fichier :
1. Clic sur la requête
2. Bouton **Connect**
3. Observer les messages dans le panneau droit
4. Les requêtes avec `body:ws` envoient leur message automatiquement à la connexion

**Flow matchmaking pour obtenir un `gameId` :**
1. Ouvrir `3. Matchmaking / Connect to matchmaking - Player 1` → Connect → `{"type": "JoinQueue"}`
2. Ouvrir `3. Matchmaking / Connect to matchmaking - Player 2` → Connect → `{"type": "JoinQueue"}`
3. Les deux reçoivent `MatchFound` avec le `gameId` → le noter dans l'environnement Local

### Dossier `4. History` en autonomie

`Get My Games` set automatiquement `gameId` depuis le premier résultat de la liste, ce qui permet d'enchaîner directement `Get Game Moves` sans passer par le matchmaking.

## Scénarios de test

### `1. Auth` — 4 tests HTTP
- Enregistrement de deux joueurs (alice, bob)
- Login → stockage automatique de `whiteToken`, `blackToken`, `whitePlayerId`, `blackPlayerId`

### `2. Game` — 13 requêtes WebSocket
- Connexion en tant que joueur blanc ou noir (réception `GameStateSync`)
- Rejet d'authentification avec token invalide (`GameAuthFailed`)
- Coups valides (`MoveExecuted` broadcast)
- Coup refusé au mauvais tour (`MoveRejected`)
- Promotion de pion
- Abandon (`GameResigned`)
- Nulle — proposition, acceptation, refus
- Claim de timeout Fischer

### `3. Matchmaking` — 3 requêtes WebSocket
- Connexion Player 1 → `JoinQueue` → `QueuePositionUpdate`
- Connexion Player 2 → `JoinQueue` → `MatchFound` (les deux)
- Token invalide → `AuthFailed`

### `4. History` — 6 tests HTTP
- Liste des parties du joueur authentifié (200 + set `gameId`)
- Accès sans token → 401
- Liste des coups d'une partie (200, joueur blanc et noir)
- Partie inconnue → 404
- Non-participant → 403

## Nettoyage entre exécutions

```bash
cd docker
docker compose down -v
docker compose up -d
```

Puis relancer l'application (`./gradlew run`).

## Dépannage

| Problème | Cause | Solution |
|---|---|---|
| `Connection refused` | Application non démarrée | `./gradlew run` |
| `401 Unauthorized` | Tokens absents | Exécuter `1. Auth` en premier |
| `404` sur moves ou history | `gameId` absent | Exécuter matchmaking WS ou `4. History / Get My Games` |
| `403` sur Get Game Moves - Forbidden | `thirdToken` non défini | Enregistrer un troisième utilisateur et copier son token dans l'env |
| Tests History échouent | Aucune partie en base | Créer une partie via matchmaking WS |
