# Auth & Identity Service — Phase 1 Implementation Guide

**Project:** Auth & Identity Service (standalone)
**Goal:** Learn JWT + Refresh Token + OAuth2 + 2FA before integrating into Wallet
**Tech Stack:** Spring Boot 3.3 | MySQL 8 | Redis 7 | Java 21
**Learning Order:** Module 1 → 2 → 3 → 4 (M3 and M4 can be done in parallel)

---

## 1. Overview

### 1.1 Why Learn Standalone First?

When integrating Auth into an existing project, you encounter hard-to-debug errors because you don't understand the internal mechanism. Learning standalone allows you to: (1) control the entire flow, (2) debug step by step without being confused by business code, (3) understand exactly why everything works before combining them.

### 1.2 Module Dependency Table

| Module | Name | Main Content | Key Libraries | Depends On |
|--------|------|--------------|---------------|------------|
| M1 | JWT Basic | Register, login, JwtFilter, protect endpoints | JJWT, Spring Security 6 | Required first |
| M2 | Refresh Token | Short-lived access token, long-lived refresh token, Redis blacklist | Redis, Spring Data Redis | Needs M1 done |
| M3 | OAuth2 Google | Social login, bind account, issue JWT | Spring OAuth2 Client | Independent, needs M1 |
| M4 | TOTP 2FA | Google Authenticator, QR code, 2-step login | google-auth library | Needs M1+M2 |

### 1.3 Environment Setup

| Tool | Source | Notes |
|------|--------|-------|
| JDK 21 | oracle.com/java or sdkman | Check: `java -version` |
| Maven 3.9+ | maven.apache.org | Check: `mvn -version` |
| MySQL 8 | dev.mysql.com or Docker | `docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8` |
| Redis 7 | redis.io or Docker | `docker run -d -p 6379:6379 redis:7` |
| IntelliJ IDEA | jetbrains.com (Community OK) | Plugins: Spring Boot, Lombok |
| Postman | postman.com | For manual API testing |
| TablePlus / DBeaver | Optional | View MySQL and Redis data |

### 1.4 Project Structure (Spring Initializr)

- **Group:** com.fintech | **Artifact:** auth-service | **Java 21** | **Maven**
- **Dependencies:** Spring Web, Spring Data JPA, Spring Security, MySQL Driver, Lombok, Validation
- **Add manually to pom.xml:** jjwt-api, jjwt-impl, jjwt-jackson (M1), spring-boot-starter-data-redis (M2)
- **Package structure:** `controller / service / impl / repository / domain / dto / config / filter / exception`

### 1.5 Database Creation

See [`src/main/resources/auth_db.sql`](../src/main/resources/auth_db.sql) for the full schema.

---

## 2. Module 1 — JWT Basic: Register / Login / JwtFilter

### 2.1 Module Objectives

- Understand JWT structure: `header.payload.signature` and how to sign with HMAC-SHA256
- Build Register and Login flows that return a JWT
- Write JwtFilter: every request to `/api/**` must have a valid Bearer token
- Understand why Spring Security needs `UserDetailsService` and how it works

### 2.2 Dependencies (pom.xml)

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

### 2.3 Database Schema — `users` table

| Column | Type / Constraint | Notes |
|--------|-------------------|-------|
| id | BIGINT AUTO_INCREMENT | PRIMARY KEY |
| email | VARCHAR(100) UNIQUE NOT NULL | Used as login username |
| password_hash | VARCHAR(255) NOT NULL | BCrypt hash, NEVER store plain text |
| full_name | VARCHAR(100) | |
| role | ENUM('USER','ADMIN') DEFAULT 'USER' | |
| enabled | BOOLEAN DEFAULT TRUE | False when locked |
| created_at | DATETIME NOT NULL | |

> Full SQL (incremental + complete schema): [`src/main/resources/auth_db.sql`](../src/main/resources/auth_db.sql)

### 2.4 Implementation Steps

