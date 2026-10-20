# SIGO — Tutorial de desarrollo paso a paso

> Guía práctica para construir SIGO desde cero, en el orden de tareas del
> `docs/SIGO_Plan_Desarrollo.md` (§5). Cada paso indica **objetivo**, **comandos**,
> **archivos**, **cómo verificar** y el **commit** sugerido.
>
> Documento de referencia (diseño y decisiones): `docs/SIGO_Plan_Desarrollo.md`.
> Requisitos originales: `docs/SIGO_Especificacion_Proyecto.md`.

---

## Apéndice A — Conceptos, notaciones y librerías (leer primero)

### A.1 Notaciones que verás en este tutorial

| Notación | Significado |
|---|---|
| `<algo>` | Marcador de posición: sustitúyelo por tu valor (p. ej. `<tu-password>`). |
| `[ ]` / `[x]` | Casilla de verificación pendiente / completada. |
| `A → B` | Transición de estado o de un paso al siguiente. |
| `src/main/java/...` | Ruta relativa a la raíz del proyecto. |
| `${...}` | En Spring: valor de propiedad (`${SIGOTRAB_DB_PASSWORD}`). En Thymeleaf: expresión de variable (`${offer.title}`). |
| `@{...}` | En Thymeleaf: URL construida (`@{/webjars/bootstrap/5.3.8/css/bootstrap.min.css}`). |
| `#{...}` | En Thymeleaf: mensaje i18n (`#{offer.title.label}`). |
| `~{...}` | En Thymeleaf: expresión de fragmento (`~{layout :: layout(...)}`). |
| `*{...}` | En Thymeleaf: selección sobre el objeto de `th:object`. |
| `[[]]` | En YAML: lista. |
| `::` | En Thymeleaf: referencia a un fragmento nombrado (`layout :: layout`). |
| `|...|` | En Java: bloque de texto (text block) para SQL multilínea. |
| `$` / `$$ ... $$` | En SQL/psql: inicio de bloque o cuerpo `DO` de función. |

### A.2 Lenguaje, build y framework

| Término | Qué es (en una línea) |
|---|---|
| **Java 25 (LTS)** | Lenguaje y máquina virtual. Incluye records, bloques de texto y concurrencia virtual. |
| **Maven** | Gestor de dependencias y build. Se configura en `pom.xml`. |
| **`pom.xml`** | "Project Object Model": declara el proyecto, sus dependencias y plugins. |
| **Maven Wrapper (`mvnw`)** | Script que descarga y usa una versión fija de Maven, para no depender del Maven instalado. |
| **Spring Boot** | Facilita crear aplicaciones Spring: autoconfiguración, starters y servidor embebido (Tomcat). |
| **Starter** | Dependencia "paraguas" que agrupa lo necesario para una función (p. ej. `spring-boot-starter-webmvc`). |
| **Spring Framework** | El núcleo (contenedor de beans, inyección de dependencias, MVC, transacciones). |
| **Bean** | Objeto administrado por Spring (`@Component`, `@Service`, `@Repository`, `@Controller`). |
| **Inyección de dependencias** | Spring entrega las dependencias por constructor en lugar de que tú las crees con `new`. |
| **`@ConfigurationProperties`** | Enlaza propiedades de `application.yml` a un objeto/record tipado. |
| **Perfil (`@Profile`)** | Configuración alternativa por entorno: `dev`, `lan`, `test`. |

### A.3 Web y vistas

