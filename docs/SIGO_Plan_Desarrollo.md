# SIGO — Plan de desarrollo: especificación, stack, configuración, orden de tareas y skills

> Plan de desarrollo de SIGO — 2026-10-16. Fuente autoritativa de requisitos:
> `docs/SIGO_Especificacion_Proyecto.md`. Este documento **no repite** el spec: lo
> refina, resuelve ambigüedades y añade lo necesario para construir.
> El proyecto real (código, pom, migraciones) se genera siguiendo el orden de tareas del §5.
> Guía práctica paso a paso: `docs/SIGO_Tutorial_Desarrollo.md`.

---

## 0. Decisiones tomadas (resultado de la entrevista)

| # | Decisión | Elección |
|---|---|---|
| D1 | Entrega | Documento de plan + chat; scaffolding real en modo build |
| D2 | Versión base | **Spring Boot 4.1.1** (Spring Framework 7, Security 7.1.1) sobre **Java 25**, Maven |
| D3 | PostgreSQL | **PG 17** para la app: Docker para la BD de desarrollo/demo (ADR-0006) o nativo; **embedded-postgres (Zonky)** para tests (sin Docker) |
| D4 | Estados | `REGISTERED, APPROVED, IN_PROGRESS, PUBLISHED, REJECTED`; `CANCELLED` fuera |
| D5 | UI | **Thymeleaf + Bootstrap server-rendered**, PRG + flash, JS mínimo, sin HTMX/SPA; 1 endpoint JSON para adquirir |
| D6 | Idioma | UI y mensajes en **español**; identificadores/código en inglés; mensajes vía `MessageSource` |
| D7 | Módulos | **Un módulo Maven** con paquetes del spec + **ArchUnit** de guardia |
| D8 | Alcance v1 | Admin CRUD usuarios/roles + dedupe `(source_name, external_reference)` + reportes en pantalla con **export CSV** |
| D9 | Dominio | **Agregado rico** (`JobOffer` con transiciones e invariantes) |
| D10 | Concurrencia | **UPDATE nativo atómico** que contempla el bloqueo expirado; `timestamptz` en UTC + `Clock` inyectable |
| D11 | Mapeo | **Mappers manuales + records**, **sin Lombok ni MapStruct** |
| D12 | Transacciones | `@Transactional` en la capa `application` (única dependencia de Spring permitida ahí) |
| D13 | Auditoría | **Explícita** vía `AuditPort` desde los casos de uso (login/logout por listener de eventos) |
| D14 | Scheduler | `@Scheduled` simple que invoca un caso de uso; **sin ShedLock** (una instancia) |
| D15 | Seguridad | Form login + `UserDetailsService` de BD + BCrypt + CSRF + URL rules + `@PreAuthorize`; escucha en `0.0.0.0` |
| D16 | Flyway | V1 esquema, V2 roles; usuarios demo por perfil `dev` |
| D17 | Assets | **WebJars** empaquetados (Bootstrap + Bootstrap Icons) para funcionar en LAN sin internet |
| D18 | Reportes | Lado de lectura con **JdbcClient**/SQL, separado del lado de escritura JPA |
| D19 | Repo | `com.sigotrab` / `sigotrab` → `sigotrab.jar`; git trunk-based; CI GitHub Actions |
| D20 | Tests | Pirámide completa; concurrencia y timeout contra **PG real embebido** |
| D21 | Historial | Escrito **explícitamente** en el caso de uso (mismo `@Transactional`) |
| D22 | Listados | Paginación y filtros **server-side** (`Pageable` + fragmento Thymeleaf) |
| D23 | Operación | Perfiles `dev` / `lan` + `scripts/` (run, backup) + README |
| D24 | Documentación viva | `docs/adr/`, `GLOSSARY.md`, conventional commits + pre-commit |
| D25 | Skills | Base mattpocock + `anthropics/frontend-design` + `obra/superpowers` + `github/awesome-copilot` (Java/Spring/PG) |

---

## 1. Deltas y precisiones sobre el spec base

El spec es la fuente de requisitos. Estos son los puntos donde el spec es ambiguo,
está desactualizado o requiere una decisión explícita:

1. **JUnit 5 → JUnit 6.** Spring Boot 4.1.1 gestiona `junit-jupiter 6.0.3`. La API de
   tests no cambia (`org.junit.jupiter.api.*`); el spec dice "JUnit 5" por costumbre.
