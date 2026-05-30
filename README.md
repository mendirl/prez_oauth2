# Démonstration OAuth2 / OIDC avec Keycloak, Spring Boot 4 & HTMX

Projet pédagogique multi-modules illustrant **deux flows OAuth2** et la **gestion fine des rôles**
sur une stack moderne : **Java 25**, **Spring Boot 4.0.5**, **Maven 4.1.0**, **JSpecify**
(null-safety vérifiée à la compilation via NullAway/ErrorProne), **Thymeleaf + HTMX**.

| Flow | Acteur | Module | Cas d'usage |
|------|--------|--------|-------------|
| **Client Credentials** (M2M) | service ↔ service | `client-service` → `resource-server` | API consommée par un backend sans utilisateur |
| **Authorization Code + PKCE + OIDC** | utilisateur ↔ navigateur | `frontend-service` → `resource-server` | Login web, rôles, UI conditionnelle |

---

## 1. Architecture

```
                          ┌─────────────────┐
                          │    Keycloak     │  realm: demo
                          │   :8080         │  rôles realm: ADMIN, USER
                          └────────┬────────┘
            client_credentials     │     authorization_code + PKCE
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          │                          ▼
┌─────────────────┐                │                ┌─────────────────┐
│  client-service │                │                │ frontend-service│
│      :8082      │                │                │      :8083      │
│  RestClient M2M │                │                │ Thymeleaf + HTMX│
└────────┬────────┘                │                └────────┬────────┘
         │  Bearer JWT             │                         │  Bearer JWT
         └───────────────► ┌─────────────────┐ ◄──────────────┘
                           │ resource-server │
                           │      :8081      │
                           │ JWT JWKS + RBAC │
                           └─────────────────┘
```

---

## 2. Prérequis

| Outil | Version |
|-------|---------|
| JDK | **25** (testé GraalVM 25.0.3) |
| Maven | **3.9+** ou **4.x** (modelVersion 4.1.0) |
| Docker / Docker Compose | récent |

---

## 3. Démarrage rapide

```bash
# 1. Keycloak (importe realm-demo.json au boot, ~30 s)
docker compose up -d
curl -fsS http://localhost:8080/realms/demo > /dev/null && echo "Keycloak OK"

# 2. Build complet (3 modules)
mvn clean package -DskipTests

# 3. Démarrer les 3 services (3 terminaux)
mvn -pl resource-server  spring-boot:run   # :8081
mvn -pl client-service   spring-boot:run   # :8082
mvn -pl frontend-service spring-boot:run   # :8083
```

> **Realm déjà importé ?** Relancer avec `docker compose down -v && docker compose up -d`
> pour réimporter les rôles/utilisateurs.

---

## 4. Utilisateurs & rôles (realm `demo`)

| Utilisateur | Mot de passe | Rôles | Ce qu'il voit sur `/home` |
|-------------|--------------|-------|---------------------------|
| `alice` | `alice` | `ADMIN`, `USER` | Espace USER **+** espace ADMIN (secret) |
| `bob`   | `bob`   | `USER`          | Espace USER ; zone ADMIN masquée + 403 sur API |
| `demo`  | `demo`  | `USER`          | Identique à `bob` |

UI conditionnelle via `sec:authorize="hasRole('ADMIN')"` (Thymeleaf Spring Security
extras). La page `/admin` est doublement protégée : `@PreAuthorize` **et** règle
`hasRole('ADMIN')` dans la `SecurityFilterChain`.

---

## 5. Endpoints