| Término | Qué es |
|---|---|
| **Spring MVC** | Framework web del lado servidor: mapea URLs a controladores. |
| **`@Controller` / `@RestController`** | Clase que atiende peticiones HTTP (vista HTML / cuerpo JSON). |
| **PRG (Post/Redirect/Get)** | Patrón: tras un POST se redirige con GET para evitar reenvíos. |
| **Flash attributes** | Mensajes que sobreviven a una redirección (p. ej. "Oferta aprobada"). |
| **Thymeleaf** | Motor de plantillas HTML del lado servidor (`templates/*.html`). |
| **Layout dialect** | Extensión de Thymeleaf para plantillas base reutilizables (`layout.html`). |
| **Bootstrap** | Biblioteca CSS/JS para UI responsiva. |
| **WebJars** | Empaqueta Bootstrap/Icons como dependencias Maven dentro del `.jar` (funciona sin internet). |
| **`webjars-locator-lite`** | Permite referenciar WebJars sin escribir la versión en la URL. |
| **`MessageSource`** | Resuelve textos desde `messages_es.properties` (i18n). |

### A.4 Seguridad

| Término | Qué es |
|---|---|
| **Spring Security** | Framework de autenticación y autorización. |
| **Autenticación vs autorización** | Quién eres vs qué puedes hacer. |
| **`UserDetailsService`** | Carga el usuario (y sus roles) desde la base de datos. |
| **`SecurityFilterChain`** | Cadena de filtros que decide el acceso a cada URL. |
| **`@PreAuthorize`** | Anotación que restringe un método según el rol del usuario. |
| **BCrypt** | Algoritmo de hash de contraseñas (con sal). Nunca se guarda la contraseña en claro. |
| **CSRF** | Ataque de falsificación de petición; Spring lo bloquea con un token. |
| **`@WithMockUser`** | En tests: simula un usuario autenticado con roles. |

### A.5 Persistencia y base de datos

| Término | Qué es |
|---|---|
| **JPA** | Especificación de mapeo objeto-relacional (anotaciones `@Entity`, `@Id`). |
| **Hibernate** | La implementación de JPA que usa Spring. |
| **Spring Data JPA** | Genera repositorios a partir de interfaces (`JpaRepository`). |
| **`@Entity`** | Clase mapeada a una tabla. |
| **`@Version`** | Bloqueo optimista: detecta modificaciones concurrentes. |
| **`@Query` / `@Modifying`** | Consulta propia (JPQL o SQL nativo) / marca una consulta que modifica datos. |
| **JDBC** | API de bajo nivel para hablar con la base de datos. |
| **`JdbcClient`** | API moderna y fluida de Spring para consultas SQL (ideal para reportes). |
| **`Pageable`** | Abstracción de paginación (`?page=0&size=20&sort=createdAt,desc`). |
| **UUID** | Identificador de 128 bits, sin significado, generado por la BD (`gen_random_uuid()`). |
| **`timestamptz`** | Fecha/hora con zona horaria en PostgreSQL; guardamos en **UTC**. |
| **`jsonb`** | Tipo de PostgreSQL para JSON binario, indexable (lo usamos en auditoría). |
| **`inet`** | Tipo de PostgreSQL para direcciones IP. |
| **`gen_random_uuid()`** | Función nativa de PostgreSQL ≥ 13 para generar UUID. |
| **Flyway** | Versiona el esquema con scripts SQL (`V1__baseline.sql`, `V2__...`). |
| **Migración** | Script numerado que se aplica una sola vez, en orden. |
| **PostgreSQL 17** | Base de datos relacional (también `pg_dump` para backup). |
| **`@DataJpaTest`** | Test que levanta solo la capa de persistencia. |

### A.6 Arquitectura y patrones

| Término | Qué es |
|---|---|
| **Arquitectura hexagonal (Ports & Adapters)** | El dominio vive en el centro; la web y la BD son "adaptadores" que se conectan por "puertos". |
| **Dominio** | Reglas de negocio puras, sin Spring ni JPA. |
| **Aplicación** | Casos de uso que orquestan el dominio y los puertos. |
| **Puerto de entrada (`port.in`)** | Interfaz que la aplicación ofrece (lo que se puede pedir). |
| **Puerto de salida (`port.out`)** | Interfaz que la aplicación necesita (BD, auditoría). |
| **Adaptador** | Implementación concreta de un puerto (controlador web, repositorio JPA). |
| **Agregado rico** | Objeto de dominio que contiene sus datos y sus reglas (no solo getters). |
| **Invariante** | Condición que siempre debe cumplirse (p. ej. `IN_PROGRESS` exige usuario asignado). |
| **ADR** | *Architecture Decision Record*: nota corta que documenta una decisión y sus motivos. |
| **`@Transactional`** | Envuelve un método en una transacción: todo o nada. |
| **Scheduler** | Tarea que se ejecuta periódicamente (`@Scheduled`). |