2. **Nombres de starters de Spring Boot 4.** El spec habla de "Spring MVC" genérico.
   Boot 4 usa starters re-nombrados: `spring-boot-starter-webmvc` (no `-web`),
   `spring-boot-starter-flyway`, y **starters de test por módulo**
   (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test`, …).
3. **`CANCELLED` fuera.** RF-09 (cancelación) vuelve a `APPROVED` con motivo; el estado
   terminal `CANCELLED` no se usa. Se documenta como no implementado.
4. **Quién captura la IP.** La aplicación no debe leer `HttpServletRequest` (acoplaría
   `application` a la web). El controlador construye un `ActorInfo(userId, username, ip)`
   y lo pasa a los casos de uso; el spec asume implícitamente acceso global al request.
5. **Doble expresión de las reglas de transición.** El spec pide el `UPDATE` atómico
   (§14) *y* reglas en el dominio (§7/§26). La regla `APPROVED → IN_PROGRESS` queda
   expresada en **dos** sitios: el método `JobOffer.acquire(...)` (in-memory, para tests
   y para el agregado) y el `WHERE` del `UPDATE` (autoridad de concurrencia). Es un
   coste deliberado; se cubre con un test de repositorio que verifica que el SQL respeta
   la misma regla. Ver ADR-0003.
6. **Historial: actor `SYSTEM`.** `JOB_OFFER_STATUS_HISTORY.changed_by` es FK nullable;
   se añade `actor` (texto) para representar `SYSTEM` sin inventar un UUID de usuario.
7. **Rechazo exige motivo** (RF-04) y **cancelación exige motivo** (RF-09): se validan en
   dominio (no solo en el formulario), lanzando `RejectionReasonRequiredException`.
8. **Serialización de la adquisición.** El spec pide `409 CONFLICT` con cuerpo JSON
   (§35). Como la UI es server-rendered, "adquirir" es el único endpoint JSON; el resto
   de acciones usan PRG + mensajes flash.
9. **CI y artefacto.** El spec pide `sigotrab.jar` (§44); se fija `artifactId=sigotrab`.
10. **Usuarios no se borran.** El admin "elimina" deshabilitando (`enabled=false`); el
    borrado físico rompería la trazabilidad. FK con `ON DELETE SET NULL`.
11. **Docker para la BD de desarrollo** (ADR-0006). El plan lo excluía; se añade un
    servicio `postgres:17` (`sigotrab`/`sigotrab`, contraseña por `.env`) manteniendo PG
    nativo como alternativa. Los tests siguen con `embedded-postgres`, sin Docker.
12. **Ciclo de vida cerrado** (ADR-0008). `REJECTED` y `PUBLISHED` son terminales; no hay
    `UNPUBLISH` ni borrado de ofertas; el rechazo solo desde `REGISTERED`.
13. **Campos obligatorios y `extracted_at`** (ADR-0007). Obligatorios: `title`, `company`,
    `description`, `requirements`, `source_url`; opcionales: `location`, `salary`,
    `employment_type`. Se añade `extracted_at` (fecha de la fuente).
14. **Marcas temporales y URL de publicación** (ADR-0010). `approved_at` se fija en la
    primera aprobación; `published_at` al publicar; `publication_url` opcional.
15. **Al deshabilitar un usuario, se liberan sus bloqueos** a `APPROVED`, auditado como
    `SYSTEM` (ADR-0011).
16. **Acceso a auditoría/reportes.** `/reports` para `ADMIN`+`REGISTRADOR`; `/audit` solo
    `ADMIN`. El `REGISTRADOR` ve la trazabilidad por oferta en `/offers/{id}`.
17. **Primer admin parametrizable** en el perfil `lan` (ADR-0009): seed idempotente solo si
    no existe ningún usuario, con contraseña por variable de entorno.

---

## 2. Stack tecnológico (versiones verificadas en Maven Central / start.spring.io)

| Componente | Tecnología | Versión |
|---|---|---|
| Lenguaje | Java (Temurin) | **25** (25.0.4 LTS, ya instalado) |
| Build | Maven | 3.9.9 (instalado) + Maven Wrapper |
| Framework | Spring Boot (Spring Framework 7) | **4.1.1** |
| Web | `spring-boot-starter-webmvc` (Spring MVC / Tomcat 11) | gestionada |
| Vistas | Thymeleaf + layout dialect | Thymeleaf 3.1.5 / layout 4.0.1 |
| UI | Bootstrap (WebJar) + Bootstrap Icons | 5.3.8 / 1.13.2 |
| Seguridad | Spring Security | 7.1.1 |
| Persistencia | Spring Data JPA + Hibernate | Hibernate 7.4.5.Final |
| BD producción/demo | PostgreSQL | 17 (nativo) |
| BD tests | `io.zonky.test:embedded-postgres` | 2.2.2 (+ binarios PG 17.5.0) |
| Migraciones | Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`) | 12.4.0 |
| Driver | `org.postgresql:postgresql` | 42.7.13 |
| Scheduler | Spring Scheduler (`@Scheduled`) | — |
| Logging | SLF4J + Logback | gestionada |
| Testing | JUnit Jupiter **6.0.3**, Mockito 5.23, AssertJ, Spring Security Test | gestionada |
| Arquitectura | ArchUnit | 1.5.1 |
| CI | GitHub Actions (`actions/setup-java` temurin 25) | — |