### `resource-server` (:8081)

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/api/public/hello`    | public                  | Pas de token |
| GET | `/api/message`         | authentifié             | Renvoie sub, roles, scope |
| GET | `/api/user/profile`    | `USER` ou `ADMIN`       | Zone utilisateur |
| GET | `/api/admin/dashboard` | `ADMIN`                 | Données sensibles |

Les rôles sont extraits du claim `realm_access.roles` du JWT Keycloak via un
`JwtAuthenticationConverter` qui les préfixe `ROLE_`.

### `client-service` (:8082)

| Méthode | Endpoint | Description |
|---|---|---|
| GET | `/client/call` | Récupère un token via Client Credentials puis appelle `/api/message`. |

### `frontend-service` (:8083)

| Route | Description |
|---|---|
| `/`              | Page d'accueil + bouton « Se connecter avec Keycloak » |
| `/home`          | Après login : claims, rôles, boutons HTMX |
| `/admin`         | Page réservée `ADMIN` |
| `/fragments/*`   | Fragments HTMX (`token`, `message`, `user`, `admin`) |
| `/logout`        | RP-Initiated Logout (Keycloak) |

---

## 6. Tests manuels

```bash
# Endpoint public (sans token)
curl http://localhost:8081/api/public/hello

# Flow M2M
curl http://localhost:8082/client/call | jq

# Flow utilisateur : ouvrir http://localhost:8083 dans 2 navigateurs privés
#   - alice/alice → voit les zones USER + ADMIN
#   - bob/bob     → voit USER, reçoit 403 sur /api/admin/dashboard
```

Récupérer un access token utilisateur en ligne de commande (activer
`directAccessGrantsEnabled` sur `frontend-client` si besoin) :

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d "grant_type=password" -d "username=alice" -d "password=alice" \
  -d "client_id=frontend-client" -d "client_secret=frontend-secret" | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/admin/dashboard
```

---

## 7. Clients Keycloak (realm `demo`)

| clientId          | Type         | Flow                  | Secret            | Redirect URI |
|-------------------|--------------|-----------------------|-------------------|--------------|
| `demo-client`     | confidentiel | Client Credentials    | `demo-secret`     | — |
| `frontend-client` | confidentiel | Authorization Code    | `frontend-secret` | `http://localhost:8083/login/oauth2/code/keycloak` |

---

## 8. Structure du projet

```
prez_oauth2/
├── pom.xml                          # parent : spring-boot-starter-parent 4.0.5, Java 25, nullability plugin
├── docker-compose.yml               # Keycloak 25.0.6
├── keycloak/realm-demo.json         # realm + rôles + clients + users
├── resource-server/                 # API REST protégée (:8081)
├── client-service/                  # Client OAuth2 M2M (:8082)
└── frontend-service/                # Front Thymeleaf + HTMX, OIDC (:8083)
```

Particularités Maven :
- `modelVersion` **4.1.0** sur tous les POMs ; `<subprojects>` (nouvelle syntaxe) à la place de `<modules>`.
- Héritage de `spring-boot-starter-parent` (BOM, plugin, encoding, java.version).
- `nullability-maven-plugin` 0.3.0 (extension) → configure ErrorProne 2.47.0 + NullAway 0.13.1
  en mode JSpecify pour tous les sous-projets, vérification null-safety à la compilation.

---

## 9. Concepts clés

| Concept | Description |
|---|---|
| **Client Credentials** | Flow OAuth2 sans utilisateur, service-account du client. |
| **Authorization Code + PKCE** | Flow standard OIDC : redirection navigateur → code → token, sécurisé par un `code_verifier`/`code_challenge` (S256). Activé côté Spring via `OAuth2AuthorizationRequestCustomizers.withPkce()` et exigé côté Keycloak via `pkce.code.challenge.method=S256`. |
| **OIDC** | Couche d'identité sur OAuth2 : ID Token JWT décrivant l'utilisateur. |
| **JWKS** | Le resource-server valide les JWT via la clé publique exposée par Keycloak. |
| **`realm_access.roles`** | Rôles realm Keycloak, mappés en `ROLE_*` côté Spring. |
| **`sec:authorize`** | Attribut Thymeleaf qui rend conditionnellement selon `hasRole(...)`. |
| **HTMX** | Le front recharge des fragments HTML sans JS, via `hx-get` / `hx-target`. |
| **RP-Initiated Logout** | Logout déclenché par le frontend, propagé à Keycloak. |
| **JSpecify `@NullMarked`** | Non-null par défaut au niveau package ; `@Nullable` localement. |
| **NullAway** | Vérifie statiquement la null-safety à la compilation (échec = build cassé). |

---

## 10. Dépannage

| Symptôme | Cause probable | Remède |
|---|---|---|
| `Connection refused :8080` | Keycloak pas prêt | Attendre 30 s, vérifier `docker logs keycloak` |
| Login OK mais pas de badge ADMIN | Realm pas réimporté | `docker compose down -v && up -d` |
| `401 invalid_token` sur `/api/*` | Token expiré ou mauvais issuer | Vérifier `issuer-uri` dans `application.yml` |
| `403` sur `/api/admin/**` avec alice | Realm non importé / mapping rôles KO | Inspecter le JWT sur https://jwt.io |
| Build cassé NullAway | Violation de nullité | Annoter `@Nullable` ou gérer le `null` explicitement |
