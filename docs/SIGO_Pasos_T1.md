# SIGO — Guía paso a paso de T1 (esqueleto ejecutable)

> **Cómo usar este documento.** Cada paso tiene: **Objetivo**, **Dónde** (paquete/archivo),
> **Qué escribir** (contrato + reglas, *no* el código completo) y **Cómo verificar**.
> **Tú escribes el código; esta guía te orienta.** El contenido literal y definitivo
> (SQL, YAML, HTML) vive en `docs/SIGO_Plan_Desarrollo.md` §4; si algo diverge,
> **manda el plan**.
>
> Referencias: `docs/SIGO_Plan_Desarrollo.md` (§2 stack, §3 estructura, §4.x ejemplos,
> §5 tickets), `docs/SIGO_Tutorial_Desarrollo.md` (T0/T1), `docs/adr/0006`–`0012`.
>
> **Actualización (verificación 2026-10-19).** El **Paso 8** recoge el plan de cierre de T1
> tras verificar la app en ejecución y la BD; el **Apéndice B** es una guía de ejemplos
> comentados para aprender. La **definición de "T1 terminado"** del final es la lista de control.

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

**Opción A — Docker (ADR-0006, recomendado para dev).** `postgres` con la versión de
`POSTGRES_VERSION` en `docker/.env` (hoy `16-alpine`), `POSTGRES_DB=sigotrabdb`,
`POSTGRES_USER=sigotrabuser` y la contraseña en `docker/.env`.
El `docker/docker-compose.yml` ya usa esos valores y `docker/` ya está fuera de
`src/main/resources` (ver 8.2.5).

**Opción B — PostgreSQL nativo.**
```sql
CREATE ROLE sigotrabuser LOGIN PASSWORD 'change-me';
CREATE DATABASE sigotrabdb OWNER sigotrabuser;
```

**Verificar:** `psql -U sigotrabuser -d sigotrabdb -c "select version();"`

> **Decisión de cierre (Paso 8):** conservar `sigotrabdb`/`sigotrabuser`, que son los
> valores que funcionan en `docker/.env` y en la BD local. El Paso 8.2.4 actualiza
> plan/ADR para que no quede la mezcla con `sigotrab`.

### 0.2 Normalizar nombres (decisión Q2)

Si aún tienes los nombres viejos (`.yaml`, `message/`), normalízalos con `git mv`
(conserva el historial). En este repositorio ya están normalizados; el bloque queda como
referencia:

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
psql -U sigotrabuser -d sigotrabdb -c "\dt"
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
   **bean** `SecurityFilterChain securityFilterChain(HttpSecurity http,
   SigAuthenticationFailureHandler failureHandler)` con reglas por rol y
   `formLogin(loginPage("/login").failureHandler(failureHandler)
   .defaultSuccessUrl("/dashboard", true))`; **bean**
   `PasswordEncoder` (`PasswordEncoderFactories.createDelegatingPasswordEncoder()`); **bean**
   `Clock` (`Clock.systemUTC()`). Spring Security 7 = estilo **solo lambda**.
   > `SigAuthenticationFailureHandler` es opcional: guarda en sesión un código
   > (`badCredentials`, `disabled`, `locked`) y redirige a `/login?error`. Si no lo usas,
   > el mensaje genérico de Spring basta.
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
psql -U sigotrabuser -d sigotrabdb -c "select u.username, r.name from users u \
  join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id order by 1;"
