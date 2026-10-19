# SIGO — Guía paso a paso de T1 (esqueleto ejecutable)

> **Cómo usar este documento.** Cada paso tiene: **Objetivo**, **Dónde** (paquete/archivo),
> **Qué escribir** (contrato + reglas, *no* el código completo) y **Cómo verificar**.
> **Tú escribes el código; esta guía te orienta.** El contenido literal y definitivo
> (SQL, YAML, HTML) vive en `docs/SIGO_Plan_Desarrollo.md` §4; si algo diverge,
> **manda el plan**.
>
> Referencias: `docs/SIGO_Plan_Desarrollo.md` (§2 stack, §3 estructura, §4.x ejemplos,
> §5 tickets), `docs/SIGO_Tutorial_Desarrollo.md` (T0/T1), `docs/adr/0006`–`0012`.

---

## Notas previas (leer antes de empezar)

- **Paquete real:** `com.evela.sigotrab` (el plan dice `com.sigotrab`; seguimos el código).
- **Prerrequisito T0:** proyecto Maven que compila (`./mvnw -B -DskipTests package` en verde),
  wrapper `./mvnw`, repo git. Ya existe en el repositorio.
- **Decisiones que afectan a T1:** ADR-0006 (Docker `postgres:17` para dev),
  ADR-0009 (primer admin parametrizable), ADR-0012 (Lombok permitido en entidades JPA).
  Q2: normalizar nombres de config.
- **Regla de oro (convención §10):** esquema **solo** por Flyway; nunca `ddl-auto=update`.
  `docs` en español, identificadores de código en inglés.
- **Todos los comandos Maven se ejecutan desde `sigotrab/`**, no desde la raíz.

---

## Paso 0 — Preparar base de datos y normalizar nombres

### 0.1 Base de datos (elige una)

**Opción A — Docker (ADR-0006, recomendado para dev).** `postgres:17` con
`POSTGRES_DB=sigotrab`, `POSTGRES_USER=sigotrab` y la contraseña en `docker/.env`.
(Más adelante en este paso ajustas `docker/docker-compose.yml`, que hoy apunta a
`appdb`/`appuser`/PG16.)

**Opción B — PostgreSQL nativo.**
```sql
CREATE ROLE sigotrab LOGIN PASSWORD 'change-me';
CREATE DATABASE sigotrab OWNER sigotrab;
```

**Verificar:** `psql -U sigotrab -d sigotrab -c "select version();"`

### 0.2 Normalizar nombres (decisión Q2)

Hoy en disco hay nombres mezclados. Normalízalos con `git mv` (conserva el historial):

| Actual | Objetivo |
|---|---|
| `src/main/resources/application.yaml` | `application.yml` |
| `src/main/resources/application-dev.yaml` | `application-dev.yml` |
| `src/main/resources/message/` (carpeta) | `src/main/resources/messages/` |

```bash
git mv src/main/resources/application.yaml     src/main/resources/application.yml
git mv src/main/resources/application-dev.yaml src/main/resources/application-dev.yml
git mv src/main/resources/message              src/main/resources/messages
```

**Cómo verificar:** `ls src/main/resources` muestra `application.yml`, `application-dev.yml`,
`application-lan.yml` y `messages/`.

> **Tip (la lección del rollback):** git **no** guarda carpetas vacías. Cuando crees una
> carpeta que aún no tiene archivos, pon dentro un `.gitkeep` vacío para que no se pierda.

---

## Paso 1 — `pom.xml` con las dependencias del proyecto

**Objetivo:** añadir WebJars, layout dialect, ArchUnit y `embedded-postgres` a lo generado.

**Dónde:** `sigotrab/pom.xml`.

**Qué escribir:** copia el bloque `<dependencies>` y `<properties>` de **§4.2 del plan**.
Puntos a vigilar (particularidades de Spring Boot 4, ver Tutorial Apéndice C):

- El starter web es `spring-boot-starter-webmvc` (**no** `spring-boot-starter-web`).
- El de migraciones es `spring-boot-starter-flyway` (más `flyway-database-postgresql`).
- Los starters de test son **por módulo**: `-webmvc-test`, `-data-jpa-test`,
  `-security-test`, `-flyway-test`.
- **Mantén Lombok** (ADR-0012): deja la dependencia `org.projectlombok:lombok` (scope
  `provided`) y su `annotationProcessorPath`. Úsalo **solo** en entidades JPA, nunca en `domain/`.

**Cómo verificar:** `./mvnw -B dependency:resolve` termina sin errores.

---

## Paso 2 — Configuración externalizada

**Objetivo:** que la app sepa a qué BD conectarse y parametrizar el lock/scheduler.