| Step | Component | Description |
|------|-----------|-------------|
| B1 | User entity + Repository | Create `User` entity mapped to `users` table. `UserRepository extends JpaRepository`. Method: `Optional<User> findByEmail(String email)`. |
| B2 | PasswordEncoder Bean | Create `@Bean BCryptPasswordEncoder` in `SecurityConfig`. Do NOT create `UserDetailsService` before this step to avoid circular dependency. |
| B3 | UserDetailsService | Implement `UserDetailsService.loadUserByUsername(email)`: find User in DB, return `UserDetails`. If not found → throw `UsernameNotFoundException`. |
| B4 | JwtUtil | `@Component` containing: `SECRET_KEY` (min 256-bit), `EXPIRATION_MS` (900000 = 15 min). Methods: `generateToken(UserDetails)`, `extractUsername(token)`, `isTokenValid(token, UserDetails)`. |
| B5 | JwtFilter | extends `OncePerRequestFilter`. Logic: (1) Get Authorization header, (2) Extract Bearer token, (3) extractUsername, (4) load UserDetails, (5) isTokenValid → set SecurityContext, (6) filterChain.doFilter(). |
| B6 | SecurityFilterChain | Config: disable CSRF (REST API), sessionManagement = STATELESS, permit `/auth/**`, protect `/api/**`. Add jwtFilter before `UsernamePasswordAuthenticationFilter`. |
| B7 | AuthController — Register | `POST /auth/register`: receive `RegisterRequest(email, password, fullName)`. Check if email exists. Hash password. Save User. Return 201 + basic info (NO password). |
| B8 | AuthController — Login | `POST /auth/login`: use `AuthenticationManager.authenticate()`. If wrong → throw 401. If correct → `generateToken(userDetails)` → return `LoginResponse{token, expiresIn}`. |
| B9 | Test protected endpoint | Create `GET /api/hello` returning "Hello {email}". Call without token → 403. Call with token → 200 + username. |

### 2.5 application.yml for Module 1

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/auth_db?createDatabaseIfNotExist=true
    username: root
    password: root
  jpa:
    hibernate.ddl-auto: update
    show-sql: true
app:
  jwt:
    secret: "a-secret-string-at-least-256-bit-to-sign-HS256-do-not-use-in-production"
    expiration-ms: 900000  # 15 minutes