```
Debe listar los 3 usuarios con su rol. Si arrancas dos veces, siguen siendo 3 (idempotente).

---

## Paso 6 — Vistas (Thymeleaf + Bootstrap) y arranque

**Objetivo:** ver la app corriendo con login y un panel.

**Dónde:** `src/main/resources/templates/` → `layout.html` (base), `login.html`, `dashboard.html`.

**Qué escribir (resumen):**
- **`login.html`**: formulario POST a `/login` con `th:action="@{/login}"`, campos
  `name="username"` y `name="password"`, y avisos con `${loginError}` (modelo) /
  `th:if="${param.logout}"`. Con el handler por defecto de Spring, el aviso sería
  `th:if="${param.error}"`.
- **`dashboard.html`**: usa el layout; "Hola, <usuario>" (`sec:authentication="name"`) y un
  botón de logout **POST** a `/logout`.
  > El `dashboard.html` actual es un stub (`… tarjetas …`). Su versión final está en
  > **8.2.1** y en **B.11**.

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

## Paso 8 — Cierre de T1: plan de corrección verificado

> **Fecha de verificación:** 2026-10-19. Se revisó el código, la app en ejecución
> (perfil `dev`, `:8080`) y la BD. **T1 funciona**, pero no cumple la definición de
> terminado. Este paso cierra las brechas en el orden en que conviene hacerlo.

### 8.1 Estado verificado (evidencia)

| Comprobación | Resultado | Evidencia |
|---|---|---|
| La app arranca con `dev` | ✅ | `com.evela.sigotrab.SigotrabApplication`, `:8080` |
| Flyway aplica `V1`/`V2` | ✅ | `flyway_schema_history`: `1 baseline`, `2 seed roles`, `success=t` |
| 6 tablas propias | ✅ | `roles`, `users`, `user_roles`, `job_offer`, `job_offer_status_history`, `audit_event` |
| 3 usuarios demo con rol | ✅ | `admin`/`ROLE_ADMIN`, `registrador`/`ROLE_REGISTRADOR`, `publicador`/`ROLE_PUBLICADOR` |
| Login correcto | ✅ | `POST /login` → `302 /dashboard`; `GET /dashboard` → `200` |
| Login incorrecto | ✅ | `POST /login` → `302 /login?error` |
| Error de login una vez | ✅ | 1.º `GET /login?error` muestra; 2.º y limpio no |
| `/dashboard` sin sesión | ✅ | `302 /login`; `/` también |
| Dashboard con saludo y logout | ✅ | `dashboard.html` con "Bienvenido, <usuario>" y POST `/logout` |
| `SeedAdminUser` (`lan`) | ✅ | crea el admin en BD vacía; si ya hay usuarios, no hace nada |
| `package` (jar) | ✅ | `target/sigotrab-0.0.1-SNAPSHOT.jar` (sin `docker/` ni `.env`) |
| Commit de T1 | ❌ | historial no convencional; árbol de trabajo sucio |

### 8.2 Correcciones (en orden)

#### 8.2.1 Dashboard real (bloqueante)

**Archivos:** `templates/dashboard.html`, `DashboardController.java`,
`messages/messages_es.properties`.

**Qué hacer:** sustituye el stub por un panel mínimo con:

1. saludo `Hola, <usuario>` usando `sec:authentication="name"`;
2. botón de logout **POST** con `th:action="@{/logout}"`;
3. cinco tarjetas placeholder (`REGISTERED`, `APPROVED`, `IN_PROGRESS`, `PUBLISHED`, `REJECTED`).

**Por qué:** el Paso 6 lo pide y sin logout no se puede cerrar sesión desde la UI. El
`POST` es obligatorio porque Spring Security protege `/logout` con CSRF.

**Cómo verificar:** login → `/dashboard` muestra "Hola, registrador"; clic en Salir →
`/login?logout`.

Código de referencia: **B.11**.

#### 8.2.2 Limpiar `loginError` y mostrar `logout` (bloqueante menor)

**Archivos:** `infrastructure/security/SigAuthenticationFailureHandler.java`,
`adapters/web/controller/LoginController.java`, `templates/login.html`.

**Problema:** el handler guarda `loginError` en sesión y nadie lo borra; el mensaje
reaparece en visitas posteriores. Además `login?logout` no muestra nada.

**Qué hacer:** en `LoginController`, pasa el error de la sesión al modelo y bórralo:

```java
@GetMapping("/login")
public String login(HttpSession session, Model model) {
    Object error = session.getAttribute("loginError");
    if (error != null) {
        model.addAttribute("loginError", error);
        session.removeAttribute("loginError");
    }
    return "login";
}
```

Y en `login.html`:

- cambia `th:if="${session.loginError}"` por `th:if="${loginError}"` (y el `th:switch`);
- añade `th:if="${param.logout}"` con `#{login.logout}`.

**Por qué:** el error se muestra una vez y se limpia; el logout tiene feedback. Si borras
la sesión en el GET *antes* de renderizar, el error no se vería; por eso se copia al modelo
y se elimina de la sesión.

**Cómo verificar:** falla el login → ves el mensaje; recarga `/login` → ya no está;
logout → ves "Sesión cerrada".

#### 8.2.3 Limpieza de código (rápida)

**Archivos:** `LoginController.java`, `DashboardController.java`,
`RoleJpaRepository.java`, `messages/messages_es.properties`.

- Quita imports sin usar: `ResponseEntity` y `RequestMapping` en los controladores;
  `javax.management.relation.Role` en `RoleJpaRepository`.
- Quita las claves espurias `á=...`, `é=...`, … de `messages_es.properties` (líneas 1–18).
  Deja solo las claves `login.*` y las nuevas `dashboard.*`.
- Asegura un salto de línea final.

**Por qué:** warnings innecesarios y ruido; no aportan nada.

**Cómo verificar:** `./mvnw -q -DskipTests compile` sin warnings de imports.

#### 8.2.4 Unificar el nombre de la BD (bloqueante de configuración)

**Decisión:** conservar `sigotrabdb` / `sigotrabuser`, que son los valores que ya
funcionan en `docker/.env` y en la BD local. El plan §4.3 y ADR-0006 se actualizan para
reflejar esa decisión; no se migra la BD.

Situación tras el cambio:

| Sitio | BD / usuario |
|---|---|
| `application.yml` | `sigotrabdb` / `sigotrabuser` |
| `application-lan.yml` | `sigotrabdb` / `sigotrabuser` (con override por env) |
| `docker-compose.yml` + `.env` | `sigotrabdb` / `sigotrabuser` |
| Plan §4.3 / ADR-0006 / esta guía | `sigotrabdb` / `sigotrabuser` |