**Dónde:** `src/main/resources/application.yml`, `application-dev.yml`, `application-lan.yml`
ya existen con el contenido de **§4.3**; revísalos y ajusta si hace falta.

**Qué escribir (nuevo):** los records de propiedades (§4.3):

- `infrastructure/config/LockProperties` → `@ConfigurationProperties("sigotrab.lock")`,
  record con `long durationMinutes`.
- `infrastructure/config/SchedulerProperties` → `@ConfigurationProperties("sigotrab.scheduler")`,
  record con `long fixedDelayMs`.
- En `SigotrabApplication` añade `@ConfigurationPropertiesScan` para que se registren.

**Detalle:** en `application-dev.yml`, el paquete de logging debe ser `com.evela.sigotrab`
(hoy dice `com.sigotrab`).

**Cómo verificar:** `./mvnw -DskipTests package` compila; los records quedan en el contexto.

---

## Paso 3 — Migraciones Flyway (el esquema)

**Objetivo:** crear el esquema y los roles base. **Flyway es la autoridad del esquema.**

**Dónde:** `src/main/resources/db/migration/`
- `V1__baseline.sql`
- `V2__seed_roles.sql`

**Qué escribir:** **copia el SQL tal cual de §4.4 del plan** (no lo inventes). Resumen de lo
que crea V1: `roles`, `users`, `user_roles`, `job_offer` (con `check` de estado y de lock, y
la `unique (source_name, external_reference)` de dedupe §24), `job_offer_status_history` y
`audit_event`, más sus índices. V2 inserta los 3 roles con `on conflict (name) do nothing`.

**Cómo verificar:** al arrancar (Paso 6) Flyway aplica las migraciones. Luego:
```bash
psql -U sigotrab -d sigotrab -c "\dt"
```
Muestra **6 tablas propias** (roles, users, user_roles, job_offer, job_offer_status_history,
audit_event) más `flyway_schema_history`.

---

## Paso 4 — Persistencia + seguridad: login y autorización por rol

**Objetivo:** leer usuarios/roles de la BD y autenticar con formulario.

### 4a. Entidades y repositorio (`adapters/persistence`)