> **Por qué PG 17 nativo:** la versión de PostgreSQL embebida para tests es **17.5.0**.
> Instalar PG 17 nativo en la máquina de demo iguala entornos (menos "pasa en test, falla
> en demo"). Si prefieres PG 18, es válido, pero queda como decisión consciente.

---

## 3. Estructura del proyecto

```
sigotrab/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/
├── .gitignore
├── .pre-commit-config.yaml
├── .github/workflows/ci.yml
├── README.md
├── GLOSSARY.md
├── docs/
│   ├── SIGO_Especificacion_Proyecto.md      (el spec original)
│   └── adr/0001-..0011-*.md
├── scripts/
│   ├── run.ps1 / run.sh
│   └── backup.ps1 / backup.sh
└── src/
    ├── main/
    │   ├── java/com/sigotrab/
    │   │   ├── SigotrabApplication.java
    │   │   ├── domain/
    │   │   │   ├── model/        JobOffer, JobStatus, ActorInfo, AuditAction, AuditRecord
    │   │   │   ├── exception/    InvalidTransitionException, JobLockedException,
    │   │   │   │                 JobNotFoundException, InvalidJobStateException,
    │   │   │   │                 UnauthorizedJobOperationException, RejectionReasonRequiredException
    │   │   │   └── service/      JobWorkflow (opcional: reglas reutilizables)
    │   │   ├── application/
    │   │   │   ├── port/in/      RegisterJobOfferUseCase, UpdateJobOfferUseCase,
    │   │   │   │                 ApproveJobOfferUseCase, RejectJobOfferUseCase,
    │   │   │   │                 AcquireJobOfferUseCase, ReleaseJobOfferUseCase,
    │   │   │   │                 PublishJobOfferUseCase, ReleaseExpiredOffersUseCase,
    │   │   │   │                 GetJobOfferHistoryUseCase, GetAuditEventsUseCase, ...
    │   │   │   ├── port/out/     JobOfferRepository, UserRepository, AuditPort,
    │   │   │   │                 StatusHistoryPort, ReportQueries, Clock (JDK)
    │   │   │   └── service/      *Service (implementan los puertos in) @Transactional
    │   │   ├── adapters/
    │   │   │   ├── web/
    │   │   │   │   ├── controller/  (MVC)  + api/ (AcquireController JSON)
    │   │   │   │   ├── dto/         records de formularios y vistas
    │   │   │   │   ├── mapper/      DTO ↔ dominio
    │   │   │   │   └── error/       ApiExceptionHandler, WebExceptionHandler
    │   │   │   └── persistence/
    │   │   │       ├── entity/      JobOfferJpaEntity, UserJpaEntity, ...
    │   │   │       ├── repository/  Spring Data interfaces (+ UPDATE atómico)
    │   │   │       └── mapper/      entidad ↔ dominio
    │   │   └── infrastructure/
    │   │       ├── security/     SecurityConfig, SigUserDetailsService, AuthAuditListener
    │   │       ├── scheduler/    ExpiredOfferScheduler
    │   │       ├── audit/        JpaAuditAdapter, JpaStatusHistoryAdapter
    │   │       └── config/       ClockConfig, LockProperties, SeedDevUsers
    │   └── resources/
    │       ├── application.yml, application-dev.yml, application-lan.yml
    │       ├── db/migration/     V1__baseline.sql, V2__seed_roles.sql
    │       ├── messages/messages_es.properties
    │       ├── static/css/app.css
    │       └── templates/        layout.html, login.html, dashboard.html, offers/*, audit.html, reports/*
    └── test/java/com/sigotrab/
        ├── domain/               JobStatusTransitionTest, JobOfferTest
        ├── application/          *ServiceTest (Mockito)
        ├── adapters/web/         *ControllerTest (@WebMvcTest), SecurityRulesTest
        ├── adapters/persistence/ JobOfferRepositoryTest (@DataJpaTest)
        ├── architecture/         HexagonalArchitectureTest (ArchUnit)
        └── support/              EmbeddedPostgresSupport
```

---

## 4. Configuración e inicialización (ejemplos)

### 4.1 Inicialización del proyecto

```bash
# 1) Andamiaje (equivalente a start.spring.io)
curl -G https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d bootVersion=4.1.1 -d javaVersion=25 \
  -d groupId=com.sigotrab -d artifactId=sigotrab -d name=SIGO \
  -d packageName=com.sigotrab -d packaging=jar \
  -d dependencies=web,thymeleaf,security,data-jpa,validation,flyway,postgresql \
  -o sigo.zip

# 2) Maven Wrapper (para no depender del Maven local)
mvn -N wrapper:wrapper -Dmaven=3.9.9

# 3) Repositorio
git init && git add -A && git commit -m "chore: andamiaje inicial de SIGO"
```

### 4.2 `pom.xml` (fragmento relevante)

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.1.1</version>
  <relativePath/>
</parent>

<groupId>com.sigotrab</groupId>
<artifactId>sigotrab</artifactId>
<version>0.0.1-SNAPSHOT</version>
<name>SIGO</name>

<properties>
  <java.version>25</java.version>
  <archunit.version>1.5.1</archunit.version>
  <embedded-postgres.version>2.2.2</embedded-postgres.version>
  <embedded-postgres-binaries.version>17.5.0</embedded-postgres-binaries.version>
</properties>

<dependencies>
  <!-- Web / vistas / seguridad / datos -->
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-thymeleaf</artifactId></dependency>
  <dependency><groupId>org.thymeleaf.extras</groupId><artifactId>thymeleaf-extras-springsecurity6</artifactId></dependency>
  <dependency><groupId>nz.net.ultraq.thymeleaf</groupId><artifactId>thymeleaf-layout-dialect</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>

  <!-- Migraciones y driver -->
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
  <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>

  <!-- Assets offline (LAN) -->
  <dependency><groupId>org.webjars</groupId><artifactId>webjars-locator-lite</artifactId></dependency>
  <dependency><groupId>org.webjars</groupId><artifactId>bootstrap</artifactId><version>5.3.8</version></dependency>
  <dependency><groupId>org.webjars.npm</groupId><artifactId>bootstrap-icons</artifactId><version>1.13.2</version></dependency>

  <!-- Test (starters por módulo en Boot 4) -->
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>com.tngtech.archunit</groupId><artifactId>archunit-junit5</artifactId><version>${archunit.version}</version><scope>test</scope></dependency>
  <dependency><groupId>io.zonky.test</groupId><artifactId>embedded-postgres</artifactId><version>${embedded-postgres.version}</version><scope>test</scope></dependency>
  <dependency><groupId>io.zonky.test.postgres</groupId><artifactId>embedded-postgres-binaries-windows-amd64</artifactId><version>${embedded-postgres-binaries.version}</version><scope>test</scope></dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```

### 4.3 Configuración externalizada

`src/main/resources/application.yml`
```yaml
spring:
  application:
    name: sigo
  datasource:
    url: jdbc:postgresql://localhost:5432/sigotrabdb
    username: sigotrabuser
    password: ${SIGOTRAB_DB_PASSWORD:change-me}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate          # Flyway es la autoridad del esquema (§17)
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    locations: classpath:db/migration
  thymeleaf:
    cache: true
server:
  port: 8080
sigotrab:
  lock:
    duration-minutes: 20          # §43; demo => 2 (application-dev.yml)
  scheduler:
    fixed-delay-ms: 60000
```

`application-dev.yml`
```yaml
spring:
  thymeleaf.cache: false
sigotrab:
  lock:
    duration-minutes: 2           # §43: demo = 2 minutos
logging:
  level:
    com.evela.sigotrab: DEBUG
```

`application-lan.yml`
```yaml
server:
  address: 0.0.0.0                # accesible desde la LAN
  port: 8080
spring:
  datasource:
    url: jdbc:postgresql://${SIGOTRAB_DB_HOST:localhost}:5432/${SIGOTRAB_DB_NAME:sigotrabdb}
    username: ${SIGOTRAB_DB_USER:sigotrabuser}
    password: ${SIGOTRAB_DB_PASSWORD}
sigotrab:
  lock:
    duration-minutes: 20
  admin:
    username: ${SIGOTRAB_ADMIN_USERNAME:admin}
    password: ${SIGOTRAB_ADMIN_PASSWORD:}
    full-name: ${SIGOTRAB_ADMIN_FULL_NAME:Administrador}
```

Enlace de propiedades (record, sin setters):
```java
@ConfigurationProperties("sigotrab.lock")
public record LockProperties(long durationMinutes) {}

@ConfigurationProperties("sigotrab.scheduler")
public record SchedulerProperties(long fixedDelayMs) {}

@ConfigurationProperties("sigotrab.admin")
public record AdminProperties(String username, String password, String fullName) {}
// @ConfigurationPropertiesScan en la clase principal, o @EnableConfigurationProperties
```

### 4.4 Migraciones Flyway

`db/migration/V1__baseline.sql`
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

create table job_offer (
    id                 uuid primary key default gen_random_uuid(),
    title              varchar(200) not null,
    company            varchar(150) not null,
    description        text         not null,
    requirements       text         not null,
    location           varchar(150),
    salary             varchar(100),
    employment_type    varchar(50),
    source_url         varchar(500) not null,
    source_name        varchar(60),
    external_reference varchar(120),
    extracted_at       timestamptz  not null,
    publication_url    varchar(500),
    status             varchar(20)  not null,
    assigned_user_id   uuid references users(id) on delete set null,
    locked_at          timestamptz,
    lock_expires_at    timestamptz,
    approved_at        timestamptz,
    published_at       timestamptz,
    created_at         timestamptz  not null default now(),
    created_by         uuid references users(id) on delete set null,
    updated_at         timestamptz  not null default now(),
    updated_by         uuid references users(id) on delete set null,
    version            bigint       not null default 0,
    constraint job_offer_status_chk
        check (status in ('REGISTERED','APPROVED','IN_PROGRESS','PUBLISHED','REJECTED')),
    constraint job_offer_lock_chk
        check ((status =  'IN_PROGRESS' and assigned_user_id is not null
                                          and locked_at is not null
                                          and lock_expires_at is not null)
            or (status <> 'IN_PROGRESS' and assigned_user_id is null
                                          and locked_at is null
                                          and lock_expires_at is null)),
    constraint job_offer_dedupe_uk unique (source_name, external_reference)  -- §24
);

create index idx_job_offer_status       on job_offer (status);
create index idx_job_offer_assigned     on job_offer (assigned_user_id);
create index idx_job_offer_lock_expires on job_offer (lock_expires_at);
create index idx_job_offer_company      on job_offer (company);

create table job_offer_status_history (
    id           bigserial primary key,
    job_offer_id uuid not null references job_offer(id) on delete cascade,
    old_status   varchar(20) not null,
    new_status   varchar(20) not null,
    changed_by   uuid references users(id) on delete set null,
    actor        varchar(60) not null default 'SYSTEM',   -- 'SYSTEM' o el username
    changed_at   timestamptz not null default now(),
    reason       varchar(300)
);
create index idx_history_offer on job_offer_status_history (job_offer_id, changed_at);

create table audit_event (
    id          bigserial primary key,
    occurred_at timestamptz not null default now(),
    user_id     uuid,
    username    varchar(60),
    action      varchar(30) not null,
    entity_type varchar(50),
    entity_id   varchar(80),
    old_values  jsonb,
    new_values  jsonb,
    ip_address  inet,
    success     boolean not null,
    error_code  varchar(60),
    reason      varchar(300)
);
create index idx_audit_occurred on audit_event (occurred_at desc);
create index idx_audit_entity   on audit_event (entity_type, entity_id);
create index idx_audit_action   on audit_event (action);
create index idx_audit_user     on audit_event (user_id);
```

`db/migration/V2__seed_roles.sql`
```sql
insert into roles (id, name) values
  ('11111111-1111-1111-1111-111111111111', 'ROLE_ADMIN'),
  ('22222222-2222-2222-2222-222222222222', 'ROLE_REGISTRADOR'),
  ('33333333-3333-3333-3333-333333333333', 'ROLE_PUBLICADOR')
on conflict (name) do nothing;
```

**Usuarios demo (refinamiento de D16):** en vez de hashes BCrypt incrustados en SQL, se
siembran con un `ApplicationRunner` bajo `@Profile("dev")` que usa el `PasswordEncoder`.
Motivo: no dejar hashes/contraseñas fijos en migraciones y permitir cambiarlas por config.
Se documenta en ADR-0005.

```java
@Component
@Profile("dev")
class SeedDevUsers implements ApplicationRunner {
    // admin/admin123, registrador/registrador123, publicador/publicador123
    // idempotente: si el usuario existe, no hace nada (usa PasswordEncoder)
}
```

### 4.5 Dominio (agregado rico, sin frameworks)

```java
public enum JobStatus {
    REGISTERED, APPROVED, IN_PROGRESS, PUBLISHED, REJECTED;

    private static final Map<JobStatus, Set<JobStatus>> ALLOWED = Map.of(
        REGISTERED,  Set.of(APPROVED, REJECTED),
        APPROVED,    Set.of(IN_PROGRESS),
        IN_PROGRESS, Set.of(PUBLISHED, APPROVED),
        PUBLISHED,   Set.of(),
        REJECTED,    Set.of());

    public boolean canTransitionTo(JobStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
```

```java
public final class JobOffer {                 // sin anotaciones Spring/JPA
    private final UUID id;
    private String title, company, description, requirements, location, salary, employmentType;
    private String originalSourceUrl;
    private JobStatus status;
    private UUID assignedUserId;
    private Instant lockedAt, lockExpiresAt, approvedAt, publishedAt;
    private final Instant createdAt;
    private final UUID createdBy;
    private Instant updatedAt;
    private UUID updatedBy;

    public static JobOffer register(NewJobOffer data, UUID actor, Instant now) { /* status = REGISTERED */ }

    public void updateContent(JobOfferContent c, UUID actor, Instant now) {
        if (status != JobStatus.REGISTERED) throw new InvalidJobStateException(id, status, "update");
        // ... + updatedAt/updatedBy
    }

    public void approve(UUID actor, Instant now) {
        transitionTo(JobStatus.APPROVED, actor, now);
        this.approvedAt = now;
    }

    public void reject(UUID actor, String reason, Instant now) {
        if (reason == null || reason.isBlank()) throw new RejectionReasonRequiredException(id);
        transitionTo(JobStatus.REJECTED, actor, now);
    }

    public void acquire(UUID userId, Duration lockDuration, Instant now) {   // usada in-memory/tests
        transitionTo(JobStatus.IN_PROGRESS, userId, now);
        this.assignedUserId = userId;
        this.lockedAt = now;
        this.lockExpiresAt = now.plus(lockDuration);
    }

    public void release(UUID actor, String reason, Instant now) {           // timeout o cancelación
        transitionTo(JobStatus.APPROVED, actor, now);
        clearLock();
    }

    public void publish(UUID userId, Instant now) {
        if (!isAssignedTo(userId)) throw new UnauthorizedJobOperationException(id, userId);
        transitionTo(JobStatus.PUBLISHED, userId, now);
        this.publishedAt = now;
        clearLock();
    }

    public boolean isLockExpired(Instant now)   { return status == IN_PROGRESS && lockExpiresAt.isBefore(now); }
    public boolean isAssignedTo(UUID userId)    { return userId != null && userId.equals(assignedUserId); }

    private void transitionTo(JobStatus target, UUID actor, Instant now) {
        if (!status.canTransitionTo(target)) throw new InvalidTransitionException(id, status, target);
        this.status = target;
        this.updatedAt = now;
        this.updatedBy = actor;
    }
    private void clearLock() { assignedUserId = null; lockedAt = null; lockExpiresAt = null; }
}
```

### 4.6 Puertos

```java
// application/port/in
public interface AcquireJobOfferUseCase {
    JobOffer acquire(UUID jobOfferId, ActorInfo actor);   // §28, ampliado con ActorInfo
}

// application/port/out
public interface JobOfferRepository {
    Optional<JobOffer> findById(UUID id);
    PageResult<JobOffer> findByStatus(JobStatus status, PageQuery page);
    boolean existsBySourceReference(String sourceName, String externalReference);
    boolean acquire(UUID jobOfferId, UUID assignedUserId, Instant now, Instant expiresAt); // §29
    void save(JobOffer jobOffer);
}

public interface AuditPort {
    void record(AuditRecord event);            // §9
}

public interface StatusHistoryPort {
    void record(UUID offerId, JobStatus from, JobStatus to, ActorInfo actor, String reason, Instant now);
}
```

`ActorInfo` (dominio/aplicación, sin web):
```java
public record ActorInfo(UUID userId, String username, String ipAddress) {
    public static ActorInfo system() { return new ActorInfo(null, "SYSTEM", null); }
}
```

### 4.7 Adaptador de persistencia + adquisición atómica

```java
public interface JobOfferJpaRepository extends JpaRepository<JobOfferJpaEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        update job_offer
           set status           = 'IN_PROGRESS',
               assigned_user_id = :userId,
               locked_at        = :now,
               lock_expires_at  = :expiresAt,
               version          = version + 1,
               updated_at       = :now,
               updated_by       = :userId
         where id = :id
           and (status = 'APPROVED'
                or (status = 'IN_PROGRESS' and lock_expires_at < :now))
        """, nativeQuery = true)
    int acquire(@Param("id") UUID id,
                @Param("userId") UUID userId,
                @Param("now") Instant now,
                @Param("expiresAt") Instant expiresAt);
}
```

```java
@Repository
public class JobOfferPersistenceAdapter implements JobOfferRepository {
    private final JobOfferJpaRepository jpa;
    private final JobOfferEntityMapper mapper;

    @Override public Optional<JobOffer> findById(UUID id) { return jpa.findById(id).map(mapper::toDomain); }

    @Override public boolean acquire(UUID id, UUID userId, Instant now, Instant expiresAt) {
        return jpa.acquire(id, userId, now, expiresAt) == 1;   // 1 = éxito, 0 = conflicto (§14)
    }

    @Override public void save(JobOffer offer) { jpa.save(mapper.toEntity(offer)); }
}
```

> `@Version` sigue en la entidad JPA (detección de modificaciones concurrentes, §15). El
> `UPDATE` nativo incrementa `version` a mano para no romperlo.

### 4.8 Caso de uso de adquisición (auditoría + historial en la misma transacción)

```java
@Service
@Transactional
public class AcquireJobOfferService implements AcquireJobOfferUseCase {
    private final JobOfferRepository offers;
    private final AuditPort audit;
    private final StatusHistoryPort history;
    private final Clock clock;
    private final LockProperties lock;

    @Override
    public JobOffer acquire(UUID jobOfferId, ActorInfo actor) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(lock.durationMinutes()));

        if (!offers.acquire(jobOfferId, actor.userId(), now, expiresAt)) {
            audit.record(AuditRecord.failure(AuditAction.ACQUIRE, "JOB_OFFER",
                    jobOfferId.toString(), actor, "JOB_ALREADY_LOCKED"));
            throw new JobLockedException(jobOfferId);          // → 409 (§35)
        }
        JobOffer offer = offers.findById(jobOfferId)
                .orElseThrow(() -> new JobNotFoundException(jobOfferId));
        history.record(jobOfferId, JobStatus.APPROVED, JobStatus.IN_PROGRESS, actor, "Adquisición", now);
        audit.record(AuditRecord.success(AuditAction.ACQUIRE, "JOB_OFFER", jobOfferId.toString(), actor));
        return offer;
    }
}
```

Liberación por timeout (invocada por el scheduler; también audita `SYSTEM`):
```java
@Transactional
public int releaseExpired() {
    Instant now = clock.instant();
    List<UUID> expired = offers.findExpiredInProgressIds(now);
    for (UUID id : expired) {
        offers.release(id, now);                                // UPDATE masivo/atómico
        history.record(id, IN_PROGRESS, APPROVED, ActorInfo.system(), "TIMEOUT", now);
        audit.record(AuditRecord.system(AuditAction.TIMEOUT_RELEASE, "JOB_OFFER", id.toString()));
    }
    return expired.size();
}
```

### 4.9 Seguridad

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
            .formLogin(form -> form.loginPage("/login")
                .defaultSuccessUrl("/dashboard", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
            .csrf(Customizer.withDefaults());
        return http.build();
    }

    @Bean PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean Clock clock() { return Clock.systemUTC(); }
}
```

`AuthAuditListener` (`@EventListener`) registra `AuthenticationSuccessEvent`,
`AbstractAuthenticationFailureEvent` y logout → acciones `LOGIN`/`LOGOUT` (§10).

### 4.10 Web

Endpoint JSON de adquisición (muestra el `409` sin recargar):
```java
@RestController
@RequestMapping("/api/offers")
class AcquireJobOfferController {

    @PostMapping("/{id}/acquire")
    ResponseEntity<AcquireResponse> acquire(@PathVariable UUID id,
                                            @AuthenticationPrincipal SigUser user,
                                            HttpServletRequest request) {
        var actor = new ActorInfo(user.getId(), user.getUsername(), request.getRemoteAddr());
        JobOffer offer = acquireJobOffer.acquire(id, actor);
        return ResponseEntity.ok(new AcquireResponse(offer.id(), offer.status(), offer.lockExpiresAt()));
    }
}
```

```java
@RestControllerAdvice(basePackageClasses = AcquireJobOfferController.class)
class ApiExceptionHandler {
    @ExceptionHandler(JobLockedException.class)
    ResponseEntity<ApiError> locked(JobLockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("JOB_ALREADY_LOCKED",
                "La oferta ya está siendo procesada por otro usuario."));   // §35
    }
    // InvalidTransitionException → 409, JobNotFoundException → 404,
    // UnauthorizedJobOperationException → 403, validación → 400, resto → 500
}
```

Plantilla base con layout dialect + WebJars (funciona sin internet):
```html
<!-- templates/layout.html -->
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
```html
<!-- templates/dashboard.html -->
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{layout :: layout(~{::title}, ~{::main})}">
<head><title>Panel — SIGO</title></head>
<body><main>… tarjetas REGISTERED / APPROVED / IN_PROGRESS / PUBLISHED / REJECTED …</main></body>
</html>
```