Pasos:

1. Mantén el rol/BD que ya existe (si hiciera falta recrearlos):
   ```sql
   CREATE ROLE sigotrabuser LOGIN PASSWORD 'change-me';
   CREATE DATABASE sigotrabdb OWNER sigotrabuser;
   ```
2. `application.yml` usa `sigotrabdb` / `sigotrabuser`.
3. `application-lan.yml` parametriza el nombre con default `sigotrabdb` y el usuario con
   default `sigotrabuser`:
   ```yaml
   url: jdbc:postgresql://${SIGOTRAB_DB_HOST:localhost}:5432/${SIGOTRAB_DB_NAME:sigotrabdb}
   username: ${SIGOTRAB_DB_USER:sigotrabuser}
   password: ${SIGOTRAB_DB_PASSWORD}
   ```
4. `docker-compose.yml` y `.env` ya usan `POSTGRES_DB=sigotrabdb`,
   `POSTGRES_USER=sigotrabuser` y `POSTGRES_VERSION=16-alpine`.
5. Arranca con `dev`: Flyway y `SeedDevUsers` siguen funcionando sobre `sigotrabdb`.

**Por qué:** es la BD que ya está creada y la que usa el `.env` que funciona; migrar a
`sigotrab` solo añadiría riesgo sin beneficio en T1.

**Cómo verificar:** `psql -U sigotrabuser -d sigotrabdb -c "\dt"` y login.

> **Deuda separada (8.2.8):** la versión de PostgreSQL sigue mezclada (Docker 16,
> embedded 17.5, runtime 18.6). Este punto solo unifica nombre de BD y usuario.

#### 8.2.5 Sacar `docker/` de `src/main/resources` (bloqueante de empaquetado)

**Problema:** `src/main/resources/docker/` se copia a `target/classes/docker/` y **entra
en el jar**, incluido `.env` con la contraseña.

**Qué hacer:**

```bash
git mv sigotrab/src/main/resources/docker sigotrab/docker
git rm --cached sigotrab/docker/.env
printf '.env\n' >> sigotrab/.gitignore
# crea sigotrab/docker/.env.example con placeholders
```

- Deja `.env` solo en local.
- Ajusta las rutas en la documentación (`docker/` → `sigotrab/docker/`).
- Si usabas `docker compose` desde `src/main/resources/docker`, ahora se ejecuta desde
  `sigotrab/docker`.

**Por qué:** un `.env` dentro del jar es una fuga de secreto y ensucia el artefacto.

**Cómo verificar:** `./mvnw -B -DskipTests package` y luego
`jar tf target/sigotrab-*.jar | grep docker` no debe listar `.env` (ni `docker/`).

#### 8.2.6 `SeedAdminUser` (opcional T1, necesario T15)

**Estado:** implementado.

Sigue ADR-0009: solo crea el administrador cuando la base de datos **no tiene ningún
usuario**. Si ya existe al menos uno, no hace nada (arranque idempotente y sin obligar a
conservar la contraseña en el entorno). Lee las propiedades:

```yaml
sigotrab:
  admin:
    username: ${SIGOTRAB_ADMIN_USERNAME:admin}
    password: ${SIGOTRAB_ADMIN_PASSWORD:}
    full-name: ${SIGOTRAB_ADMIN_FULL_NAME:Administrador}
```

- `sigotrab.admin.password` no tiene valor por defecto: si la BD está vacía y falta,
  falla con un mensaje claro.
- El rol asignado es `ROLE_ADMIN` (debe existir por `V2__seed_roles.sql`).

**Cómo verificar:** con `--spring.profiles.active=lan`, `SIGOTRAB_DB_USER`,
`SIGOTRAB_DB_PASSWORD` y `SIGOTRAB_ADMIN_PASSWORD` definidos, una BD vacía crea el
admin; sin la contraseña, falla con un mensaje claro.

#### 8.2.7 `package` y commit de cierre (bloqueante)

```bash
./mvnw -B -DskipTests package
ls target/sigotrab-*.jar
git add -A
git commit -m "feat: esqueleto con base de datos, login y dashboard (#T1)"
git status --short
```

**Por qué:** la definición de terminado exige el jar y el commit; hoy no existen.

#### 8.2.8 Deuda que NO bloquea T1

- **Versión de PostgreSQL:** runtime 18.6, tests embebidos 17.5.0, Docker 16. Resolver
  antes de T9 (integración). Recomendación: `postgres:17` en Docker + embedded 17.5; si
  te quedas en 18 nativo, anótalo en ADR-0006 y actualiza los binarios de test.
- **Tests:** hay un test unitario de `SeedAdminUser`; el `@SpringBootTest` sigue
  dependiendo de la BD local. Antes de T9, añade `EmbeddedPostgresSupport` (§4.12) y un
  `application-test.yml`.