### A.7 Pruebas y operación

| Término | Qué es |
|---|---|
| **JUnit Jupiter (JUnit 6)** | Framework de pruebas (`@Test`, `@ParameterizedTest`). |
| **Mockito** | Crea objetos simulados ("mocks") para aislar la unidad. |
| **AssertJ** | Aserciones legibles (`assertThat(x).isEqualTo(y)`). |
| **MockMvc** | Simula peticiones HTTP sin arrancar el servidor. |
| **`@WebMvcTest`** | Test que levanta solo la capa web. |
| **`@SpringBootTest`** | Test de integración que arranca toda la aplicación. |
| **ArchUnit** | Biblioteca que verifica reglas de arquitectura como tests. |
| **EmbeddedPostgres (Zonky)** | Levanta un PostgreSQL real y temporal durante los tests (sin Docker). |
| **SLF4J / Logback** | Fachada de logging / implementación que escribe los logs. |
| **`sigotrab.jar`** | Artefacto ejecutable (`java -jar`). |
| **LAN** | Red de área local: varias PC contra un servidor (`0.0.0.0:8080`). |
| **Conventional Commits** | Formato de mensajes: `feat:`, `fix:`, `test:`, `docs:`, `chore:`. |
| **CI** | Integración continua: GitHub Actions compila y prueba en cada push. |
| **`pg_dump`** | Herramienta de PostgreSQL para respaldar la base de datos. |

---

## Parte 0 — Preparar la máquina

**Objetivo:** tener las herramientas base.

1. **Java 25** (Temurin). Verificar:
   ```bash
   java -version
   # openjdk version "25.x" ...
   ```
2. **PostgreSQL 17** (instalador de Windows). Verificar que `psql` y `pg_dump` están en el PATH:
   ```bash
   psql --version
   pg_dump --version
   ```
3. **Git**:
   ```bash
   git --version
   ```
4. **Maven** (o usar el wrapper tras el paso 1).

Crear la base de datos y el usuario:

```sql
CREATE ROLE sigotrabuser LOGIN PASSWORD 'change-me';
CREATE DATABASE sigotrabdb OWNER sigotrabuser;
```

> **Alternativa con Docker (ADR-0006):** en lugar de instalar PostgreSQL nativo, puedes
> levantar la BD de desarrollo con el servicio PostgreSQL de `docker/docker-compose.yml`
> (`POSTGRES_DB=sigotrabdb`, `POSTGRES_USER=sigotrabuser`, versión en `POSTGRES_VERSION`
> del `.env`). La app apunta a `localhost:5432/sigotrabdb`. Los **tests** siguen usando
> `embedded-postgres` (sin Docker).

> **Por qué PostgreSQL 17:** la versión embebida que usarán los tests es 17.5.0.
> Igualar versiones evita diferencias entre test y demo. El `.env` actual usa
> `POSTGRES_VERSION=16-alpine`; unificar la versión es deuda aparte.

**Verificar:** `psql -U sigotrabuser -d sigotrabdb -c "select version();"`

---

## T0 — Andamiaje del proyecto

**Objetivo:** proyecto Maven que compila, repo git y CI listos.

### Paso 1 — Generar el proyecto