> Filtros de estado y paginación (D22): `?status=&page=&size=&sort=` con un fragmento
> `fragments/pagination.html` reutilizable. El conteo de "tiempo restante" se hace con un
> pequeño JS que lee `data-expires-at` (ISO-8601 UTC) del modelo.

### 4.11 Scheduler

```java
@Component
class ExpiredOfferScheduler {
    private final ReleaseExpiredOffersUseCase releaseExpiredOffers;
    private static final Logger log = LoggerFactory.getLogger(ExpiredOfferScheduler.class);

    @Scheduled(fixedDelayString = "${sigotrab.scheduler.fixed-delay-ms}")
    void releaseExpired() {
        int released = releaseExpiredOffers.releaseExpired();
        if (released > 0) log.info("Ofertas liberadas por timeout: {}", released);
    }
}
```

### 4.12 Pruebas

**Concurrencia (PG real embebido, no H2):**
```java
@SpringBootTest
@ActiveProfiles("test")
class AcquireJobOfferConcurrencyTest {

    static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) throws IOException {
        if (pg == null) pg = EmbeddedPostgres.builder().start();
        registry.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired AcquireJobOfferUseCase acquire;
    @Autowired JobOfferFixture fixture;      // helper de test: crea oferta APPROVED

    @Test
    void solo_un_publicador_adquiere_la_misma_oferta() throws Exception {
        for (int round = 0; round < 20; round++) {
            UUID offerId = fixture.approvedOffer();
            UUID a = fixture.publisher("ana"), b = fixture.publisher("beto");
            var ready = new CyclicBarrier(2);
            var results = new CopyOnWriteArrayList<String>();
            try (var pool = Executors.newFixedThreadPool(2)) {
                for (UUID userId : List.of(a, b)) {
                    pool.submit(() -> {
                        ready.await();
                        try { acquire.acquire(offerId, new ActorInfo(userId, "u", "127.0.0.1"));
                              results.add("OK"); }
                        catch (JobLockedException e) { results.add("CONFLICT"); }
                        return null;
                    });
                }
                pool.shutdown();
                assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
            }
            assertThat(results).containsExactlyInAnyOrder("OK", "CONFLICT");   // §40
        }
    }
}
```