- **`AGENTS.md`:** actualizado con el estado de T1 (`SeedDevUsers`/`SeedAdminUser`,
  dashboard, BD `sigotrabdb`, docker fuera de resources).

### 8.3 Orden recomendado

1. [ ] 8.2.3 limpieza (rápida, sin riesgo).
2. [ ] 8.2.2 login/logout.
3. [ ] 8.2.1 dashboard.
4. [ ] 8.2.4 nombres de BD.
5. [ ] 8.2.5 docker fuera de resources.
6. [ ] 8.2.6 `SeedAdminUser` (o borrarlo).
7. [ ] 8.2.7 `package` + commit.
8. [ ] Re-verificación (8.4).

### 8.4 Re-verificación final

```bash
# 1) build + jar
./mvnw -B -DskipTests package && ls target/sigotrab-*.jar

# 2) BD
psql -U sigotrabuser -d sigotrabdb -c "\dt"
psql -U sigotrabuser -d sigotrabdb -c "select u.username, r.name from users u
  join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id order by 1;"

# 3) app
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 4) HTTP (en otra terminal)
curl -i http://localhost:8080/dashboard          # 302 → /login
curl -i http://localhost:8080/login              # 200
# login: GET /login, extraer _csrf, POST /login con cookie, seguir a /dashboard
```

**Criterio:** los puntos de la definición de terminado (al final del documento).

---

## Apéndice A — Errores comunes en T1

| Síntoma | Causa probable | Solución |
|---|---|---|
| `GET /login` → **404** | Falta el controlador de login | Añade `LoginController` (Paso 4c) |
| `GET /login` muestra el texto "login" | El controlador devuelve `ResponseEntity` (texto plano) | Devuelve `String` = nombre de vista (Paso 4c) |
| La app no arranca, Flyway falla | `db/migration` vacío con `ddl-auto: validate` | Crea `V1`/`V2` (Paso 3) |
| `./mvnw test` falla sin BD | El único test es `@SpringBootTest` y no hay PG | Usa `-Dtest=...` o escribe `EmbeddedPostgresSupport` (§4.12) |
| Textos i18n no cargan | `spring.messages.basename` ignorado | Bean `MessageSource` explícito (Paso 7) |
| "constructor already defined" | Lombok: `@NoArgsConstructor` + `@RequiredArgsConstructor` sin campos `final` | Deja solo `@NoArgsConstructor` (ADR-0012) |
| `taskkill` no mata la app (Windows) | Mata el shell, no el JVM | Usa `jps -l` para hallar el PID del JVM |
| El mensaje de error de login reaparece | `loginError` en sesión sin limpiar | Pásalo al modelo y bórralo (8.2.2) |
| 403 al hacer logout desde un enlace | `/logout` exige **POST** + CSRF | Formulario POST con `th:action` (B.11) |
| El jar contiene `.env` | `docker/` dentro de `src/main/resources` | Muévelo a `sigotrab/docker` (8.2.5) |
| En perfil `lan` no hay usuarios | `SeedAdminUser` vacío | Implementarlo o borrarlo (8.2.6) |
| `lan` no conecta con la BD | La URL apunta a una BD que no existe o las credenciales no coinciden | Revisa `SIGOTRAB_DB_NAME`/`SIGOTRAB_DB_USER`/`SIGOTRAB_DB_PASSWORD` (8.2.4) |
| `LazyInitializationException` en login | `open-in-view: false` y roles `LAZY` | `EAGER` o `@EntityGraph` (B.4) |

---

## Apéndice B — Guía de ejemplos para aprender (T1 de punta a punta)

> **Cómo usar este apéndice.** No es código para copiar: es material de lectura. Cada
> ejemplo tiene **qué hace**, el **código** y **por qué** está así. Después de leerlo,
> vuelve al paso correspondiente y escríbelo tú. Los ejemplos reflejan el código real del
> repositorio (paquete `com.evela.sigotrab`) y, donde indica "objetivo", la versión que
> deja el Paso 8. Si un ejemplo difiere de §4 del plan, **manda el plan**.
>
> ¿Te falta vocabulario (bean, JPA, Flyway, CSRF, DTO…)? Consulta el **Apéndice A** de
> `docs/SIGO_Tutorial_Desarrollo.md`, que define las notaciones y librerías.

### B.1 El viaje de una petición (mapa mental)

Petición protegida:

```text
Navegador
  │  GET /dashboard
  ▼
SecurityFilterChain ── ¿hay sesión? ── no ──► 302 /login
  │ sí
  ▼
DashboardController.dashboard() → "dashboard"
  ▼
Thymeleaf: templates/dashboard.html + layout.html
  ▼
HTML 200
```

Login:

```text
POST /login  (username, password, _csrf)
  ▼
UsernamePasswordAuthenticationFilter
  ▼
SigUserDetailsService.loadUserByUsername(username)
  ▼
UserJpaRepository.findByUsername → UserJpaEntity (roles EAGER)
  ▼
SigUser (UserDetails)
  ▼
PasswordEncoder.matches(raw, hash) ── no ──► SigAuthenticationFailureHandler → 302 /login?error
  │ sí
  ▼
SecurityContext (sesión) → 302 /dashboard
```

Idea clave: **el controlador nunca ve la contraseña**. Spring Security autentica antes de
llegar al controlador; el controlador solo sirve la vista.

### B.2 `application.yml` explicado

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sigotrabdb
    username: sigotrabuser
    password: ${SIGOTRAB_DB_PASSWORD:change-me}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    locations: classpath:db/migration
  thymeleaf:
    cache: true
  web:
    locale: es
    locale-resolver: fixed
server:
  port: 8080
sigotrab:
  lock:
    duration-minutes: 20
  scheduler:
    fixed-delay-ms: 60000
```

- `${VAR:default}`: variable de entorno con valor de respaldo; nunca escribas secretos reales.
- `open-in-view: false`: el contexto de persistencia se cierra al terminar la llamada. Por
  eso los roles se cargan `EAGER` en T1: si fueran `LAZY`, al pintar la vista ya no habría
  sesión de Hibernate (`LazyInitializationException`).
- `ddl-auto: validate`: Hibernate **solo comprueba** que el esquema coincide con las
  entidades; no crea tablas. La autoridad del esquema es Flyway.
- `hibernate.jdbc.time_zone: UTC`: guarda instantes en UTC; convierte en los bordes.
- `locale-resolver: fixed`: siempre español, sin depender del navegador.
- `thymeleaf.cache: false` va solo en `application-dev.yml`; en producción se cachea.
- `application-dev.yml` también baja el lock a 2 minutos para probar rápido.

### B.3 Flyway: `V1__baseline.sql` (fragmento)

```sql
create table roles (
    id   uuid primary key default gen_random_uuid(),
    name varchar(50) not null unique
);