```bash
curl -G https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d bootVersion=4.1.1 -d javaVersion=25 \
  -d groupId=com.sigotrab -d artifactId=sigotrab -d name=SIGO \
  -d packageName=com.sigotrab -d packaging=jar \
  -d dependencies=web,thymeleaf,security,data-jpa,validation,flyway,postgresql \
  -o sigo.zip
```

> `curl -G -d` envía los parámetros como query string; `-o` guarda el `.zip`.
> Si no tienes `curl`, descarga el ZIP desde https://start.spring.io con esos mismos valores.

Descomprime el contenido en la raíz del proyecto.

### Paso 2 — Maven Wrapper

```bash
mvn -N wrapper:wrapper -Dmaven=3.9.9
```

> `-N` evita construir el proyecto; solo genera `mvnw`, `mvnw.cmd` y `.mvn/`.
> A partir de aquí usa `./mvnw` en lugar de `mvn`.

### Paso 3 — Repositorio y `.gitignore`

```bash
git init
git add -A
git commit -m "chore: andamiaje inicial de SIGO"
```

`.gitignore` debe incluir: `target/`, `.idea/`, `*.iml`, `.vscode/`, `backups/`,
`*.log`, `.env`.

### Paso 4 — CI

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

### Paso 5 — Documentación viva

Crear `GLOSSARY.md` (ver §10 del plan) y `docs/adr/0001-transacciones-en-application.md`.

**Verificar:** `./mvnw -B verify` termina en `BUILD SUCCESS` (aún sin tests propios).

**Commit:** `chore: maven wrapper, gitignore y CI (#T0)`

---

## T1 — Esqueleto ejecutable: app + BD + login + dashboard

**Objetivo:** la aplicación arranca, aplica migraciones, permite iniciar sesión y muestra
un panel vacío con Bootstrap.

### Paso 1 — `pom.xml` con las dependencias del proyecto

Añade a `pom.xml` (además de lo generado): WebJars, layout dialect, ArchUnit y
`embedded-postgres` de test. El fragmento completo está en §4.2 del plan.

Puntos clave:
```xml
<dependency><groupId>org.webjars</groupId><artifactId>bootstrap</artifactId><version>5.3.8</version></dependency>
<dependency><groupId>org.webjars.npm</groupId><artifactId>bootstrap-icons</artifactId><version>1.13.2</version></dependency>
<dependency><groupId>org.webjars</groupId><artifactId>webjars-locator-lite</artifactId></dependency>
<dependency><groupId>org.thymeleaf.extras</groupId><artifactId>thymeleaf-extras-springsecurity6</artifactId></dependency>
<dependency><groupId>nz.net.ultraq.thymeleaf</groupId><artifactId>thymeleaf-layout-dialect</artifactId></dependency>
```

> **Aviso Spring Boot 4:** el starter web es `spring-boot-starter-webmvc` (no `-web`), el
> de migraciones es `spring-boot-starter-flyway`, y los starters de test son **por módulo**
> (`-webmvc-test`, `-data-jpa-test`, `-security-test`).

**Verificar:** `./mvnw -B dependency:resolve` descarga todo sin errores.

### Paso 2 — Configuración

Crea `application.yml`, `application-dev.yml` y `application-lan.yml` (§4.3 del plan).

### Paso 3 — Migraciones

Crea `src/main/resources/db/migration/V1__baseline.sql` y `V2__seed_roles.sql`
(§4.4 del plan). Copia el SQL tal cual.

**Verificar tras arrancar:** `psql -U sigotrabuser -d sigotrabdb -c "\dt"` lista 6 tablas.

### Paso 4 — Seguridad mínima

Crea `infrastructure/security/SecurityConfig.java` (§4.9) con `/login` y reglas por rol,
y un `SigUserDetailsService` que lee de `users`/`roles`.

> En Spring Security 7 el estilo es **solo lambda**:
> `http.authorizeHttpRequests(auth -> auth...).formLogin(form -> form...)`.

### Paso 5 — Sembrado de usuarios de desarrollo