**Transiciones del dominio (§39):**
```java
@ParameterizedTest
@CsvSource({
  "REGISTERED,  APPROVED,    true",
  "REGISTERED,  PUBLISHED,   false",
  "APPROVED,    IN_PROGRESS, true",
  "APPROVED,    PUBLISHED,   false",
  "IN_PROGRESS, PUBLISHED,   true",
  "IN_PROGRESS, APPROVED,    true",
  "PUBLISHED,   APPROVED,    false",
  "PUBLISHED,   IN_PROGRESS, false"
})
void transiciones(JobStatus from, JobStatus to, boolean allowed) {
    assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
}
```

**Timeout (§41):** con `Clock` fijo, oferta `IN_PROGRESS` con `lock_expires_at < now`;
ejecutar el caso de uso y verificar `status = APPROVED`, lock en `NULL` y `AUDIT_EVENT`
con `action = TIMEOUT_RELEASE`, `username = SYSTEM`.

**Seguridad (§42)** con `MockMvc` + `@WithMockUser`:
`REGISTRADOR` → 200 en aprobar / 403 en adquirir / 403 en publicar;
`PUBLICADOR` → 200 en adquirir / 403 en aprobar.

**Arquitectura (ArchUnit):**
```java
@AnalyzeClasses(packages = "com.sigotrab")
class HexagonalArchitectureTest {
    @ArchTest static final ArchRule dominio_puro = noClasses()
        .that().resideInAPackage("..domain..").should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                            "org.hibernate..", "org.thymeleaf..");

    @ArchTest static final ArchRule application_no_depende_de_adapters = noClasses()
        .that().resideInAPackage("..application..").should().dependOnClassesThat()
        .resideInAPackage("..adapters..");
}
```