create table users (
    id         uuid primary key default gen_random_uuid(),
    username   varchar(60)  not null unique,
    password   varchar(100) not null,
    full_name  varchar(150) not null,
    enabled    boolean      not null default true,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

create table user_roles (
    user_id uuid not null references users(id) on delete cascade,
    role_id uuid not null references roles(id) on delete cascade,
    primary key (user_id, role_id)
);
```

- `gen_random_uuid()`: nativo en PostgreSQL ≥ 13; no hace falta `pgcrypto`.
- `timestamptz`: guarda un instante, no una hora local "ingenua".
- `unique`: la BD lo impone aunque la app tenga un bug.
- `on delete cascade` en `user_roles`: borrar un usuario limpia la tabla de unión.
- `user_roles` es la **tabla de unión** del `@ManyToMany`.
- `V1__baseline.sql`: Flyway ejecuta los archivos en orden de versión, una sola vez, y
  registra el checksum en `flyway_schema_history`. **Nunca edites una migración ya
  aplicada**: crea `V3__...`.

El resto de `V1` (no lo copies a medias) crea `job_offer` con sus `check`, la
`unique (source_name, external_reference)` de dedupe, `job_offer_status_history` y
`audit_event`, más sus índices. El SQL completo está en §4.4 del plan.

### B.4 Entidad JPA: `UserJpaEntity`

```java
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UserJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String username;
    private String password;

    @Column(name = "full_name")
    private String fullName;

    private boolean enabled;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleJpaEntity> roles = new HashSet<>();
}
```

- `@Table(name = "users")`, **no** `@Entity(name = ...)`: en `@Entity`, `name` es el nombre
  JPQL, no la tabla.
- `@GeneratedValue(UUID)`: Hibernate genera el id; no lo asignes a mano.
- `@Column(name = "full_name")`: mapea camelCase a snake_case.
- `@ManyToMany` + `@JoinTable`: un usuario tiene muchos roles y un rol muchos usuarios.
- `EAGER`: con `open-in-view: false`, los roles deben estar cargados al cerrar la llamada.
  Alternativa: `@EntityGraph(attributePaths = "roles")` en `findByUsername`.
- `@NoArgsConstructor`: JPA exige constructor sin argumentos. Por eso Lombok se permite en
  entidades (ADR-0012), **no** en `domain/`.

### B.5 Repositorio Spring Data

```java
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByUsername(String username);
}
```

- Escribes la interfaz; Spring Data genera la implementación al arrancar.
- `findByUsername` → `select ... from users where username = ?`.
- `Optional` obliga a manejar el "no existe".
- `JpaRepository<T, ID>` aporta `save`, `findById`, `findAll`, `delete`, etc.
- Vive en `adapters/persistence`; el dominio no lo conoce.

### B.6 `SigUser`: un `record` que es `UserDetails`

```java
public record SigUser(UUID id, String username, String password, boolean enabled,
                      Collection<? extends GrantedAuthority> authorities)
        implements UserDetails {

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public @Nullable String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
```

- `record`: portador inmutable de datos; ideal para una "foto" del usuario autenticado.
- `UserDetails` es el contrato que Spring Security usa tras autenticar.
- `id` es un extra: T3+ lo necesita para `created_by` / `assigned_user_id`.
- Los `true`: T1 no tiene política de expiración/bloqueo; se declaran explícitamente.
- No llames a tu clase `UserDetails` (choca con la interfaz).

| Método de `UserDetails` | Quién lo llama | Respuesta en T1 |
|---|---|---|
| `getUsername()` | `UsernamePasswordAuthenticationFilter` | el `username` de la BD |
| `getPassword()` | `DaoAuthenticationProvider` | el hash `{bcrypt}...` |
| `getAuthorities()` | `authorizeHttpRequests` | `ROLE_ADMIN`, etc. |
| `isEnabled()` | `DaoAuthenticationProvider` | `users.enabled` |
| `isAccountNonExpired()` | idem | `true` |
| `isAccountNonLocked()` | idem | `true` |
| `isCredentialsNonExpired()` | idem | `true` |

### B.7 `SigUserDetailsService`

```java
@Service
public class SigUserDetailsService implements UserDetailsService {
    private final UserJpaRepository users;

    public SigUserDetailsService(UserJpaRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var user = users.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        var authorities = user.getRoles().stream()
            .map(r -> new SimpleGrantedAuthority(r.getName()))
            .collect(Collectors.toSet());

        return new SigUser(user.getId(), user.getUsername(), user.getPassword(),
                           user.isEnabled(), authorities);
    }
}
```

- Spring Security llama a este método durante el formulario; tú nunca lo llamas desde un
  controlador.
- La autoridad debe ser `ROLE_ADMIN` **con** el prefijo, porque `hasRole("ADMIN")` lo añade
  por dentro. Si guardas `ADMIN`, `hasRole("ADMIN")` no coincide.
- `UsernameNotFoundException` y "credenciales incorrectas" se muestran igual: no reveles si
  el usuario existe.
- `password` es el hash de la BD; Spring lo compara con `PasswordEncoder.matches`.

### B.8 `SecurityConfig` (Security 7, lambda DSL)

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SigAuthenticationFailureHandler failureHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/webjars/**", "/login", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/offers/new", "/offers/*/edit", "/offers/*/approve", "/offers/*/reject")
                    .hasRole("REGISTRADOR")
                .requestMatchers("/api/offers/*/acquire", "/offers/*/cancel", "/offers/*/publish")
                    .hasRole("PUBLICADOR")
                .requestMatchers("/reports/**").hasAnyRole("ADMIN", "REGISTRADOR")
                .requestMatchers("/audit/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .failureHandler(failureHandler)
                .defaultSuccessUrl("/dashboard", true)
                .permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
            .csrf(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    Clock clock() { return Clock.systemUTC(); }
}
```

- `@Bean` en el método es **obligatorio**: sin él, Spring Boot usa la cadena por defecto
  (todo autenticado y login generado) y tu configuración se ignora.
- El orden importa: gana la **primera** regla que coincide; `anyRequest()` va al final.
- `hasRole("ADMIN")` espera la autoridad `ROLE_ADMIN`.
- `permitAll()` en `formLogin` deja pasar `GET/POST /login` sin autenticar.
- `defaultSuccessUrl("/dashboard", true)`: siempre al panel tras entrar.
- `csrf(Customizer.withDefaults())`: CSRF está activo por defecto; la línea lo documenta.
  Con `th:action`, Thymeleaf inyecta `_csrf`.
- `Clock`: tiempo inyectable para tests (T3+), en vez de `Instant.now()`.
- `createDelegatingPasswordEncoder()`: guarda `{bcrypt}hash` y permite migrar de algoritmo.

Estados HTTP típicos en T1:

| Situación | Código | Por qué |
|---|---|---|
| `GET /dashboard` sin sesión | `302 → /login` | *entry point* de seguridad |
| `POST /login` con credenciales malas | `302 → /login?error` | `SigAuthenticationFailureHandler` |
| `POST` sin `_csrf` | `403` | CSRF |
| `GET /login` | `200` | `permitAll` + controlador |
| `GET /dashboard` autenticado | `200` | vista |

### B.9 Controladores: nombre de vista, no `ResponseEntity`

```java
@Controller
public class LoginController {
    @GetMapping("/login")
    public String login() { return "login"; }        // → templates/login.html
}

@Controller
public class DashboardController {
    @GetMapping("/dashboard")
    public String dashboard() { return "dashboard"; } // → templates/dashboard.html
}
```

- `@Controller` (**no** `@RestController`): el retorno es un nombre de vista que resuelve
  Thymeleaf.
- `return "login"` → `templates/login.html`.
- `ResponseEntity<String>` escribiría `login` como texto plano: el bug clásico de T1.
- `loginPage("/login")` en Security solo **redirige**; hace falta este controlador para
  servir la vista (quirk de Boot 4).

### B.10 `SeedDevUsers`: semilla idempotente

```java
@Component
@Profile("dev")
public class SeedDevUsers implements ApplicationRunner {
    private final UserJpaRepository users;
    private final RoleJpaRepository roles;
    private final PasswordEncoder passwordEncoder;

    public SeedDevUsers(UserJpaRepository users, RoleJpaRepository roles,
                        PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        create("admin", "admin123", "Administrador", "ROLE_ADMIN");
        create("registrador", "registrador123", "Registrador", "ROLE_REGISTRADOR");
        create("publicador", "publicador123", "Publicador", "ROLE_PUBLICADOR");
    }

    private void create(String username, String rawPassword, String fullName, String roleName) {
        if (users.findByUsername(username).isPresent()) return;   // idempotente
        var role = roles.findByName(roleName)
            .orElseThrow(() -> new IllegalStateException("Falta el rol " + roleName));
        var user = new UserJpaEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        users.save(user);
    }
}
```

- `@Profile("dev")`: nunca siembra usuarios demo en `lan`/producción.
- `ApplicationRunner`: corre cuando el contexto está listo y Flyway ya aplicó `V2`.
- Idempotente: reiniciar no duplica usuarios.
- `encode`: guarda `{bcrypt}...`; nunca texto plano.
- ¿Por qué no en SQL? Las migraciones son esquema/historial; las contraseñas dependen del
  entorno (ADR-0005).

### B.11 Vistas: layout, login y dashboard

`layout.html` (fragmento base):

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security"
      th:fragment="layout(title, content)">
<head>
  <meta charset="UTF-8"/>
  <title th:replace="${title}">SIGO</title>
  <link rel="stylesheet" th:href="@{/webjars/bootstrap/5.3.8/css/bootstrap.min.css}"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
</head>
<body>
  <main th:replace="${content}"></main>
  <script th:src="@{/webjars/bootstrap/5.3.8/js/bootstrap.bundle.min.js}"></script>
</body>
</html>
```

`login.html` (objetivo, con el error ya limpio y el aviso de logout):

```html
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout :: layout(~{::title}, ~{::main})}">
<head><title th:text="#{login.title}">Login — SIGO</title></head>
<body>
<main>
  <form th:action="@{/login}" method="post">
    <div>
      <label for="username" th:text="#{login.username}">Usuario</label>
      <input type="text" id="username" name="username" required autofocus>
    </div>
    <div>
      <label for="password" th:text="#{login.password}">Contraseña</label>
      <input type="password" id="password" name="password" required>
    </div>
    <p th:if="${loginError}" th:switch="${loginError}">
      <span th:case="'badCredentials'" th:text="#{login.error.badCredentials}"></span>
      <span th:case="'disabled'"       th:text="#{login.error.disabled}"></span>
      <span th:case="'locked'"         th:text="#{login.error.locked}"></span>
      <span th:case="*"                th:text="#{login.error}"></span>
    </p>
    <p th:if="${param.logout}" th:text="#{login.logout}">Sesión cerrada</p>
    <button type="submit" th:text="#{login.submit}">Entrar</button>
  </form>