```

### 2.6 Test Cases — Module 1

| ID | Description | Input / Condition | Expected Result |
|----|-------------|-------------------|-----------------|
| TC-1-01 | Successful register | `POST /auth/register {email, password, fullName}` | HTTP 201, UserResponse without password |
| TC-1-02 | Duplicate email register | Call twice with same email | HTTP 409 "Email already exists" |
| TC-1-03 | Invalid email format | `email = "not-an-email"` | HTTP 400 validation error |
| TC-1-04 | Correct login | `POST /auth/login {email, password}` | HTTP 200, LoginResponse with token string |
| TC-1-05 | Wrong password login | `password = "wrong"` | HTTP 401 "Invalid credentials" |
| TC-1-06 | Call /api/hello without token | `GET /api/hello` (no Authorization header) | HTTP 403 Forbidden |
| TC-1-07 | Call /api/hello with valid token | `Authorization: Bearer <token>` | HTTP 200 "Hello user@example.com" |
| TC-1-08 | Call /api/hello with expired token | Token expired after 15 min | HTTP 403 with error message in body |
| TC-1-09 | Call /api/hello with forged token | Token modified by 1 character | HTTP 403 (signature invalid) |

> **Key Learning Point M1:** After writing, open the generated JWT, decode at jwt.io. Verify: `header.alg = "HS256"`, payload has `sub` (email), `iat`, `exp`. Understand all 3 parts before moving to M2.

---

## 3. Module 2 — Refresh Token: Rotation and Redis Blacklist

### 3.1 Module Objectives

- Understand why short-lived access tokens (15 min) don't require re-login every 15 minutes
- Build refresh token mechanism — 7 days stored in Redis
- Implement Token Rotation: each refresh → issue new token, revoke old one
- Logout: revoke refresh token → fully logged out
- Blacklist access tokens that are still valid but need emergency revocation

### 3.2 Additional Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 3.3 Redis Data Structure

| Redis Key Pattern | Type | TTL | Purpose |
|-------------------|------|-----|---------|
| `refresh:{userId}` | String | 7 days | Store current refresh token for user. Keyed by userId for easy revocation. |
| `blacklist:{jti}` | String | Remaining access token lifetime | Blacklist revoked access tokens. `jti` = JWT ID claim. |
| `locked:{email}` | String | 15 min | Extension: count failed login attempts (used in M4/integration). |

> **Why key by userId instead of token?** If keyed by token, when user changes password or logs out from all devices, you only need to delete 1 Redis key `refresh:{userId}` to revoke immediately, without knowing what the token is.

### 3.4 Implementation Steps

| Step | Component | Description |
|------|-----------|-------------|
| B1 | Add jti to JWT | In `generateToken()`: add `.id(UUID.randomUUID().toString())` → each token has unique jti for blacklisting. |
| B2 | RefreshTokenService | `generateRefreshToken(userId)`: create UUID, save to Redis key `"refresh:{userId}"`, TTL 7 days, return token string. |
| B3 | Update Login response | After successful login: call `generateRefreshToken(user.getId())`. Return `LoginResponse{accessToken, refreshToken, expiresIn:900}`. |
| B4 | `POST /auth/refresh` endpoint | Receive `{refreshToken}`. (1) Find userId from Redis. (2) Verify token matches. (3) Delete old token. (4) Create new access token + refresh token. (5) Return response. |
| B5 | Token Rotation | In refresh step: `deleteByKey("refresh:{userId}")`, then `generateRefreshToken(userId)` → save new token. Old refresh token is no longer valid. |
| B6 | `POST /auth/logout` endpoint | Receive Authorization header. (1) Extract jti from access token. (2) Calculate remaining TTL. (3) Set Redis `"blacklist:{jti}" = "revoked"` with that TTL. (4) Delete `"refresh:{userId}"`. (5) Return 200. |
| B7 | Update JwtFilter | After `isTokenValid()`: also check `Redis.hasKey("blacklist:{jti}")`. If exists → token is revoked → return 403 even if not yet expired. |
| B8 | application.yml additions | `spring.data.redis.host=localhost`, `spring.data.redis.port=6379`. `app.jwt.refresh-expiration-ms=604800000` (7 days). |

### 3.5 Test Cases — Module 2

| ID | Description | Input / Condition | Expected Result |
|----|-------------|-------------------|-----------------|
| TC-2-01 | Login returns both access + refresh token | `POST /auth/login` | HTTP 200, has both `accessToken` and `refreshToken` |
| TC-2-02 | Valid refresh token | `POST /auth/refresh {refreshToken}` | HTTP 200, new accessToken + new refreshToken (different from old) |
| TC-2-03 | Old refresh token after rotation | Use old refreshToken on `/auth/refresh` a 2nd time | HTTP 401 "Refresh token invalid" |
| TC-2-04 | Successful logout | `POST /auth/logout` with Bearer token | HTTP 200. Call `/api/hello` with old token → 403 |
| TC-2-05 | Blacklisted access token | Logout, then call `/api/hello` with the same token | HTTP 403 even though token not yet expired |
| TC-2-06 | Refresh token after logout | Logout, then call `/auth/refresh` with old refreshToken | HTTP 401 (refresh token deleted from Redis) |
| TC-2-07 | Redis down: does login still work? | Stop Redis, try login | HTTP 500 or degraded (depends on config). Note behavior. |

---

## 4. Module 3 — OAuth2 Google Login: Social Login and JWT Issue

### 4.1 Module Objectives

- Allow login with Google account without registration
- Understand OAuth2 Authorization Code flow: redirect → Google → callback → JWT
- Handle bind account: if Google email already in DB → link to existing account
- Issue JWT after OAuth2 success, return to frontend like normal login

### 4.2 Google Cloud Console Setup

| Step | Action |
|------|--------|
| B1 | Create project: go to console.cloud.google.com → New Project → name "auth-service-dev" |
| B2 | Enable API: APIs & Services → Enable APIs → search "Google+ API" or "Google Identity" → Enable |
| B3 | OAuth consent: OAuth consent screen → External → fill App name, email → Save |
| B4 | Create credentials: Credentials → Create Credentials → OAuth 2.0 Client IDs → Web application |
| B5 | Redirect URI: Add to Authorized redirect URIs: `http://localhost:8080/login/oauth2/code/google` |
| B6 | Get keys: Copy Client ID and Client Secret → add to application.yml (DO NOT commit to Git) |