`infrastructure/config/SeedDevUsers.java` con `@Profile("dev")` y `ApplicationRunner`
que crea `admin`, `registrador` y `publicador` usando el `PasswordEncoder`.

### Paso 6 — Vistas y dashboard

`templates/layout.html`, `templates/login.html`, `templates/dashboard.html` y
`DashboardController` que devuelve `/dashboard`.

**Verificar:** `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` y abre
`http://localhost:8080`. Inicia sesión como `registrador` / `registrador123`.

**Commit:** `feat: esqueleto con base de datos, login y dashboard (#T1)`

---

## T2 — Ciclo de vida del dominio

**Objetivo:** reglas de transición probadas sin Spring.

### Paso 1 — Enum y agregado

Crea `domain/model/JobStatus.java` (con `canTransitionTo`) y `domain/model/JobOffer.java`
(§4.5 del plan). Crea las excepciones en `domain/exception/`.

### Paso 2 — Test de transiciones (§39 del spec)

```java
@ParameterizedTest
@CsvSource({
  "REGISTERED,  APPROVED,    true",
  "REGISTERED,  PUBLISHED,   false",
  "APPROVED,    IN_PROGRESS, true",
  "APPROVED,    PUBLISHED,   false",
  "IN_PROGRESS, PUBLISHED,   true",
  "IN_PROGRESS, APPROVED,    true",
  "PUBLISHED,   APPROVED,    false"
})
void transiciones(JobStatus from, JobStatus to, boolean allowed) {
    assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
}
```

### Paso 3 — Test del agregado

Comprueba que `approve()` sobre `REGISTERED` cambia de estado y que `publish()` sin ser
el asignado lanza `UnauthorizedJobOperationException`.

### Paso 4 — ArchUnit

Crea `architecture/HexagonalArchitectureTest.java` (§4.12). Verifica que `domain` no
importe Spring ni JPA.

**Verificar:** `./mvnw test` en verde.

**Commit:** `feat: dominio con transiciones, excepciones y ArchUnit (#T2)`

---

## T3 — Registrar oferta

**Objetivo:** un `REGISTRADOR` crea una oferta que nace `REGISTERED`, y queda auditada.

### Paso 1 — Puertos

`application/port/in/RegisterJobOfferUseCase.java`,
`application/port/out/JobOfferRepository.java`, `AuditPort.java`, `StatusHistoryPort.java`.

### Paso 2 — Entidad JPA y repositorio

`adapters/persistence/entity/JobOfferJpaEntity.java` (con `@Version`), mapper
entidad↔dominio, y `JobOfferJpaRepository`.

### Paso 3 — Caso de uso

`application/service/RegisterJobOfferService.java` (`@Transactional`): construye el
agregado, guarda, y registra auditoría `CREATE`.

### Paso 4 — Web

- `OffersController`: `GET /offers`, `GET /offers/new`, `POST /offers`,
  `GET /offers/{id}`, `GET /offers/registered` (con `Pageable`).
- DTO de formulario con Bean Validation (`@NotBlank`, `@Size`) y `@Valid` en el controlador.
  Obligatorios: `title`, `company`, `description`, `requirements`, `source_url`,
  `extractedAt`; opcionales: `location`, `salary`, `employmentType` (ADR-0007).

### Paso 5 — Deduplicación (§24)

Antes de guardar, `existsBySourceReference(...)`; si existe, añade error de formulario.
La restricción `unique (source_name, external_reference)` es la red de seguridad.

**Verificar:** crear una oferta desde la UI; aparece en `/offers/registered` con estado
`REGISTERED`; en `audit_event` hay una fila `CREATE`.

**Commit:** `feat: registro de ofertas con validación y auditoría (#T3)`

---

## T4 — Curar y editar

**Objetivo:** editar solo ofertas `REGISTERED`, con auditoría `old/new`.

### Pasos