</main>
</body>
</html>
```

- `th:action` inyecta `_csrf` automáticamente. Con `action` plano y sin `_csrf` → **403**.
- `@{/login}` respeta el *context path*.
- `#{...}` lee `messages/messages_es.properties`.
- `th:if="${param.logout}"` muestra el aviso tras cerrar sesión.

`DashboardController` (objetivo, con la lista de estados en el modelo):

```java
@Controller
public class DashboardController {
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("statuses",
            List.of("REGISTERED", "APPROVED", "IN_PROGRESS", "PUBLISHED", "REJECTED"));
        return "dashboard";
    }
}
```

`dashboard.html` (objetivo):

```html
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security"
      th:replace="~{layout :: layout(~{::title}, ~{::main})}">
<head><title>Panel — SIGO</title></head>
<body>
<main class="container py-4">
  <div class="d-flex justify-content-between align-items-center mb-4">
    <h1 class="h3 mb-0">
      <span th:text="#{dashboard.hello}">Hola</span>,
      <span sec:authentication="name">usuario</span>
    </h1>
    <form th:action="@{/logout}" method="post">
      <button type="submit" class="btn btn-outline-secondary"
              th:text="#{dashboard.logout}">Salir</button>
    </form>
  </div>

  <div class="row g-3">
    <div class="col" th:each="status : ${statuses}">
      <div class="card">
        <div class="card-body">
          <div class="text-muted" th:text="${status}">STATUS</div>
          <div class="fs-3">0</div>
        </div>
      </div>
    </div>
  </div>
</main>
</body>
</html>
```

- `sec:authentication="name"` imprime el usuario autenticado.
- `th:action="@{/logout}"` + `method="post"`: el filtro de logout de Spring Security. Es
  **POST** (no un enlace GET) por CSRF.
- `th:each` recorre la lista que puso el controlador en el modelo.

### B.12 i18n: `MessageConfig` + propiedades