- **`entity/RoleJpaEntity.java`:** `@Entity @Table(name = "roles")` + Lombok
  (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor`); campos `UUID id`
  (`@Id @GeneratedValue(strategy = GenerationType.UUID)`) y `String name`.
  > Usa `@Table(name = ...)`, **NO** `@Entity(name = ...)`: en `@Entity`, `name` es el nombre
  > de entidad JPQL, no la tabla.
- **`entity/UserJpaEntity.java`:** `@Entity @Table(name = "users")`; campos `id`
  (`@Id @GeneratedValue(UUID)`), `username`, `password`, `@Column(name = "full_name") fullName`,
  `enabled`; y la relación:
  ```java
  @ManyToMany(fetch = FetchType.EAGER)          // EAGER porque open-in-view: false
  @JoinTable(name = "user_roles",
      joinColumns        = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<RoleJpaEntity> roles = new HashSet<>();
  ```
  > `@ManyToMany` (**no** `@ManyToOne` sobre una colección). Alternativa a `EAGER`:
  > `@EntityGraph(attributePaths = "roles")` en `findByUsername`.
- **`repository/UserJpaRepository.java`:** `interface ... extends JpaRepository<UserJpaEntity, UUID>`
  con `Optional<UserJpaEntity> findByUsername(String username)` (Spring Data deriva el SQL).
- **Lombok (ADR-0012):** en entidades usa `@NoArgsConstructor` (JPA lo exige) + `@Getter`/`@Setter`.
  **No** combines `@NoArgsConstructor` con `@RequiredArgsConstructor` sin campos `final`
  (ambos generan un constructor sin argumentos → error de compilación).

### 4b. Seguridad (`infrastructure/security`)

1. **`SecurityConfig`** (§4.9): `@Configuration @EnableWebSecurity @EnableMethodSecurity`;
   **bean** `SecurityFilterChain securityFilterChain(HttpSecurity http)` con reglas por rol y
   `formLogin(loginPage("/login").defaultSuccessUrl("/dashboard", true))`; **bean**
   `PasswordEncoder` (`PasswordEncoderFactories.createDelegatingPasswordEncoder()`); **bean**
   `Clock` (`Clock.systemUTC()`). Spring Security 7 = estilo **solo lambda**.
   > Sin `@Bean` en el método, la cadena **no se registra** y cae la seguridad por defecto.
2. **`SigUser`** (`infrastructure/security/SigUser.java`): **record** que implementa
   `UserDetails` con `id`, `username`, `password`, `enabled`, `authorities`; implementa los 7
   métodos (`getAuthorities`, `getPassword`, `getUsername`, `isAccountNonExpired`,
   `isAccountNonLocked`, `isCredentialsNonExpired`, `isEnabled`). El `id` extra es lo que usarás
   luego en `ActorInfo`.
   > No llames a tu clase `UserDetails` (choca con la interfaz de Spring).
3. **`SigUserDetailsService`** (`@Service`): `loadUserByUsername` busca en `UserJpaRepository`;
   mapea cada rol a `new SimpleGrantedAuthority(r.getName())` (con el prefijo `ROLE_` **tal
   cual**); si no existe, lanza `UsernameNotFoundException`; devuelve `new SigUser(...)`.

### 4c. Controladores (`adapters/web/controller`)

- **`LoginController`**: `@Controller` + `@GetMapping("/login")` → `return "login";`
  > **Quirk Boot 4:** `loginPage("/login")` solo *redirige* a `/login`; si no hay un controlador
  > que sirva `GET /login`, obtienes **404**. Por eso es obligatorio.
- **`DashboardController`**: `@GetMapping("/dashboard")` → `return "dashboard";`.
- Devuelve **`String`** (nombre de vista). **NO** devuelvas `ResponseEntity`, que escribe texto
  plano: `GET /login` mostraría la palabra "login" en vez del formulario.

**Cómo verificar (parcial, tras el Paso 6):** `GET /login` responde 200 con el formulario.

---

## Paso 5 — Sembrado de usuarios (`SeedDevUsers`)

**Objetivo:** que existan usuarios en la BD para poder entrar (perfil `dev`). **Nunca** pongas
hashes ni contraseñas en las migraciones SQL (ADR-0005).

**Dónde:**
- `adapters/persistence/repository/RoleJpaRepository.java` (nuevo)
- `infrastructure/config/SeedDevUsers.java` → `@Profile("dev")`
- `infrastructure/config/SeedAdminUser.java` → `@Profile("lan")` (opcional, ADR-0009)

**Paso a paso:**

1. **Repositorio de roles**. Lo necesitas para obtener las entidades `RoleJpaEntity` al asignar
   roles:
   ```java
   public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {
       Optional<RoleJpaEntity> findByName(String name);
   }
   ```

2. **`SeedDevUsers`**: `@Component @Profile("dev")` que implementa `ApplicationRunner`.
   Inyecta por constructor `UserJpaRepository`, `RoleJpaRepository` y `PasswordEncoder`.
   Crea cada usuario **solo si no existe** (idempotente):

   | username | password | rol |
   |---|---|---|
   | `admin` | `admin123` | `ROLE_ADMIN` |
   | `registrador` | `registrador123` | `ROLE_REGISTRADOR` |
   | `publicador` | `publicador123` | `ROLE_PUBLICADOR` |

   ```java
   if (userJpaRepository.findByUsername(username).isPresent()) return;   // idempotente
   var role = roleJpaRepository.findByName(roleName)
       .orElseThrow(() -> new IllegalStateException("Falta el rol " + roleName));
   var user = new UserJpaEntity();                    // @NoArgsConstructor
   // NO asignes id: Hibernate lo genera (@GeneratedValue UUID)
   user.setUsername(username);
   user.setPassword(passwordEncoder.encode(rawPassword));   // hash, NUNCA texto plano
   user.setFullName(fullName);
   user.setEnabled(true);
   user.setRoles(Set.of(role));
   userJpaRepository.save(user);
   ```
   - **Por qué `ApplicationRunner`:** se ejecuta al arrancar, **después** de que Flyway aplicó
     `V2` (por eso los roles ya existen).
   - **Por qué `encode`:** el `PasswordEncoder` delegado guarda el hash con prefijo `{bcrypt}`;
     `matches(...)` lo validará en el login.

3. **(Opcional) `SeedAdminUser`** (`@Profile("lan")`, ADR-0009): mismo patrón, pero
   username/password se leen de propiedades de configuración en vez de estar fijos.

**Cómo verificar:** arranca con el perfil `dev` (Paso 6) y consulta:
```bash
psql -U sigotrab -d sigotrab -c "select u.username, r.name from users u \
  join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id order by 1;"
```
Debe listar los 3 usuarios con su rol. Si arrancas dos veces, siguen siendo 3 (idempotente).

---

## Paso 6 — Vistas (Thymeleaf + Bootstrap) y arranque

**Objetivo:** ver la app corriendo con login y un panel.

**Dónde:** `src/main/resources/templates/` → `layout.html` (base), `login.html`, `dashboard.html`.

**Qué escribir (resumen):**
- **`login.html`**: formulario POST a `/login` con `th:action="@{/login}"`, campos
  `name="username"` y `name="password"`, y avisos con `th:if="${param.error}"` /
  `th:if="${param.logout}"`.
- **`dashboard.html`**: usa el layout; "Hola, <usuario>" (`sec:authentication="name"`) y un
  botón de logout **POST** a `/logout`.

### Thymeleaf en 5 minutos

- **Qué es:** motor de plantillas del lado servidor. Escribes HTML con atributos `th:*` y Spring
  lo renderiza; el controlador devuelve el **nombre de vista** (`return "login"` →
  `templates/login.html`).
- **Espacios de nombres:** `xmlns:th="http://www.thymeleaf.org"` (Thymeleaf) y
  `xmlns:sec="http://www.thymeleaf.org/extras/spring-security"` (extras de Spring Security).
- **Expresiones:** `${...}` variable · `*{...}` campo de `th:object` · `@{...}` URL ·
  `#{...}` mensaje i18n · `~{...}` fragmento.
- **Atributos:** `th:text`, `th:each`, `th:if`/`th:unless`, `th:href`, `th:src`, `th:action`,
  `th:object`+`th:field`, `th:replace`/`th:insert`.
- **Layout:** `layout.html` declara `th:fragment="layout(title, content)"`; cada página lo usa con
  `th:replace="~{layout :: layout(~{::title}, ~{::main})}"` y aporta su `<title>` y `<main>`
  (`~{::title}` = "este `<title>` de aquí"). Así los estilos/scripts se escriben una sola vez.
- **CSRF:** en formularios POST usa **`th:action`** (no `action`); Thymeleaf inyecta el campo
  oculto `_csrf` solo. Con `action` plano y sin `_csrf` → **403**.
- **URLs:** usa siempre `@{...}` (respeta el *context path*).

**Cómo verificar (fin de T1):**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
Abre `http://localhost:8080` → te redirige a `/login`; entra como `registrador` /
`registrador123` → llegas a `/dashboard` (200).
- Sin sesión, `GET /dashboard` redirige a `/login`.
- Credenciales malas → `/login?error`.

**Commit:** `feat: esqueleto con base de datos, login y dashboard (#T1)`

---

## Paso 7 — Mensajes i18n (opcional en T1, recomendado)

**Objetivo:** textos de UI en español desde un `MessageSource`.

**Dónde:** `src/main/resources/messages/messages_es.properties` y un
`infrastructure/config/MessageConfig.java`.

**Qué escribir:** llaves de los textos usados (título de login, "Iniciar sesión", etc.).

> **Quirk Boot 4:** `spring.messages.basename` **no** se respetó en este setup. Se
> resolvió declarando un bean `MessageSource` explícito en `MessageConfig` con
> `basename = classpath:messages/messages` y `UTF-8`. Documenta esto donde corresponda.

**Cómo verificar:** la pantalla de login muestra los textos desde el archivo de mensajes.

---

## Apéndice — Errores comunes en T1

| Síntoma | Causa probable | Solución |
|---|---|---|
| `GET /login` → **404** | Falta el controlador de login | Añade `LoginController` (Paso 4c) |
| `GET /login` muestra el texto "login" | El controlador devuelve `ResponseEntity` (texto plano) | Devuelve `String` = nombre de vista (Paso 4c) |
| La app no arranca, Flyway falla | `db/migration` vacío con `ddl-auto: validate` | Crea `V1`/`V2` (Paso 3) |
| `./mvnw test` falla sin BD | El único test es `@SpringBootTest` y no hay PG | Usa `-Dtest=...` o escribe `EmbeddedPostgresSupport` (§4.12) |
| Textos i18n no cargan | `spring.messages.basename` ignorado | Bean `MessageSource` explícito (Paso 7) |
| "constructor already defined" | Lombok: `@NoArgsConstructor` + `@RequiredArgsConstructor` sin campos `final` | Deja solo `@NoArgsConstructor` (ADR-0012) |
| `taskkill` no mata la app (Windows) | Mata el shell, no el JVM | Usa `jps -l` para hallar el PID del JVM |

---

## Definición de "T1 terminado"

- [ ] `./mvnw -B -DskipTests package` en verde.
- [ ] Flyway aplica `V1`/`V2`; `\dt` lista 6 tablas + `flyway_schema_history`.
- [ ] Existen los 3 usuarios demo (`dev`).
- [ ] `/login` responde 200; login correcto → `/dashboard`; incorrecto → `/login?error`.
- [ ] `/dashboard` sin sesión → redirige a `/login`.
- [ ] Commit `feat: esqueleto con base de datos, login y dashboard (#T1)`.