1. `UpdateJobOfferUseCase` + `UpdateJobOfferService`.
2. `JobOffer.updateContent(...)` en el dominio (lanza `InvalidJobStateException` si el
   estado no es `REGISTERED`).
3. `POST /offers/{id}` (edición) con validación y PRG.
4. En la auditoría, guarda `old_values`/`new_values` como JSONB.

**Verificar:** editar una oferta aprobada no está disponible en la UI **y** el endpoint
devuelve error (validación en servidor, §34).

**Commit:** `feat: curación de ofertas con auditoría de cambios (#T4)`

---

## T5 — Aprobar y rechazar

**Objetivo:** `REGISTERED → APPROVED` y `REGISTERED → REJECTED` (con motivo).

### Pasos

1. `ApproveJobOfferUseCase` / `RejectJobOfferUseCase` + servicios.
2. `POST /offers/{id}/approve` y `POST /offers/{id}/reject`.
3. El rechazo exige motivo (validación de dominio y de formulario).
4. En la misma transacción: `StatusHistoryPort.record(...)` + `AuditPort.record(...)`.
5. Proteger con `@PreAuthorize("hasRole('REGISTRADOR')")`.

**Verificar:** aprobar una oferta; `job_offer_status_history` tiene
`REGISTERED → APPROVED`.

**Commit:** `feat: aprobación y rechazo con historial de estados (#T5)`

---

## T6 — Adquirir (adquisición atómica)

**Objetivo:** un `PUBLICADOR` adquiere una oferta; dos no pueden a la vez.

### Paso 1 — El `UPDATE` atómico (§14 del spec)

En `JobOfferJpaRepository`:
```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(value = """
    update job_offer
       set status = 'IN_PROGRESS', assigned_user_id = :userId,
           locked_at = :now, lock_expires_at = :expiresAt,
           version = version + 1, updated_at = :now, updated_by = :userId
     where id = :id
       and (status = 'APPROVED'
            or (status = 'IN_PROGRESS' and lock_expires_at < :now))
    """, nativeQuery = true)
int acquire(@Param("id") UUID id, @Param("userId") UUID userId,
            @Param("now") Instant now, @Param("expiresAt") Instant expiresAt);
```

> `1` fila afectada = adquisición exitosa; `0` = conflicto. La **base de datos** es la
> autoridad, nunca `synchronized`.

### Paso 2 — Caso de uso

`AcquireJobOfferService` (§4.8): calcula `now`/`expiresAt` con el `Clock`, llama a
`acquire`, y si falla lanza `JobLockedException` tras auditar el intento fallido.

### Paso 3 — Endpoint JSON + errores

`AcquireJobOfferController` (`POST /api/offers/{id}/acquire`) y `ApiExceptionHandler`
que traduce `JobLockedException` a **409** con `{ "code": "JOB_ALREADY_LOCKED", ... }`.

### Paso 4 — UI

`/offers/approved` con botón "Adquirir" que hace `fetch(...)` y, si recibe 409, muestra
un aviso sin recargar.

**Verificar:** dos pestañas, dos publicadores distintos, misma oferta, clic simultáneo:
una obtiene OK y la otra 409.

**Commit:** `feat: adquisición atómica de ofertas con 409 (#T6)`

---

## T7 — Bloqueo, cancelar y publicar

**Objetivo:** ver tiempo restante; cancelar vuelve a `APPROVED`; publicar pasa a `PUBLISHED`.

### Pasos

1. `/offers/in-progress`: lista las ofertas del usuario, con `data-expires-at` y un JS
   que muestra el tiempo restante.
2. `ReleaseJobOfferUseCase` (cancelación manual, con motivo) + `POST /offers/{id}/cancel`.
3. `PublishJobOfferUseCase` + `POST /offers/{id}/publish`.
4. Solo el usuario asignado puede cancelar/publicar (`JobOffer.isAssignedTo`).
5. `/offers/published`.