```java
@Configuration
public class MessageConfig {
    @Bean
    MessageSource messageSource() {
        var ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:messages/messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        return ms;
    }
}
```

```properties
login.title=Iniciar sesi\u00F3n
login.username=Usuario
login.password=Contrase\u00F1a
login.submit=Entrar
login.logout=Sesi\u00F3n cerrada
login.error=No se pudo iniciar sesi\u00F3n
login.error.badCredentials=Usuario o contrase\u00F1a incorrectos
login.error.disabled=El usuario est\u00E1 deshabilitado
login.error.locked=La cuenta est\u00E1 bloqueada

dashboard.hello=Hola
dashboard.logout=Salir
```

- `.properties` es ISO-8859-1 por defecto → usa `\uXXXX` para acentos (o configura UTF-8;
  aquí el bean ya lo hace, pero los escapes son seguros).
- Quirk de Boot 4: `spring.messages.basename` no se respetó; el bean explícito lo resuelve.
- El archivo es `messages_es.properties`; la base es `messages` y el locale `es` elige la
  variante `_es`.
- `setFallbackToSystemLocale(false)`: no cae al locale del sistema operativo.

### B.13 Diagnóstico: síntoma → causa → arreglo

| Síntoma | Causa probable | Arreglo |
|---|---|---|
| `GET /login` → 404 | Falta el controlador de login | `LoginController` (B.9) |
| `GET /login` muestra el texto "login" | El controlador devuelve `ResponseEntity` | Devuelve `String` (B.9) |
| 403 al enviar un formulario | Falta `_csrf` | Usa `th:action` (B.11) |
| 302 en bucle a `/login` | `permitAll` mal puesto o matcher que no coincide | Revisa el orden de `requestMatchers` (B.8) |
| `LazyInitializationException` | `open-in-view: false` + rol `LAZY` | `EAGER` o `@EntityGraph` (B.4) |
| Flyway falla al arrancar | Esquema desincronizado o migración editada | Crea `V3`; nunca edites `V1` (B.3) |
| El error de login reaparece | `loginError` en sesión sin limpiar | Pásalo al modelo y bórralo (8.2.2) |
| El jar contiene `.env` | `docker/` dentro de `src/main/resources` | Muévelo a `sigotrab/docker` (8.2.5) |

### B.14 Ejercicios para practicar

1. **Saludo y logout** (cierre de T1): añade "Hola, usuario" y el botón POST de logout.
   *Pista:* `sec:authentication="name"` y `th:action="@{/logout}"`.
2. **Aviso de logout**: muestra `#{login.logout}` cuando `param.logout` exista.
3. **Rol visible**: enseña un badge solo para admin con
   `sec:authorize="hasRole('ADMIN')"`.
4. **Ruta raíz**: crea un `HomeController` que redirija `/` a `/dashboard`
   (`return "redirect:/dashboard";`) y comprueba que sin sesión acaba en `/login`.
5. **Test de vista**: escribe un `@WebMvcTest(LoginController.class)` que compruebe
   `status().isOk()` y `view().name("login")`.
   *Pista Boot 4:* el import es
   `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.
6. **Test de seguridad**: con MockMvc, `GET /dashboard` sin usuario debe dar `3xx` a
   `/login`; con `@WithMockUser` debe dar `200`.
7. **Rompe a propósito**: quita `@Bean` de `securityFilterChain` y observa que la app vuelve
   al login por defecto; quita `_csrf` del formulario y observa el 403. Documenta qué pasa.

---

## Definición de "T1 terminado"

**Funcional**

- [x] `./mvnw -B -DskipTests package` en verde y `target/sigotrab-*.jar` existe.
- [x] Flyway aplica `V1`/`V2`; `\dt` lista 6 tablas + `flyway_schema_history`.
- [x] Existen los 3 usuarios demo (`dev`) con su rol.
- [x] `/login` responde 200 con formulario y CSRF.
- [x] Login correcto → `/dashboard` (200); incorrecto → `/login?error`.
- [x] `/dashboard` sin sesión → `302 /login`; `/` también.
- [x] Dashboard muestra "Bienvenido, <usuario>" y el logout POST funciona → `/login?logout`.
- [x] El error de login se muestra una vez y no reaparece.

**Configuración y empaquetado**

- [x] Un solo nombre de BD (`sigotrabdb`/`sigotrabuser`) en `application*.yml`, docker y docs.
- [x] `docker/` vive en `sigotrab/docker/`, no en `src/main/resources`; `.env` no está en git.
- [x] El jar no contiene `.env` ni `docker/`.

**Cierre**

- [ ] `git status` limpio.
- [ ] Commit `feat: esqueleto con base de datos, login y dashboard (#T1)`.

**Opcional (no bloquea T1)**

- [x] `SeedAdminUser` implementado o eliminado.
- [x] `AGENTS.md` actualizado.
- [ ] `EmbeddedPostgresSupport` + `application-test.yml` (antes de T9).