### 4.3 Dependency and application.yml

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: email, profile
```

### 4.4 Implementation Steps

| Step | Component | Description |
|------|-----------|-------------|
| B1 | Update User entity | Add fields: `googleId VARCHAR(100) UNIQUE NULL`, `authProvider ENUM("LOCAL","GOOGLE") DEFAULT "LOCAL"`. |
| B2 | CustomOAuth2UserService | implements `OAuth2UserService`. Get email from attributes. Find User by email in DB. If not found → create new User with `authProvider=GOOGLE`. If found but `authProvider=LOCAL` → throw "Email already registered with password". |
| B3 | OAuth2AuthenticationSuccessHandler | implements `AuthenticationSuccessHandler`. Get `CustomUserDetails` from authentication. Call `generateToken()` → create JWT. Redirect to frontend URL with token: `http://localhost:3000/oauth/callback?token=xxx` |
| B4 | OAuth2AuthenticationFailureHandler | implements `AuthenticationFailureHandler`. Redirect to `http://localhost:3000/login?error=oauth_failed`. |
| B5 | Update SecurityFilterChain | `.oauth2Login(oauth2 -> oauth2.userInfoEndpoint(...).successHandler(...).failureHandler(...))` |
| B6 | Manual test | Open browser: `http://localhost:8080/oauth2/authorization/google` → will redirect to Google login → after login → redirect to localhost:3000 with token. |
| B7 | Handle token at "frontend" | In this scope, use Postman or create simple HTML to capture token from URL param. Parse JWT at jwt.io to verify payload. |

### 4.5 Test Cases — Module 3