**Verificar:** publicar una oferta; no aparece ningún botón para volver atrás y el
endpoint de aprobar devuelve error sobre una oferta `PUBLISHED`.

**Commit:** `feat: bloqueo, cancelación y confirmación de publicación (#T7)`

---

## T8 — Timeout automático

**Objetivo:** las ofertas expiradas vuelven a `APPROVED` y se auditan como `SYSTEM`.

### Pasos

1. `ReleaseExpiredOffersUseCase`: busca ids `IN_PROGRESS` con `lock_expires_at < now`,
   los libera y registra historial + auditoría (`TIMEOUT_RELEASE`, actor `SYSTEM`).
2. `ExpiredOfferScheduler` con `@Scheduled(fixedDelayString = "${sigotrab.scheduler.fixed-delay-ms}")`.

> El scheduler es **limpieza**; la seguridad del timeout ya está en el `UPDATE` de T6.

**Verificar:** en perfil `dev` (bloqueo de 2 min), adquirir una oferta, esperar y
comprobar que vuelve a `APPROVED` con una fila `TIMEOUT_RELEASE`.

**Commit:** `feat: liberación por timeout con auditoría SYSTEM (#T8)`

---

## T9 — Pruebas de concurrencia y timeout

**Objetivo:** demostrar con PostgreSQL real que solo un usuario adquiere.

### Pasos

1. Crea `test/support/EmbeddedPostgresSupport` con `@DynamicPropertySource` (§4.12).
2. `AcquireJobOfferConcurrencyTest`: 2 hilos con `CyclicBarrier`, repetido 20 veces,
   afirmando `["OK","CONFLICT"]` en cada ronda.
3. `TimeoutReleaseTest`: con `Clock` fijo, verifica estado y auditoría.

**Verificar:** `./mvnw verify` en verde en local y en CI.

**Commit:** `test: concurrencia y timeout contra PostgreSQL embebido (#T9)`

---

## T10 a T13 — Trazabilidad, auditoría, reportes y dashboard

- **T10 Trazabilidad:** `/offers/{id}` muestra la línea de tiempo uniendo
  `job_offer_status_history` y `audit_event` (§12 del spec).
- **T11 Auditoría:** `/audit` con filtros (usuario, acción, entidad, fecha) y paginación.
- **T12 Reportes:** `/reports` con las 3 consultas del §32, usando **`JdbcClient`** y
  botón **Exportar CSV**. Incluye "tiempo de procesamiento" (`published_at - locked_at`).
- **T13 Dashboard:** tarjetas con el conteo por estado y las métricas del §33.

**Verificar:** cada pantalla responde y el CSV se descarga y abre en Excel.

**Commit:** `feat: trazabilidad, auditoría, reportes y dashboard (#T10-#T13)`

---

## T14 — Administración de usuarios y roles

**Objetivo:** el `ADMIN` gestiona usuarios y roles.

### Pasos

1. `UserAdminController` (`/admin/users`): listar, crear, editar, habilitar/deshabilitar.
2. Asignación de roles con casillas (`role_admin`, `role_registrador`, `role_publicador`).
3. Nunca se borra físicamente un usuario: se deshabilita (`enabled = false`).
4. Auditar cada operación (`CREATE`, `UPDATE`, `ASSIGN`).

**Verificar:** con un usuario deshabilitado, el login falla.

**Commit:** `feat: administración de usuarios y roles (#T14)`

---

## T15 — Operación en LAN

**Objetivo:** la demo funciona desde otros equipos de la red.

### Pasos

1. `application-lan.yml` con `server.address: 0.0.0.0` y credenciales por variables de
   entorno.
2. `scripts/run.ps1` / `run.sh` y `scripts/backup.ps1` / `backup.sh` (§8 del plan).
3. `README.md` con: requisitos, instalación de PostgreSQL, creación de la BD, arranque,
   URLs y política de backup.
