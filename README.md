# Démonstration OAuth2 / OIDC

Ce projet illustre **deux flows OAuth2** distincts avec Keycloak comme Authorization Server :

1. **Client Credentials** (M2M) — entre `client-service` et `resource-server`.
2. **Authorization Code + OIDC** (utilisateur) — depuis `frontend-service` (Spring Boot + Thymeleaf + HTMX).

## Architecture

```
                          ┌─────────────────┐
                          │    Keycloak     │
                          │   (port 8080)   │
                          │  realm: demo    │
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
         │  Bearer token           │                         │  Bearer token
         ▼                         │                         ▼
              ┌──────────────────────────────────────┐
              │           resource-server            │
              │             (port 8081)              │
              │   valide les JWT via JWKS Keycloak   │
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

## Tests

### A) Flow Client Credentials (M2M)

```bash
curl http://localhost:8082/client/call
```

Réponse attendue :
```json
{
  "message": "Bonjour depuis le Resource Server !",
  "client": "service-account-demo-client",
  "scope": "openid profile email"
}
```

### B) Flow Authorization Code (OIDC, utilisateur)

1. Ouvrir <http://localhost:8083> dans un navigateur.
2. Cliquer sur **« Se connecter avec Keycloak »**.
3. Se connecter avec l'utilisateur de démo : `demo` / `demo`.
4. Sur la page d'accueil, utiliser les boutons HTMX pour :
   - afficher l'access token reçu,
   - appeler l'API protégée `resource-server` avec ce token.

### Endpoint public (sans token)

```bash
curl http://localhost:8081/api/public/hello
```

### Obtenir un token manuellement (Client Credentials, debug)

```bash
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=demo-client" \
  -d "client_secret=demo-secret" | jq .
```

## Structure du projet

```
prez_oauth2/
├── docker-compose.yml          # Keycloak
├── keycloak/
│   └── realm-demo.json         # Realm + clients + user demo (import au démarrage)
├── resource-server/            # API REST protégée (port 8081)
├── client-service/             # Client OAuth2 Client Credentials (port 8082)
└── frontend-service/           # Front Spring Boot + Thymeleaf + HTMX, OIDC Auth Code (port 8083)
```

## Clients Keycloak (realm `demo`)

| clientId          | Type          | Flow                  | Secret             |
|-------------------|---------------|-----------------------|--------------------|
| `demo-client`     | confidentiel  | Client Credentials    | `demo-secret`      |
| `frontend-client` | confidentiel  | Authorization Code    | `frontend-secret`  |

Utilisateur de démo : **`demo` / `demo`**.

## Concepts clés

| Concept | Description |
|---|---|
| **Client Credentials** | Flow OAuth2 M2M, sans utilisateur (service-account). |
| **Authorization Code** | Flow OAuth2/OIDC standard avec utilisateur, redirection navigateur et code échangé contre un token. |
| **OIDC** | Couche d'identité au-dessus d'OAuth2 : ID Token (JWT) décrivant l'utilisateur authentifié. |
| **Resource Server** | Valide le JWT via la clé publique de Keycloak (JWKS). |
| **HTMX** | Permet au front Thymeleaf de rafraîchir des fragments HTML via des appels AJAX déclaratifs, sans framework JS. |
| **RP-Initiated Logout** | Déconnexion côté Keycloak déclenchée par le frontend (`OidcClientInitiatedLogoutSuccessHandler`). |