### 4.13 CI

`.github/workflows/ci.yml`
```yaml
name: ci
on: [push, pull_request]
jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '25', cache: maven }
      - run: ./mvnw -B verify
```

---

## 5. Orden de tareas (tickets tracer-bullet con dependencias)

Cada ticket es una **rebanada vertical** (schema + dominio + aplicación + web + tests),
demoable por sí sola. "Bloqueado por" son las aristas del grafo.

| Ticket | Entrega demoable | Bloqueado por |
|---|---|---|
| **T0 — Andamiaje** | Proyecto Maven (pom, wrapper), `git init`, `.gitignore`, CI en verde vacío, `GLOSSARY.md` + ADR-0001..0003 | — |
| **T1 — Esqueleto ejecutable** | App arranca; V1+V2 Flyway; `/login` con seguridad y usuarios `dev`; layout Bootstrap (WebJars); `/dashboard` vacío | T0 |
| **T2 — Ciclo de vida del dominio** | `JobOffer`/`JobStatus`/excepciones + tests de transiciones (§39) | T0 |
| **T3 — Registrar oferta** | `REGISTRADOR` crea oferta → `REGISTERED`; `/offers`, `/offers/new`, `/offers/{id}`, `/offers/registered`; dedupe `(source_name, external_reference)`; auditoría `CREATE` | T1, T2 |
| **T4 — Curar/editar oferta** | Edición solo en `REGISTERED`; auditoría `UPDATE` con `old/new` | T3 |
| **T5 — Aprobar / rechazar** | `REGISTERED → APPROVED` y `→ REJECTED` (motivo obligatorio); historial + auditoría | T4 |
| **T6 — Adquirir (atómico)** | `PUBLICADOR` adquiere; `UPDATE` atómico; `/offers/approved`; endpoint JSON + `409 JOB_ALREADY_LOCKED` | T5 |
| **T7 — Bloqueo, cancelar y publicar** | `/offers/in-progress` con tiempo restante; cancelar `IN_PROGRESS → APPROVED` (motivo); publicar `→ PUBLISHED`; `/offers/published` | T6 |
| **T8 — Timeout** | Scheduler libera expiradas; auditoría `SYSTEM`/`TIMEOUT_RELEASE` | T6 |
| **T9 — Integración concurrencia/timeout** | Test de 2 hilos contra PG embebido (§40) + test de timeout (§41) | T6, T8 |
| **T10 — Trazabilidad de la oferta** | `/offers/{id}` muestra la línea de tiempo (historial + auditoría) | T7 |
| **T11 — Auditoría consultable** | `/audit` con filtros (usuario, acción, entidad, fecha) + paginación | T5 |
| **T12 — Reportes** | `/reports`: ofertas, publicaciones (con tiempo de procesamiento), actividad; **export CSV** | T7 |
| **T13 — Dashboard y métricas** | Conteos por estado + tiempos (`approved_at-created_at`, `published_at-approved_at`, `published_at-locked_at`) | T2 |
| **T14 — Admin usuarios/roles** | CRUD de usuarios (alta, edición, habilitar/deshabilitar) y asignación de roles; auditoría | T1 |
| **T15 — Operación LAN** | `application-lan.yml`, `scripts/run.*`, `scripts/backup.*`, README de instalación | T1 |
| **T16 — Endurecer seguridad** | Tests §42 completos; revisión de autorización backend por endpoint; CSRF verificado | T7, T14 |
| **T17 — Cierre** | Checklist §50 al 100 %; ADRs finales; tag `v1.0` | todos |