4. Empaquetar:
   ```bash
   ./mvnw -DskipTests package
   java -jar target/sigotrab.jar --spring.profiles.active=lan
   ```

**Verificar:** abrir `http://IP-DEL-SERVIDOR:8080` desde otro equipo de la LAN.

**Commit:** `feat: perfil LAN, scripts de operación y README (#T15)`

---

## T16 — Endurecer seguridad

**Objetivo:** matriz de permisos del §42 verificada automáticamente.

```java
@WebMvcTest(OffersController.class)
class SecurityRulesTest {
    @Test @WithMockUser(roles = "REGISTRADOR")
    void registrador_puede_aprobar() throws Exception { mockMvc.perform(post("/offers/{id}/approve", ID)).andExpect(status().is3xxRedirection()); }

    @Test @WithMockUser(roles = "PUBLICADOR")
    void publicador_no_puede_aprobar() throws Exception { mockMvc.perform(post("/offers/{id}/approve", ID)).andExpect(status().isForbidden()); }
    // ... adquirir, publicar, admin, reportes
}
```

**Verificar:** `./mvnw verify`. Revisar manualmente que cada endpoint tiene su regla
(ocultar un botón **no** es seguridad, §34).

**Commit:** `test: matriz de permisos por rol (#T16)`

---

## T17 — Cierre

1. Recorrer el **checklist del §50** del spec y marcar cada casilla.
2. Revisar que los ADR reflejan las decisiones finales.
3. `git tag v1.0`.

**Commit:** `docs: cierre v1.0 y ADR finales (#T17)`

---

## Apéndice B — Comandos de uso frecuente

```bash
# ejecutar en desarrollo (lock de 2 min, usuarios demo)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# solo los tests de dominio (rápidos)
./mvnw test -Dtest="Job*Test,JobStatus*Test"

# empaquetar sin tests
./mvnw -DskipTests package

# inspeccionar la base de datos
psql -U sigotrabuser -d sigotrabdb -c "select status, count(*) from job_offer group by status;"

# respaldar
pg_dump -Fc -d sigotrabdb -f backups/sigotrabdb_$(date +%F).dump
```

## Apéndice C — Errores comunes y solución

| Síntoma | Causa probable | Solución |
|---|---|---|
| `BUILD FAILURE` por dependencia no encontrada | Nombre de starter de Boot 4 | Usa `spring-boot-starter-webmvc`, no `-web` |
| `Unable to find @WebMvcTest` | Paquete cambiado en Boot 4 | Importa `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| `Schema validation: missing table` | Flyway no corrió o `ddl-auto` mal | Revisa `spring.flyway.locations` y `ddl-auto: validate` |
| `409` al adquirir siempre | Oferta no está `APPROVED` o ya bloqueada | Revisa el estado y `lock_expires_at` |
| El test de concurrencia falla | Falta PostgreSQL embebido en el classpath | Añade `embedded-postgres` y los binarios de tu SO |
| `relation "job_offer" does not exist` | Migración no aplicada | `./mvnw flyway:migrate` o revisa el arranque |
| El login no funciona | Contraseña/roles mal sembrados | Verifica `SeedDevUsers` y la tabla `user_roles` |

## Apéndice D — Mapa tutorial → plan

| Tutorial | Ticket | Sección del plan |
|---|---|---|
| Parte 0, T0 | T0 | §4.1, §4.2, §10 |
| T1 | T1 | §4.3, §4.4, §4.9, §4.10 |
| T2 | T2 | §4.5, §6 |
| T3–T5 | T3–T5 | §4.6, §4.7, §4.10 |
| T6–T8 | T6–T8 | §4.7, §4.8, §4.11 |
| T9 | T9 | §4.12, §6 |
| T10–T13 | T10–T13 | §4.10, §7, §8 |
| T14 | T14 | §4.9, §10 |
| T15 | T15 | §8 |
| T16–T17 | T16–T17 | §6, §12 |