| ID | Description | Input / Condition | Expected Result |
|----|-------------|-------------------|-----------------|
| TC-3-01 | First-time Google login (no account) | Browser → `/oauth2/authorization/google` → select account | Redirect to frontend with token. DB has new user with `authProvider=GOOGLE` |
| TC-3-02 | Second Google login (account exists) | Same Google account, login again | Redirect with token. DB does not create another user |
| TC-3-03 | OAuth2 JWT works with /api/** | Use OAuth2 token to call `GET /api/hello` | HTTP 200, returns Google user's name |
| TC-3-04 | Google email conflicts with LOCAL account | Register LOCAL first, then Google login with same email | HTTP 400 "Email already registered with password" |
| TC-3-05 | Direct access to `/login/oauth2/code/google` | `GET /login/oauth2/code/google` (bypassing Google) | HTTP 400 or error redirect (no state param) |

---

## 5. Module 4 — TOTP 2FA: Google Authenticator and 2-Step Login

### 5.1 Module Objectives

- Understand TOTP: Time-based One-Time Password, how 6 digits are computed every 30 seconds
- Allow users to optionally enable/disable 2FA (not mandatory for all)
- Implement 2-step login: step 1 verify password, step 2 verify TOTP code
- Generate backup codes for use when device is lost

### 5.2 How TOTP Works

TOTP (RFC 6238) is based on the formula: `OTP = HOTP(secret, T)` where `T = floor(unix_time / 30)`. Both server and app have the same secret → compute the same OTP at the same time. Secret is Base32-encoded, delivered to app via QR code (`otpauth://` URI). Server accepts ±1 window (30 seconds) to compensate for clock skew.

### 5.3 Dependencies

```xml
<!-- TOTP library -->
<dependency>
    <groupId>com.warrenstrange</groupId>
    <artifactId>googleauth</artifactId>
    <version>1.5.0</version>
</dependency>
<!-- QR code generation -->
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.3</version>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>3.5.3</version>
</dependency>
```

### 5.4 Additional Database Columns

| Column | Type | Purpose |
|--------|------|---------|
| totp_secret | VARCHAR(100) NULL | Base32 secret for TOTP, NULL if 2FA not enabled |
| totp_enabled | BOOLEAN DEFAULT FALSE | Flag whether 2FA is activated |
| backup_codes | TEXT NULL | JSON array of 8 backup codes, BCrypt-hashed |

> SQL: see Module 4 section in [`src/main/resources/auth_db.sql`](../src/main/resources/auth_db.sql)

### 5.5 Implementation Steps

| Step | Component | Description |
|------|-----------|-------------|
| B1 | `POST /auth/2fa/setup` | User calls (needs Bearer token). Server: (1) Create `GoogleAuthenticator`, call `ga.createCredentials()` for secret. (2) Save secret to `user.totpSecret` (not yet enabled). (3) Create QR URL: `otpauth://totp/AuthService:{email}?secret={base32}&issuer=AuthService`. (4) Use ZXing to write QR URL as PNG base64. (5) Return `{qrCodeBase64, secret}`. |
| B2 | `POST /auth/2fa/verify-setup` | User enters 6-digit code from app after scanning QR. Server: (1) Get secret from DB. (2) `GoogleAuthenticator.authorize(secret, code)`. (3) If correct → set `totp_enabled=true`. (4) Generate 8 random backup codes, hash with BCrypt, save JSON to `backup_codes`. (5) Return backup codes list to user (show only once). |
| B3 | Update Login flow (2 steps) | Step 1: `POST /auth/login` → verify password → if `totp_enabled=false`: return full JWT as before. If `totp_enabled=true`: return `PartialToken{partialToken, requiresTwoFactor:true}`. PartialToken is a short-lived JWT (5 min) with only claim `"stage":"partial"`. |
| B4 | `POST /auth/2fa/validate` | Receive `{partialToken, code}`. (1) Verify partialToken and get email. (2) Find user, get secret. (3) Check code via `GoogleAuthenticator.authorize()` or backup code. (4) If correct → issue full JWT + refresh token. If wrong → 401. |
| B5 | JwtFilter distinguish partial token | In JwtFilter: if token has claim `stage="partial"` → only allow access to `/auth/2fa/**`. Calling `/api/**` with partial token → 403 "2FA not completed". |
| B6 | Backup code logic | When user enters backup code: scan the list, use `BCrypt.matches(input, hash)`. If match → remove that code from list, update DB. Each backup code can only be used once. |
| B7 | `POST /auth/2fa/disable` | Requires Bearer token and re-confirmation of password. Clear `totpSecret`, set `totp_enabled=false`, clear `backup_codes`. |

### 5.6 Test Cases — Module 4

| ID | Description | Input / Condition | Expected Result |
|----|-------------|-------------------|-----------------|
| TC-4-01 | Setup 2FA successfully | `POST /auth/2fa/setup` with Bearer token | HTTP 200, returns `qrCodeBase64` and `secret`. DB stores secret but `totp_enabled=false` |
| TC-4-02 | Verify 2FA setup with correct code | `POST /auth/2fa/verify-setup {code: code from app}` | HTTP 200, `totp_enabled=true` in DB, returns 8 backup codes |
| TC-4-03 | Verify 2FA setup with wrong code | `POST /auth/2fa/verify-setup {code: "000000"}` | HTTP 401 "Invalid authentication code" |
| TC-4-04 | Login when 2FA not enabled | `POST /auth/login` (user hasn't enabled 2FA) | HTTP 200, returns full JWT as before (no partial token) |
| TC-4-05 | Login when 2FA enabled (step 1) | `POST /auth/login` (user has enabled 2FA) | HTTP 200, returns `{partialToken, requiresTwoFactor:true}` |
| TC-4-06 | Call /api/hello with partial token | `Authorization: Bearer <partial_token>` | HTTP 403 "2FA not completed" |
| TC-4-07 | Validate 2FA with correct code (step 2) | `POST /auth/2fa/validate {partialToken, code}` | HTTP 200, returns full JWT + refreshToken |
| TC-4-08 | Use backup code instead of TOTP | `POST /auth/2fa/validate {code: backup_code}` | HTTP 200, backup code removed from list (single use) |
| TC-4-09 | Reuse already-used backup code | Use same backup code a 2nd time | HTTP 401 (code no longer valid) |
| TC-4-10 | Disable 2FA | `POST /auth/2fa/disable` with password confirmation | HTTP 200, `totp_enabled=false`, secret cleared |

---

## 6. End-to-End Testing and Checklist

### 6.1 Full End-to-End Flow

After completing all 4 modules, run this flow to verify the system works correctly:

| # | Step | Description |
|---|------|-------------|
| L1 | Register LOCAL | `POST /auth/register` → 201, user in DB with password hash |
| L2 | Login → get token | `POST /auth/login` → accessToken + refreshToken |
| L3 | Call API | `GET /api/hello` with Bearer accessToken → 200 + username |
| L4 | Refresh token | `POST /auth/refresh` → new accessToken, new refreshToken |
| L5 | Setup 2FA | `POST /auth/2fa/setup` → scan QR, verify, receive backup codes |
| L6 | 2-step login | Login → partial token → validate TOTP → full JWT |
| L7 | OAuth2 login | Browser → Google → callback → JWT token |
| L8 | Logout | `POST /auth/logout` → call API with old token → 403 |

### 6.2 Security Checklist

- [ ] Password NEVER stored in plain text (must BCrypt hash)
- [ ] JWT secret at least 256-bit, NOT hardcoded in code (use env var)
- [ ] Access token TTL <= 15 minutes. NEVER use forever-valid tokens
- [ ] Every auth-required endpoint must return 401/403 if no/invalid token
- [ ] NEVER return `password_hash` in any response
- [ ] Partial token only usable for `/auth/2fa/**`, cannot call other APIs
- [ ] Backup codes hashed with BCrypt, NOT stored as plain text
- [ ] CSRF disabled is valid for REST API (stateless session)
- [ ] HTTPS mandatory for production (localhost OK in dev)
- [ ] Do not log JWT tokens to console (security leak)
- [ ] Redis connection has password in production

### 6.3 Common Errors and Fixes

| Error | Fix |
|-------|-----|
| Circular dependency: `SecurityConfig → UserDetailsService → PasswordEncoder → SecurityConfig` | Extract `PasswordEncoder` Bean to a separate config or use `@Lazy` |
| JWT verify error "signature does not match": secret differs between generate and verify | Ensure using the same `@Value` inject, do not create Key object in multiple places |
| OAuth2 error "redirect_uri_mismatch" | URI in Google Console must exactly match URI in application.yml, including trailing slash |
| TOTP error "Invalid code" even with correct input: clock skew | TOTP accepts +/- 1 window. Check `System.currentTimeMillis()` and NTP sync |
| Partial token can access `/api/**` (forgot filter) | In JwtFilter check `"stage"` claim. If `stage=partial` and path doesn't start with `/auth/2fa` → reject |
| Redis connection refused: forgot to start Redis | `docker run -d -p 6379:6379 redis:7` or `redis-server &` |

### 6.4 After Phase 1: Preparing for Integration

After completing 4 modules and passing all test cases above, you are ready for Phase 2 (integration into Digital Wallet API). Things to bring:

- `JwtUtil.java`: utility class for generate/verify JWT
- `JwtFilter.java`: `OncePerRequestFilter` that is working
- `SecurityConfig` snippet: `SecurityFilterChain` with correct configuration
- `application.yml` jwt section: secret and expiration
- Understand: how to get `userId` from `SecurityContext` in any method
- Understand: why account ownership check is needed (user A cannot use user B's account)

> **Final Goal:** After Phase 1, you must be able to explain to an interviewer: "Why are access tokens short-lived?", "Why do we need refresh tokens?", "If a token is stolen, what should you do?" — These are common Fintech interview questions.