**Ruta crítica:** `T0 → T1 → T3 → T4 → T5 → T6 → T7 → T9 → T17`.
**Paralelizables en cuanto existan T1/T2:** T13, T14, T15.

---

## 6. Estrategia de pruebas (pirámide y costuras)

| Nivel | Herramienta | Qué cubre |
|---|---|---|
| Dominio | JUnit puro | Transiciones (§39), invariantes, motivos obligatorios |
| Aplicación | JUnit + Mockito | Orquestación de casos de uso, auditoría/historial, mapeo de errores |
| Persistencia | `@DataJpaTest` + PG embebido | `UPDATE` atómico, constraints, dedupe, mapeo entidad↔dominio |
| Web | `@WebMvcTest` + MockMvc | Rutas, PRG, flash, validación, `409` |
| Seguridad | MockMvc + `@WithMockUser` | Matriz de permisos (§42) |
| Integración | `@SpringBootTest` + PG embebido | Concurrencia (§40), timeout (§41), flujo E2E feliz |
| Arquitectura | ArchUnit | El dominio no depende de frameworks |

> Costuras preferidas (pocas y altas): **(1)** el caso de uso (puertos `in`) para el
> comportamiento; **(2)** los adaptadores de persistencia para la concurrencia real. El
> dominio se prueba sin Spring.

---

## 7. Auditoría y trazabilidad

- `AuditPort.record(AuditRecord)` con `action`, `entity_type`, `entity_id`, `old_values`/
  `new_values` (JSONB), `success`, `error_code`, `ip_address`, `actor`.
- Catálogo de acciones (§10) como `enum AuditAction`.
- `StatusHistoryPort` escribe `JOB_OFFER_STATUS_HISTORY` en la misma transacción.
- Se auditan también **intentos fallidos** (p. ej. `ACQUIRE` con conflicto) con
  `success=false` y `error_code=JOB_ALREADY_LOCKED`.
- Login/logout vía `@EventListener` (no vía interceptor de dominio).
- Logging técnico (SLF4J/Logback) separado de la auditoría funcional (§37).
- La vista `/offers/{id}` une historial + auditoría para la línea de tiempo (§12).

---

## 8. Operación

```bash
# demo local (perfil dev: lock de 2 min, usuarios demo)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# empaquetado y ejecución como en producción
./mvnw -DskipTests package
java -jar target/sigotrab.jar --spring.profiles.active=lan
#   → http://IP-DEL-SERVIDOR:8080
```

`scripts/backup.ps1`
```powershell
param([string]$Db = "sigotrabdb", [string]$Out = "backups")
$stamp = Get-Date -Format "yyyy-MM-dd_HHmm"
New-Item -ItemType Directory -Force -Path $Out | Out-Null
pg_dump -Fc -d $Db -f "$Out/sigotrab_$stamp.dump"
```

