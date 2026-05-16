# Démonstration OAuth2 / OIDC

Ce projet illustre **deux flows OAuth2** distincts avec Keycloak comme Authorization Server,
ainsi que la **gestion des rôles** et l'affichage conditionnel selon les droits de l'utilisateur :

1. **Client Credentials** (M2M) — entre `client-service` et `resource-server`.
2. **Authorization Code + OIDC** (utilisateur) — depuis `frontend-service` (Spring Boot + Thymeleaf + HTMX),
   avec UI différenciée selon les rôles Keycloak.

## Architecture

```
                          ┌─────────────────┐
                          │    Keycloak     │
                          │   (port 8080)   │
                          │  realm: demo    │
                          │ rôles: ADMIN /  │
                          │        USER     │
                          └────────┬────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        │ Client Credentials       │      Authorization Code  │
        ▼                          │                          ▼
┌─────────────────┐                │                ┌─────────────────┐
│  client-service │                │                │ frontend-service│
│   (port 8082)   │                │                │   (port 8083)   │
│  flow M2M       │                │                │  Thymeleaf+HTMX │
└────────┬────────┘                │                └────────┬────────┘
         │                         │                         │
         │  Bearer token           │                         │  Bearer token (+ rôles)
         ▼                         │                         ▼
              ┌──────────────────────────────────────┐
              │           resource-server            │
              │             (port 8081)              │
              │   valide les JWT via JWKS Keycloak   │
              │   /api/admin/**  → hasRole('ADMIN')  │
              │   /api/user/**   → hasRole('USER')   │
              └──────────────────────────────────────┘
```

## Prérequis

- Java 25
- Maven 3.9+
- Docker & Docker Compose

## Lancement

### 1. Démarrer Keycloak

```bash
docker compose up -d
```

> Si Keycloak tournait déjà avant ces changements, relancez-le pour réimporter
> le realm avec les nouveaux rôles et utilisateurs :
> ```bash
> docker compose down -v && docker compose up -d
> ```

Attendre que Keycloak soit prêt (~30 s) :
```bash
curl http://localhost:8080/realms/demo
```

### 2. Compiler le projet

```bash
mvn clean package -DskipTests
```

### 3. Démarrer les services (3 terminaux)

```bash
# Terminal 1 — Resource Server (8081)
cd resource-server && mvn spring-boot:run

# Terminal 2 — Client Service M2M (8082)
cd client-service && mvn spring-boot:run

# Terminal 3 — Frontend OIDC (8083)
cd frontend-service && mvn spring-boot:run
```

## Utilisateurs & rôles (realm `demo`)

| Utilisateur | Mot de passe | Rôles realm     | Ce que voit l'utilisateur                                |
|-------------|--------------|-----------------|----------------------------------------------------------|
| `alice`     | `alice`      | `ADMIN`, `USER` | Espace utilisateur **+** espace administrateur + secret  |
| `bob`       | `bob`        | `USER`          | Espace utilisateur uniquement, message « accès refusé » sur la zone admin |
| `demo`      | `demo`       | `USER`          | Idem `bob` (compatibilité historique)                    |

L'interface (`home.html`) utilise `sec:authorize="hasRole('ADMIN')"` (Thymeleaf
Spring Security extras) pour afficher/masquer les sections.
La page `/admin` est protégée par `@PreAuthorize("hasRole('ADMIN')")` côté
contrôleur **et** par `hasRole('ADMIN')` côté chaîne de sécurité HTTP.

## Tests

### A) Flow Client Credentials (M2M)

```bash
curl http://localhost:8082/client/call
```

Le service-account du client `demo-client` n'a pas de rôle utilisateur, l'endpoint
appelé est `/api/message` (authentifié simple).

### B) Flow Authorization Code (OIDC, utilisateur)

1. Ouvrir <http://localhost:8083> dans un navigateur (privé, ou un par utilisateur).
2. Cliquer sur **« Se connecter avec Keycloak »**.
3. Se connecter, par exemple avec `alice` / `alice` puis comparer avec `bob` / `bob`.
4. Sur `/home`, observer :
   - les **rôles** affichés (badge `ADMIN` ou `USER`),
   - l'espace utilisateur (vert) accessible à tous,
   - l'espace administrateur (orange) visible **uniquement** pour `alice`,
   - pour `bob` : message « 🚫 Accès refusé » sur la zone admin.
5. Cliquer sur les boutons HTMX pour appeler le `resource-server` :
   - `/api/user/profile`  → OK pour tous,
   - `/api/admin/dashboard` → 200 pour `alice`, **403** pour `bob`,
   - `/api/message`       → OK pour tous.

### Endpoint public (sans token)

```bash
curl http://localhost:8081/api/public/hello
```

### Tester directement les endpoints avec un token (Password Grant désactivé)

Activez si besoin `directAccessGrantsEnabled: true` sur `frontend-client`
pour pouvoir récupérer un token utilisateur via :

```bash
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d "grant_type=password" -d "username=alice" -d "password=alice" \
  -d "client_id=frontend-client" -d "client_secret=frontend-secret" | jq -r .access_token
```

## Structure du projet

```
prez_oauth2/
├── docker-compose.yml          # Keycloak
├── keycloak/
│   └── realm-demo.json         # Realm + rôles + clients + users (import au démarrage)
├── resource-server/            # API REST protégée (port 8081)
├── client-service/             # Client OAuth2 Client Credentials (port 8082)
└── frontend-service/           # Front Spring Boot + Thymeleaf + HTMX, OIDC Auth Code (port 8083)
```

## Endpoints clés du `resource-server`

| Endpoint                  | Accès                          |
|---------------------------|--------------------------------|
| `GET /api/public/hello`   | Public, pas de token requis    |
| `GET /api/message`        | Authentifié (n'importe quel JWT) |
| `GET /api/user/profile`   | `hasRole('USER')` ou `ADMIN`   |
| `GET /api/admin/dashboard`| `hasRole('ADMIN')` uniquement  |

Les rôles sont extraits du claim `realm_access.roles` du JWT Keycloak via un
`JwtAuthenticationConverter` qui les préfixe `ROLE_` pour Spring Security.

## Clients Keycloak (realm `demo`)

| clientId          | Type          | Flow                  | Secret             |
|-------------------|---------------|-----------------------|--------------------|
| `demo-client`     | confidentiel  | Client Credentials    | `demo-secret`      |
| `frontend-client` | confidentiel  | Authorization Code    | `frontend-secret`  |

## Concepts clés

| Concept | Description |
|---|---|
| **Client Credentials** | Flow OAuth2 M2M, sans utilisateur (service-account). |
| **Authorization Code** | Flow OAuth2/OIDC standard avec utilisateur, redirection navigateur et code échangé contre un token. |
| **OIDC** | Couche d'identité au-dessus d'OAuth2 : ID Token (JWT) décrivant l'utilisateur authentifié. |
| **Resource Server** | Valide le JWT via la clé publique de Keycloak (JWKS) et fait l'autorisation basée sur les rôles. |
| **Rôles Keycloak**     | Présents dans `realm_access.roles` de l'access token ; mappés en `ROLE_*` côté Spring. |
| **HTMX** | Permet au front Thymeleaf de rafraîchir des fragments HTML via des appels AJAX déclaratifs. |
| **`sec:authorize`** | Attribut Thymeleaf (extras Spring Security) qui affiche/masque un bloc HTML selon le rôle courant. |
| **RP-Initiated Logout** | Déconnexion côté Keycloak déclenchée par le frontend (`OidcClientInitiatedLogoutSuccessHandler`). |