Pendiente de la máquina: **instalar PostgreSQL 17** y crear el rol/BD:
```sql
CREATE ROLE sigotrabuser LOGIN PASSWORD 'change-me';
CREATE DATABASE sigotrabdb OWNER sigotrabuser;
```

---

## 9. Skills recomendados

Instalación (CLI `skills`, compatible con OpenCode). Revisar el contenido de cada skill
antes de confiar en él (`npx skills add <repo> --list`).

**Base de proceso (Matt Pocock):**
```bash
npx skills add mattpocock/skills -a opencode --skill grill-with-docs --skill grilling \
  --skill to-spec --skill to-tickets --skill implement-spec --skill implement \
  --skill tdd --skill code-review --skill domain-modeling --skill codebase-design \
  --skill diagnosing-bugs --skill handoff
```
> Nota: `grill-me` está instalado pero invoca la skill `grilling`, que **no** lo está.
> Instalar `grilling` deja `@grill-me` funcionando, y `grill-with-docs` es la variante
> que deja rastro (`GLOSSARY.md` + ADRs) cuando hay repositorio.

**Backend:**
```bash
npx skills add github/awesome-copilot -a opencode \
  --skill java-springboot --skill java-junit --skill postgresql-optimization
# opcional (revisar antes): affaan-m/ecc --skill hexagonal-architecture
```

**Frontend:**
```bash
npx skills add anthropics/skills -a opencode --skill frontend-design
# opcional: vercel-labs/agent-skills --skill web-design-guidelines
```

**Testing:**
```bash
npx skills add obra/superpowers -a opencode \
  --skill test-driven-development --skill verification-before-completion \
  --skill systematic-debugging
```

Mapa por área:

| Área | Skills |
|---|---|
| Proceso | `grill-with-docs`, `to-spec`, `to-tickets`, `implement-spec`, `code-review`, `handoff` |
| Backend | `codebase-design`, `domain-modeling`, `java-springboot`, `postgresql-optimization`, `diagnosing-bugs` |
| Frontend | `frontend-design` (+ `web-design-guidelines`) |
| Test | `tdd`, `test-driven-development`, `verification-before-completion`, `java-junit` |

---

## 10. Convenciones

- **Git:** trunk-based; ramas cortas `feat/T6-adquirir`; PR con plantilla; squash merge.
- **Commits:** Conventional Commits (`feat`, `fix`, `refactor`, `test`, `docs`, `chore`).
- **Pre-commit:** formato + verificación rápida (skill `setup-pre-commit`).
- **ADRs iniciales:** `docs/adr/`
  - `0001-@Transactional-en-application.md`
  - `0002-update-atomico-para-adquisicion.md`
  - `0003-regla-de-transicion-en-dominio-y-sql.md`
  - `0004-webjars-para-operacion-lan.md`
  - `0005-sembrado-de-usuarios-demo.md`
  - `0006-docker-para-base-de-datos-de-desarrollo.md`
  - `0007-campos-obligatorios-y-fecha-de-extraccion.md`
  - `0008-ciclo-de-vida-cerrado.md`
  - `0009-primer-administrador-parametrizable.md`
  - `0010-marcas-temporales-y-url-de-publicacion.md`
  - `0011-deshabilitar-usuario-libera-bloqueos.md`
- **GLOSSARY.md:** Oferta (`JobOffer`), Adquisición (`acquire`), Bloqueo (`lock`),
  Liberación (`release`), Publicación (`publish`), Registrador/Publicador/Admin, Timeout.

---

## 11. Riesgos y decisiones diferidas

| Riesgo | Mitigación |
|---|---|
| Novedades de Spring Boot 4 (starters y anotaciones de test movidas) | Ya verificado; usar exactamente las coordenadas de §2/§4 |
| Divergencia PG 17 (embebido) vs PG 18 | Se instala PG 17 nativo para igualar |
| `thymeleaf-extras-springsecurity6` con Security 7 | Spring Boot 4.1.1 la **gestiona** (compatibilidad asumida); caer a control de visibilidad por modelo si molesta |
| Regla de transición duplicada (dominio + SQL) | ADR-0003 + test de repositorio que verifica el `WHERE` |
| `gen_random_uuid()` requiere PG ≥ 13 | PG 17 lo trae nativo; no hace falta `pgcrypto` |
| CI en Linux vs demo en Windows | Binarios embebidos: añadir `embedded-postgres-binaries-linux-amd64` (perfil CI) |

**Fuera de alcance (spec §45):** Nginx, HTTPS, API REST pública, notificaciones,
almacenamiento de documentos, backup automatizado, integración con fuentes/portal. (Docker
para la BD de desarrollo queda **dentro** de alcance, ADR-0006.)

---

## 12. Checklist de aceptación (§50) → dónde se verifica

| Criterio | Ticket | Test |
|---|---|---|
| Crear oferta, inicia `REGISTERED` | T3 | `@WebMvcTest` + E2E |
| Editar y quedar auditado | T4 | `@DataJpaTest` auditoría |
| Aprobar; solo `APPROVED` visible a publicadores | T5 | `@WebMvcTest` seguridad |
| Adquirir atómico; dos publicadores no pueden | T6/T9 | `AcquireJobOfferConcurrencyTest` |
| Usuario asignado + expiración + tiempo restante | T6/T7 | MockMvc + JS |
| Expirada vuelve a `APPROVED` con auditoría `SYSTEM` | T8/T9 | Test de timeout |
| Cancelar y confirmar publicación | T7 | MockMvc |
| Publicada no vuelve al flujo | T2 | `JobStatusTransitionTest` |
| Todas las transiciones en dominio | T2 | Test parametrizado |
| Roles con permisos distintos | T16 | Matriz §42 |
| Auditoría + historial + trazabilidad | T10/T11 | `@DataJpaTest` + MVC |
| Reportes básicos | T12 | MVC + JdbcClient |
| Pruebas unitarias y de concurrencia | T2/T9 | Suites |
| Ejecutable en una sola computadora | T15 | README + `java -jar` |
