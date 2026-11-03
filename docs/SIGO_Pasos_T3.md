# SIGO — Guía paso a paso de T3 (registrar oferta)

> **Cómo usar este documento.** Cada paso tiene **Objetivo**, **Dónde** (paquete/archivo),
> **Qué escribir** (contrato + reglas, *no* el código completo) y **Cómo verificar**.
> **Tú escribes el código; esta guía te orienta.** Si algo diverge, mandan el
> `docs/SIGO_Plan_Desarrollo.md` §4.5–§4.10/§4.12 y el
> `docs/SIGO_Especificacion_Proyecto.md` §7/§13/§24/§30/§36/§39.
>
> Referencias: plan §3 (estructura), §4.5 (dominio), §4.6 (puertos), §4.7 (persistencia),
> §4.9 (seguridad), §4.10 (web), §4.12 (pruebas), §5 (T3), §10 (convenciones);
> spec §8 (RF-01), §13 (modelo), §22 (JOB_OFFER), §24 (duplicados), §30 (web),
> §35–§36 (errores/excepciones), §39 (tests); tutorial T3; `docs/adr/0007`
> (campos obligatorios), `docs/adr/0012` (Lombok solo en entidades JPA).
>
> **Prerrequisito:** T1 y T2 cerrados y commiteados (`4aa7f84`, `bc1f660`). T3 es la
> **primera rebanada vertical**: toca dominio (poco), aplicación, persistencia, auditoría
> y web. **No** hay migración Flyway nueva: las tablas `job_offer`, `audit_event` y
> `job_offer_status_history` ya existen en `V1__baseline.sql`.

---

## Notas previas (leer antes de empezar)

- **Paquete real:** `com.evela.sigotrab` (el plan escribe `com.sigotrab`; seguimos el código).
- **Hexagonal estricta:** `domain/` no importa Spring/JPA/Hibernate/Thymeleaf/Lombok.
  `application/` solo depende de `domain/` y de sus propios puertos. `adapters/` e
  `infrastructure/` dependen hacia dentro.
- **`@Transactional` solo en `application/`.** El controlador nunca toca JPA.
- **Sin Lombok en `domain/`.** En `JobOfferJpaEntity` sí puedes usar Lombok (ADR-0012).
- **Sin migración Flyway.** Si `ddl-auto: validate` falla, el problema es tu mapeo, no el
  esquema.
- **Todos los comandos Maven desde `sigotrab/`**, no desde la raíz.
- **No añadas dependencias:** `validation`, `thymeleaf`, `data-jpa`, `security` y
  `webjars` ya están en `pom.xml`.
- **El dominio ya existe.** `JobOffer`, `JobStatus`, `NewJobOffer`, `JobOfferContent` y
  las seis excepciones están implementados y probados. T3 **no los reescribe**; solo
  añade lo que falta (`reconstitute`, `ActorInfo`, auditoría y una excepción de duplicado).

### Qué NO entra en T3 (para no adelantar trabajo)

| Cosa | Ticket |
|---|---|
| Editar una oferta (`updateContent` en web) | T4 |
| Aprobar / rechazar (historial de estados) | T5 |
| `acquire` atómico, `409 JOB_ALREADY_LOCKED` | T6 |
| Cancelar, publicar, `/offers/in-progress`, `/offers/published` | T7 |
| Scheduler de timeout | T8 |
| `/audit` consultable, reportes, CSV | T11/T12 |
| Dashboard con conteos por estado | T13 |
| `StatusHistoryPort` (aún no hay transición que registrar) | T5 |

> **Delta documentado:** el tutorial T3 lista `StatusHistoryPort` entre los puertos. En T3
> la oferta nace `REGISTERED` y **no hay transición**, así que ese puerto se implementa en
> T5 (primer cambio de estado). Si prefieres seguirlo al pie de la letra, declara la
> interfaz ahora; el adaptador llega en T5.

---

## Paso 0 — Cerrar T2 antes de empezar T3

**Objetivo:** que el commit de T3 contenga solo T3.

**Dónde:** raíz del repositorio.

**Qué hacer:**

```bash
git status --short
git log --oneline -3
```

**Cómo verificar:** el árbol está limpio y el último commit es
`bc1f660 feat: dominio con transiciones, excepciones y ArchUnit (#T2)`. Si hay cambios
pendientes, ciérralos antes.

---

## Paso 1 — Estructura de archivos de T3

**Objetivo:** dejar claro qué se crea y qué se modifica.

**Dónde:** `sigotrab/src/main/java/com/evela/sigotrab/` y `sigotrab/src/main/resources/`.

**Qué crear:**

```text
src/main/java/com/evela/sigotrab/
├── domain/
│   ├── model/
│   │   ├── ActorInfo.java                 (nuevo)
│   │   ├── AuditAction.java               (nuevo)
│   │   ├── AuditRecord.java               (nuevo)
│   │   └── JobOffer.java                  (MODIFICAR: + reconstitute)
│   └── exception/
│       └── DuplicateJobOfferException.java (nuevo)
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── RegisterJobOfferUseCase.java (nuevo)
│   │   │   └── GetJobOffersUseCase.java     (nuevo)
│   │   └── out/
│   │       ├── JobOfferRepository.java      (nuevo)
│   │       ├── AuditPort.java               (nuevo)
│   │       ├── PageQuery.java               (nuevo)
│   │       └── PageResult.java              (nuevo)
│   └── service/
│       ├── RegisterJobOfferService.java     (nuevo)
│       └── GetJobOffersService.java         (nuevo)
├── adapters/
│   ├── persistence/
│   │   ├── entity/JobOfferJpaEntity.java    (nuevo)
│   │   ├── repository/JobOfferJpaRepository.java (nuevo)
│   │   ├── mapper/JobOfferEntityMapper.java (nuevo)
│   │   └── JobOfferPersistenceAdapter.java  (nuevo)
│   └── web/
│       ├── controller/OffersController.java (nuevo)
│       ├── dto/JobOfferForm.java            (nuevo)
│       ├── dto/JobOfferView.java            (nuevo)
│       └── mapper/JobOfferWebMapper.java    (nuevo)
└── infrastructure/
    └── audit/JpaAuditAdapter.java           (nuevo)

src/main/resources/templates/offers/
├── list.html        (nuevo)
├── form.html        (nuevo)
├── detail.html      (nuevo)
└── not-found.html   (nuevo)

src/main/resources/static/css/app.css              (MODIFICAR: tema de T3)
src/main/resources/templates/layout.html            (MODIFICAR: navbar responsive)
src/main/resources/messages/messages_es.properties  (MODIFICAR: claves offers.*)
src/main/java/com/evela/sigotrab/infrastructure/security/SecurityConfig.java (MODIFICAR)
```

**Tests a crear:**

```text
src/test/java/com/evela/sigotrab/
├── application/service/RegisterJobOfferServiceTest.java (Mockito)
├── adapters/persistence/mapper/JobOfferEntityMapperTest.java
├── adapters/web/controller/OffersControllerTest.java (@WebMvcTest)
└── adapters/web/controller/SecurityRulesTest.java (@WebMvcTest, matriz de roles)
```

**Cómo verificar:** `git status --short` muestra solo los archivos nuevos/modificados de
T3. `./mvnw -B -DskipTests compile` compila (aún sin los tests).

---

## Paso 2 — `ActorInfo`: quién hace la operación

**Objetivo:** pasar el actor (usuario + username + IP) al caso de uso sin que la capa de
aplicación conozca HTTP ni `HttpServletRequest`.

**Dónde:** `domain/model/ActorInfo.java`.

**Contrato:**

```java
package com.evela.sigotrab.domain.model;

import java.util.UUID;

public record ActorInfo(UUID userId, String username, String ipAddress) {

    public static ActorInfo system() {
        return new ActorInfo(null, "SYSTEM", null);
    }
}
```

**Reglas:**

- `ipAddress` es un `String`; el dominio no valida IPs ni conoce `inet`.
- `system()` se usa en T8 (timeout). No la borres aunque T3 no la use.
- No metas `GrantedAuthority` ni nada de Spring Security aquí.

**Cómo verificar:** compila y `domain/` sigue sin imports de Spring.

---

## Paso 3 — `AuditAction` y `AuditRecord`: vocabulario de auditoría

**Objetivo:** que el caso de uso emita un evento de auditoría sin depender de JPA ni de
Jackson.

**Dónde:** `domain/model/AuditAction.java` y `domain/model/AuditRecord.java`.

### 3.1 `AuditAction`

```java
package com.evela.sigotrab.domain.model;

public enum AuditAction {
    CREATE, UPDATE, DELETE, LOGIN, LOGOUT,
    APPROVE, REJECT,
    ACQUIRE, RELEASE, TIMEOUT_RELEASE,
    PUBLISH, ASSIGN, REASSIGN
}
```

> `UNPUBLISH` **no** existe (ADR-0008). `DELETE` sobre usuarios se representa con
> `enabled=false`; en ofertas no se usa.

### 3.2 `AuditRecord`

```java
package com.evela.sigotrab.domain.model;

import java.util.Map;

public record AuditRecord(
        AuditAction action,
        String entityType,
        String entityId,
        ActorInfo actor,
        boolean success,
        String errorCode,
        String reason,
        Map<String, Object> oldValues,
        Map<String, Object> newValues
) {

    public static AuditRecord success(AuditAction action, String entityType,
                                      String entityId, ActorInfo actor) {
        return new AuditRecord(action, entityType, entityId, actor, true, null, null, null, null);
    }

    public static AuditRecord success(AuditAction action, String entityType,
                                      String entityId, ActorInfo actor,
                                      Map<String, Object> newValues) {
        return new AuditRecord(action, entityType, entityId, actor, true, null, null, null, newValues);
    }

    public static AuditRecord failure(AuditAction action, String entityType,
                                      String entityId, ActorInfo actor, String errorCode) {
        return new AuditRecord(action, entityType, entityId, actor, false, errorCode, null, null, null);
    }

    public static AuditRecord system(AuditAction action, String entityType, String entityId) {
        return new AuditRecord(action, entityType, entityId, ActorInfo.system(), true, null, null, null, null);
    }
}
```

**Reglas:**

- `Map<String, Object>` es JDK puro; el adaptador decide cómo serializarlo a `jsonb`.
- `occurred_at` lo pone la base (`default now()`); no lo metas en el record.
- No añadas aquí `HttpStatus`, `ResponseEntity` ni nada web.

**Cómo verificar:** compila; ArchUnit sigue en verde (`domain` puro).

---

## Paso 4 — `DuplicateJobOfferException`

**Objetivo:** expresar el duplicado como error de negocio, no como un `boolean` ni un
`500`.

**Dónde:** `domain/exception/DuplicateJobOfferException.java`.

**Contrato:** extiende `RuntimeException`, guarda `sourceName` y `externalReference` como
campos `final` con getters, y un mensaje en español:

```text
Ya existe una oferta registrada para la fuente '<sourceName>' con referencia '<externalReference>'
```

**Reglas:**

- Es una excepción **nueva de T3** (no estaba en la lista de T2). Justifícala en el commit.
- No la confundas con `DataIntegrityViolationException` (esa es técnica y vive en el
  adaptador; ver Paso 10.4).
- El controlador la traduce a un error de formulario (Paso 12.4).

**Cómo verificar:** compila y no importa Spring.

---

## Paso 5 — `JobOffer.reconstitute(...)`

**Objetivo:** poder reconstruir el agregado desde JPA sin abrir setters públicos ni
romper la encapsulación.

**Dónde:** `domain/model/JobOffer.java` (modificar).

**Qué añadir:** un constructor privado con **todos** los campos y una factoría estática
`reconstitute(...)` que delegue en él.

```java
public static JobOffer reconstitute(
        UUID id,
        String title, String company, String description, String requirements,
        String location, String salary, String employmentType,
        String originalSourceUrl, String sourceName, String externalReference,
        Instant extractedAt, String publicationUrl,
        JobStatus status, UUID assignedUserId, Instant lockedAt, Instant lockExpiresAt,
        Instant approvedAt, Instant publishedAt,
        Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {
    return new JobOffer(id, title, company, description, requirements, location, salary,
            employmentType, originalSourceUrl, sourceName, externalReference, extractedAt,
            publicationUrl, status, assignedUserId, lockedAt, lockExpiresAt, approvedAt,
            publishedAt, createdAt, createdBy, updatedAt, updatedBy);
}
```

**Reglas:**

- El constructor privado solo asigna; **no** revalida reglas de negocio (el estado viene
  de la base y es confiable). Como mínimo, `Objects.requireNonNull(id)` y
  `Objects.requireNonNull(status)`.
- Mantén el constructor privado actual `(UUID, NewJobOffer, UUID, Instant)` que usa
  `register`. No lo reemplaces.
- **No** añadas `version` al dominio: el optimistic locking es del adaptador JPA (T6).
- No añadas setters públicos. `reconstitute` es la única puerta de entrada desde el mapper.

**Cómo verificar:** `./mvnw test -Dtest=JobOfferTest,JobStatusTransitionTest` sigue en
verde (no cambiaste el comportamiento).

---

## Paso 6 — Puertos de salida

**Objetivo:** que `application/` declare lo que necesita y `adapters/` lo implemente.

**Dónde:** `application/port/out/`.

### 6.1 `PageQuery` y `PageResult`

```java
package com.evela.sigotrab.application.port.out;

public record PageQuery(int page, int size) {
    public PageQuery {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1) throw new IllegalArgumentException("size must be >= 1");
    }
}
```

```java
package com.evela.sigotrab.application.port.out;

import java.util.List;

public record PageResult<T>(List<T> items, long totalElements, int page, int size) {
    public PageResult {
        items = List.copyOf(items);
    }
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
    public boolean hasNext() { return page + 1 < totalPages(); }
    public boolean hasPrevious() { return page > 0; }
}
```

> **Por qué no `org.springframework.data.domain.Page`:** meterlo en `application/` ataría
> el caso de uso a Spring Data. El adaptador traduce `Page` ↔ `PageResult`.

### 6.2 `JobOfferRepository`

```java
package com.evela.sigotrab.application.port.out;

import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;

import java.util.Optional;
import java.util.UUID;

public interface JobOfferRepository {
    void save(JobOffer jobOffer);
    Optional<JobOffer> findById(UUID id);
    PageResult<JobOffer> findAll(PageQuery page);
    PageResult<JobOffer> findByStatus(JobStatus status, PageQuery page);
    boolean existsBySourceReference(String sourceName, String externalReference);
}
```

### 6.3 `AuditPort`

```java
package com.evela.sigotrab.application.port.out;

import com.evela.sigotrab.domain.model.AuditRecord;

public interface AuditPort {
    void record(AuditRecord event);
}
```

**Reglas:**

- Los puertos solo usan tipos de `domain/` y JDK. Nada de `Pageable`, `EntityManager` ni
  `JdbcTemplate`.
- `save` no devuelve nada: el id ya lo generó `JobOffer.register`.
- `existsBySourceReference` solo se llama cuando **ambos** valores están presentes (lo
  decide el servicio, Paso 8).

**Cómo verificar:** compila; `application/` no importa `adapters/` (ArchUnit).

---

## Paso 7 — Puertos de entrada

**Objetivo:** que el controlador dependa de casos de uso, no de servicios concretos.

**Dónde:** `application/port/in/`.

```java
package com.evela.sigotrab.application.port.in;

import com.evela.sigotrab.domain.model.ActorInfo;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.NewJobOffer;

public interface RegisterJobOfferUseCase {
    JobOffer register(NewJobOffer data, ActorInfo actor);
}
```

```java
package com.evela.sigotrab.application.port.in;

import com.evela.sigotrab.application.port.out.PageQuery;
import com.evela.sigotrab.application.port.out.PageResult;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;

import java.util.UUID;

public interface GetJobOffersUseCase {
    JobOffer getById(UUID id);
    PageResult<JobOffer> list(JobStatus statusOrNull, PageQuery page);
}
```

**Reglas:**

- `statusOrNull == null` significa "todas".
- `getById` lanza `JobNotFoundException` (ya existe) si no hay oferta.
- No metas `HttpServletRequest`, `Model` ni `BindingResult` en los puertos.

**Cómo verificar:** compila.

---

## Paso 8 — `RegisterJobOfferService`

**Objetivo:** orquestar el registro: deduplicar, crear el agregado, guardar y auditar
`CREATE`, todo en una transacción.

**Dónde:** `application/service/RegisterJobOfferService.java`.

**Contrato:**

```java
@Service
@Transactional
public class RegisterJobOfferService implements RegisterJobOfferUseCase {

    private final JobOfferRepository offers;
    private final AuditPort audit;
    private final Clock clock;

    public RegisterJobOfferService(JobOfferRepository offers, AuditPort audit, Clock clock) {
        this.offers = offers;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public JobOffer register(NewJobOffer data, ActorInfo actor) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(actor, "actor");
        Instant now = clock.instant();

        if (hasSourceReference(data)
                && offers.existsBySourceReference(data.sourceName(), data.externalReference())) {
            throw new DuplicateJobOfferException(data.sourceName(), data.externalReference());
        }

        JobOffer offer = JobOffer.register(data, actor.userId(), now);
        offers.save(offer);
        audit.record(AuditRecord.success(
                AuditAction.CREATE, "JOB_OFFER", offer.getId().toString(), actor, snapshot(offer)));
        return offer;
    }

    private static boolean hasSourceReference(NewJobOffer data) {
        return data.sourceName() != null && !data.sourceName().isBlank()
                && data.externalReference() != null && !data.externalReference().isBlank();
    }

    private static Map<String, Object> snapshot(JobOffer offer) {
        var values = new LinkedHashMap<String, Object>();
        values.put("title", offer.getTitle());
        values.put("company", offer.getCompany());
        values.put("status", offer.getStatus().name());
        values.put("sourceName", offer.getSourceName());
        values.put("externalReference", offer.getExternalReference());
        return values;
    }
}
```

**Reglas:**

- `Clock` ya es un bean (`SecurityConfig#clock`). No uses `Instant.now()` dentro del
  servicio: rompe la testabilidad.
- La comprobación de duplicado **solo** se hace si `sourceName` y `externalReference`
  están ambos presentes. Si faltan, la oferta se guarda y la UI avisa (PostgreSQL permite
  varios `NULL` en el `UNIQUE`).
- **No** uses `Map.of(...)` para el snapshot si algún valor puede ser `null`: lanza NPE.
  Usa `LinkedHashMap`.
- La auditoría va **después** del `save`, en la misma transacción: si el insert de
  auditoría falla, se revierte la oferta.
- La restricción `unique (source_name, external_reference)` es la red de seguridad ante
  una carrera; el pre-chequeo es solo UX. La carrera real se cubre en T9.

**Cómo verificar:** `./mvnw -B -DskipTests compile`. El test llega en el Paso 15.1.

---

## Paso 9 — `GetJobOffersService`

**Objetivo:** leer ofertas sin lógica de escritura.

**Dónde:** `application/service/GetJobOffersService.java`.

```java
@Service
@Transactional(readOnly = true)
public class GetJobOffersService implements GetJobOffersUseCase {

    private final JobOfferRepository offers;

    public GetJobOffersService(JobOfferRepository offers) {
        this.offers = offers;
    }

    @Override
    public JobOffer getById(UUID id) {
        return offers.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    @Override
    public PageResult<JobOffer> list(JobStatus status, PageQuery page) {
        return status == null ? offers.findAll(page) : offers.findByStatus(status, page);
    }
}
```

**Reglas:**

- `@Transactional(readOnly = true)` en la clase; no abras transacciones en el controlador.
- No devuelvas entidades JPA: devuelves `JobOffer` (dominio).

**Cómo verificar:** compila.

---

## Paso 10 — Persistencia

**Objetivo:** mapear `job_offer` ↔ `JobOffer` sin que el dominio se entere.

**Dónde:** `adapters/persistence/`.

### 10.1 `JobOfferJpaEntity`

**Contrato (campos y anotaciones):**

```java
@Entity
@Table(name = "job_offer")
@Getter @Setter @NoArgsConstructor
public class JobOfferJpaEntity {

    @Id
    private UUID id;                       // sin @GeneratedValue: el id lo genera el dominio

    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 150) private String company;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(nullable = false, columnDefinition = "text") private String requirements;
    @Column(length = 150) private String location;
    @Column(length = 100) private String salary;
    @Column(name = "employment_type", length = 50) private String employmentType;
    @Column(name = "source_url", nullable = false, length = 500) private String originalSourceUrl;
    @Column(name = "source_name", length = 60) private String sourceName;
    @Column(name = "external_reference", length = 120) private String externalReference;
    @Column(name = "extracted_at", nullable = false) private Instant extractedAt;
    @Column(name = "publication_url", length = 500) private String publicationUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private JobStatus status;

    @Column(name = "assigned_user_id") private UUID assignedUserId;
    @Column(name = "locked_at") private Instant lockedAt;
    @Column(name = "lock_expires_at") private Instant lockExpiresAt;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "published_at") private Instant publishedAt;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;

    @Version
    private Long version;                  // wrapper: null ⇒ entidad nueva ⇒ persist
}
```

**Reglas y trampas:**

- **Tabla singular `job_offer`**, no `job_offers`.
- `source_url` es la columna; el campo de dominio se llama `originalSourceUrl`. Mapea con
  `@Column(name = "source_url")`.
- `description` y `requirements` son `text` en `V1`: usa `columnDefinition = "text"` o
  `ddl-auto: validate` puede quejarse del tipo.
- `Instant` ↔ `timestamptz`: correcto. No uses `LocalDateTime` en la entidad.
- **No pongas `@GeneratedValue`**: el id viene de `JobOffer.register`. Si lo pones,
  Hibernate hará `merge` (SELECT + INSERT) en vez de `persist`.
- `@Version` con `Long` (no `long`): así `version == null` y `save` hace `persist`.
  Hibernate lo inicializa a `0` al insertar.
- `assignedUserId`, `createdBy` y `updatedBy` se mapean como `UUID` planos (no
  `@ManyToOne`). Evitas `LazyInitializationException` con `open-in-view: false`.
- No mapees `job_offer_status_history` ni `audit_event` aquí.

### 10.2 `JobOfferJpaRepository`

```java
public interface JobOfferJpaRepository extends JpaRepository<JobOfferJpaEntity, UUID> {
    Page<JobOfferJpaEntity> findByStatus(JobStatus status, Pageable pageable);
    boolean existsBySourceNameAndExternalReference(String sourceName, String externalReference);
}
```

> El `JobStatus` es el enum de dominio; con `@Enumerated(STRING)` la consulta derivada
> funciona sobre la columna `status`.

### 10.3 `JobOfferEntityMapper`

**Contrato:**

```java
@Component
public class JobOfferEntityMapper {

    public JobOffer toDomain(JobOfferJpaEntity e) {
        return JobOffer.reconstitute(
                e.getId(), e.getTitle(), e.getCompany(), e.getDescription(), e.getRequirements(),
                e.getLocation(), e.getSalary(), e.getEmploymentType(), e.getOriginalSourceUrl(),
                e.getSourceName(), e.getExternalReference(), e.getExtractedAt(), e.getPublicationUrl(),
                e.getStatus(), e.getAssignedUserId(), e.getLockedAt(), e.getLockExpiresAt(),
                e.getApprovedAt(), e.getPublishedAt(), e.getCreatedAt(), e.getCreatedBy(),
                e.getUpdatedAt(), e.getUpdatedBy());
    }

    public JobOfferJpaEntity toEntity(JobOffer d) {
        var e = new JobOfferJpaEntity();
        e.setId(d.getId());
        // ... copia todos los campos ...
        // NO copies version: lo gestiona Hibernate
        return e;
    }
}
```

**Reglas:**

- `toEntity` **no** toca `version`.
- `toDomain` usa `reconstitute`, nunca setters (no existen).
- No metas lógica de negocio aquí: es traducción pura.

### 10.4 `JobOfferPersistenceAdapter`

```java
@Repository
public class JobOfferPersistenceAdapter implements JobOfferRepository {

    private final JobOfferJpaRepository jpa;
    private final JobOfferEntityMapper mapper;

    @Override
    public void save(JobOffer jobOffer) {
        jpa.save(mapper.toEntity(jobOffer));
    }

    @Override
    public Optional<JobOffer> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<JobOffer> findAll(PageQuery page) {
        return toPageResult(jpa.findAll(pageable(page)), page);
    }

    @Override
    public PageResult<JobOffer> findByStatus(JobStatus status, PageQuery page) {
        return toPageResult(jpa.findByStatus(status, pageable(page)), page);
    }

    @Override
    public boolean existsBySourceReference(String sourceName, String externalReference) {
        return jpa.existsBySourceNameAndExternalReference(sourceName, externalReference);
    }

    private static Pageable pageable(PageQuery q) {
        return PageRequest.of(q.page(), q.size(), Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private PageResult<JobOffer> toPageResult(Page<JobOfferJpaEntity> p, PageQuery q) {
        return new PageResult<>(p.getContent().stream().map(mapper::toDomain).toList(),
                p.getTotalElements(), q.page(), q.size());
    }
}
```

**Reglas:**

- `Sort.by("createdAt")` usa la **propiedad de la entidad**, no la columna
  (`created_at`).
- El adaptador traduce `Page` → `PageResult`; `application/` no ve Spring Data.
- Si el `INSERT` viola el `UNIQUE` (carrera), saltará `DataIntegrityViolationException`
  al hacer commit. Para T3 basta con el pre-chequeo; en T9/T16 se traduce a
  `DuplicateJobOfferException` (por ejemplo, con `saveAndFlush` + `catch` en el adaptador).

**Cómo verificar:** `./mvnw -B -DskipTests compile` y, al arrancar, que
`ddl-auto: validate` no se queje.

---

## Paso 11 — Auditoría (`JpaAuditAdapter`)

**Objetivo:** insertar en `audit_event` sin pelear con el mapeo JPA de `jsonb`/`inet`.

**Dónde:** `infrastructure/audit/JpaAuditAdapter.java`.

**Enfoque recomendado: `JdbcTemplate` + casts explícitos.**

```java
package com.evela.sigotrab.infrastructure.audit;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.evela.sigotrab.application.port.out.AuditPort;
import com.evela.sigotrab.domain.model.AuditRecord;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JpaAuditAdapter implements AuditPort {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JpaAuditAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void record(AuditRecord e) {
        jdbc.update("""
                insert into audit_event
                    (user_id, username, action, entity_type, entity_id,
                     old_values, new_values, ip_address, success, error_code, reason)
                values (?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb),
                        cast(? as inet), ?, ?, ?)
                """,
                e.actor() == null ? null : e.actor().userId(),
                e.actor() == null ? null : e.actor().username(),
                e.action().name(),
                e.entityType(),
                e.entityId(),
                toJson(e.oldValues()),
                toJson(e.newValues()),
                e.actor() == null ? null : e.actor().ipAddress(),
                e.success(),
                e.errorCode(),
                e.reason());
    }

    private String toJson(Map<String, Object> values) {
        if (values == null) return null;
        try {
            return json.writeValueAsString(values);
        } catch (JacksonException ex) {
            throw new IllegalStateException("No se pudo serializar la auditoría", ex);
        }
    }
}
```

**Reglas y trampas:**

- `occurred_at` e `id` los pone la base; no los insertes.
- `cast(? as jsonb)` / `cast(? as inet)` evitan el error
  *"column is of type jsonb/inet but expression is of type character varying"*.
- Si PostgreSQL se queja de *"could not determine data type of parameter"* con `ip_address`
  nulo, usa un `PreparedStatementSetter` con `setNull(i, Types.VARCHAR)` para esa columna.
- `JdbcTemplate` participa en la transacción del servicio (`DataSourceUtils`), así que la
  auditoría y la oferta se confirman o revierten juntas.
- **No** metas `ObjectMapper` en `domain/`; la serialización es del adaptador.
- **Boot 4 trae Jackson 3**, cuyo paquete es `tools.jackson.*`, no `com.fasterxml.jackson.*`.
  Si copias un ejemplo de Boot 3, no compila. La excepción que se captura es
  `tools.jackson.core.JacksonException`.
- `entityType` en T3: `"JOB_OFFER"` (constante, no un enum nuevo).

**Cómo verificar:** al crear una oferta, `select ... from audit_event` debe mostrar una
fila `CREATE` con `success = true`.

---

## Paso 12 — Web

**Objetivo:** exponer `/offers`, `/offers/new`, `POST /offers`, `/offers/{id}` y
`/offers/registered` con validación de servidor y PRG.

**Dónde:** `adapters/web/`.

### 12.1 `JobOfferForm` (DTO de entrada)

```java
package com.evela.sigotrab.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public record JobOfferForm(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 150) String company,
        @NotBlank String description,
        @NotBlank String requirements,
        @Size(max = 150) String location,
        @Size(max = 100) String salary,
        @Size(max = 50) String employmentType,
        @NotBlank @Size(max = 500) @URL
        @Pattern(regexp = "^\\s*https?://\\S+\\s*$", message = "{offers.validation.url}")
        String originalSourceUrl,
        @Size(max = 60) String sourceName,
        @Size(max = 120) String externalReference,
        @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime extractedAt
) {
}
```

**Reglas:**

- Obligatorios (ADR-0007 / `V1`): `title`, `company`, `description`, `requirements`,
  `originalSourceUrl`, `extractedAt`. Opcionales: `location`, `salary`,
  `employmentType`, `sourceName`, `externalReference`.
- `extractedAt` se bindea como `LocalDateTime` (lo que envía
  `<input type="datetime-local">`) y se convierte a `Instant` en UTC en el mapper web.
- `@URL` valida sintaxis, pero **acepta `ftp://` y `file://`**; el `@Pattern` restringe el
  esquema a `http(s)://` (ver Paso 14.6). Sin esa segunda regla, un `href` a `file://` o a
  un esquema raro llega a la plantilla.
- Los mensajes por defecto de Bean Validation ya salen en español (locale fijado a `es`).
  El de la URL es la clave `offers.validation.url` (Paso 13.8).
- **Trampa de import:** `NotNull` debe venir de `jakarta.validation.constraints`. Si el IDE
  te ofrece `groovyjarjarantlr4.v4.runtime.misc.NotNull` (o cualquier paquete raro), la
  anotación no valida nada.

### 12.2 `JobOfferView` (DTO de salida)

```java
public record JobOfferView(
        UUID id,
        String title,
        String company,
        String description,
        String requirements,
        String location,
        String salary,
        String employmentType,
        String originalSourceUrl,
        String sourceName,
        String externalReference,
        String extractedAt,
        String publicationUrl,
        JobStatus status,
        String statusLabel,
        String createdAt,
        String updatedAt
) {
}
```

> Las fechas van **pre-formateadas** (`"2026-01-15 10:00 UTC"`). Así evitas depender de
> `#temporals` en Thymeleaf.

### 12.3 `JobOfferWebMapper`

**Contrato:**

```java
@Component
public class JobOfferWebMapper {

    public NewJobOffer toNewJobOffer(JobOfferForm f) {
        return new NewJobOffer(
                f.title().trim(),
                f.company().trim(),
                f.description(),
                f.requirements(),
                trimToNull(f.location()),
                trimToNull(f.salary()),
                trimToNull(f.employmentType()),
                f.originalSourceUrl().trim(),
                trimToNull(f.sourceName()),
                trimToNull(f.externalReference()),
                f.extractedAt().toInstant(ZoneOffset.UTC));
    }

    public JobOfferView toView(JobOffer o) {
        return new JobOfferView(
                o.getId(), o.getTitle(), o.getCompany(), o.getDescription(), o.getRequirements(),
                o.getLocation(), o.getSalary(), o.getEmploymentType(), o.getOriginalSourceUrl(),
                o.getSourceName(), o.getExternalReference(), fmt(o.getExtractedAt()),
                o.getPublicationUrl(), o.getStatus(), label(o.getStatus()),
                fmt(o.getCreatedAt()), fmt(o.getUpdatedAt()));
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private static String fmt(Instant i) { return i == null ? null : FMT.format(i); }

    private static String label(JobStatus s) {
        return switch (s) {
            case REGISTERED -> "Registrada";
            case APPROVED -> "Aprobada";
            case IN_PROGRESS -> "En curso";
            case PUBLISHED -> "Publicada";
            case REJECTED -> "Rechazada";
        };
    }
}
```

**Reglas:**

- `trimToNull` es importante: si `sourceName`/`externalReference` llegan vacíos, se guardan
  como `NULL` y el `UNIQUE` no bloquea ofertas sin fuente.
- La conversión `LocalDateTime → Instant` usa `ZoneOffset.UTC` (coherente con
  `hibernate.jdbc.time_zone: UTC`).

### 12.4 `OffersController`

**Contrato:**

```java
@Controller
@RequestMapping("/offers")
@PreAuthorize("hasAnyRole('REGISTRADOR','ADMIN')")
public class OffersController {

    private static final int PAGE_SIZE = 20;

    private final RegisterJobOfferUseCase registerJobOffer;
    private final GetJobOffersUseCase getJobOffers;
    private final JobOfferWebMapper mapper;
    private final Clock clock;

    // constructor con los cuatro

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        return renderList(null, page, model, false);
    }

    @GetMapping("/registered")
    public String registered(@RequestParam(defaultValue = "0") int page, Model model) {
        return renderList(JobStatus.REGISTERED, page, model, true);
    }

    private String renderList(JobStatus status, int page, Model model, boolean registered) {
        PageResult<JobOffer> result = getJobOffers.list(status, new PageQuery(page, PAGE_SIZE));
        model.addAttribute("offers", result.items().stream().map(mapper::toView).toList());
        model.addAttribute("page", result);
        model.addAttribute("totalElements", result.totalElements());
        model.addAttribute("registered", registered);
        model.addAttribute("baseUrl", registered ? "/offers/registered" : "/offers");
        model.addAttribute("activeNav", "offers");
        return "offers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new JobOfferForm(
                null, null, null, null, null, null, null, null, null, null,
                LocalDateTime.now(clock)));
        model.addAttribute("activeNav", "new");
        return "offers/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") JobOfferForm form,
                         BindingResult binding,
                         @AuthenticationPrincipal SigUser user,
                         HttpServletRequest request,
                         RedirectAttributes ra) {
        // BindingResult primero: si el formulario no es válido no hace falta el actor.
        if (binding.hasErrors()) {
            return "offers/form";
        }
        if (user == null) {
            return "redirect:/login";
        }
        ActorInfo actor = new ActorInfo(user.id(), user.getUsername(), request.getRemoteAddr());
        JobOffer offer;
        try {
            offer = registerJobOffer.register(mapper.toNewJobOffer(form), actor);
        } catch (DuplicateJobOfferException ex) {
            binding.reject("duplicate", ex.getMessage());
            return "offers/form";
        }
        ra.addFlashAttribute("success", true);
        return "redirect:/offers/" + offer.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("offer", mapper.toView(getJobOffers.getById(id)));
        model.addAttribute("activeNav", "offers");
        return "offers/detail";
    }

    @ExceptionHandler(JobNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() {
        return "offers/not-found";
    }
}
```

**Reglas:**

- `BindingResult` va **inmediatamente después** del parámetro `@Valid`; si no, Spring no
  lo enlaza y lanza `IllegalStateException`.
- El actor se construye desde `SigUser` (`user.id()`, `user.getUsername()`) y
  `request.getRemoteAddr()`. No pases `SigUser` a `application/`.
- PRG: tras crear, `redirect:/offers/{id}`. Nunca renderizar el detalle directamente en el
  `POST` (recarga duplicada).
- El duplicado se traduce a **error global** de formulario (`binding.reject`), no a `500`;
  afecta a la pareja fuente + referencia, no a un campo suelto.
- Defensa en profundidad: la clase lleva
  `@PreAuthorize("hasAnyRole('REGISTRADOR','ADMIN')")`; `@EnableMethodSecurity` ya está
  activo en `SecurityConfig`.
- El guard de `user == null` va **después** de `binding.hasErrors()`: con `@WithMockUser`
  el principal no es `SigUser` y el test de errores de campo debe poder renderizar el
  formulario. En producción el login siempre crea un `SigUser`.
- `Clock` se inyecta en el **constructor**; Spring MVC no inyecta beans arbitrarios como
  parámetros de un handler.
- `/{id}` no choca con `/new` ni `/registered`: Spring prioriza las rutas literales.
- Opcional: añade `model.addAttribute("activeNav", "offers")` en `renderList`/`detail` y
  `"new"` en `newForm` para que la barra marque la sección activa (Paso 13.2). Si no lo
  haces, no falla: el atributo simplemente no se renderiza.

**Cómo verificar:** `./mvnw -B -DskipTests compile`.

---

## Paso 13 — Frontend: dirección visual, plantillas e i18n

**Objetivo:** pantallas en español con Bootstrap 5, responsive de verdad y con una
identidad propia (no el look de plantilla por defecto). Sin SPA y **sin fuentes ni CDN
externos**: la app corre en LAN sin internet.

**Dónde:** `src/main/resources/static/css/app.css`,
`src/main/resources/templates/layout.html`,
`src/main/resources/templates/offers/*.html` y
`src/main/resources/messages/messages_es.properties`.

> Bootstrap 5 se queda como **rejilla y utilidades**; la identidad vive en `app.css`.
> No recompilas Bootstrap: redefines sus tokens (`--bs-btn-*`, `--bs-body-*`).

### 13.0 Dirección de diseño — "mesa de curaduría"

SIGO no es un CRUD de ofertas: es un **registro** donde alguien revisa fichas que llegan
de fuera. La interfaz debe sentirse como una mesa de curaduría: papel frío, tinta petróleo
y un **sello de estado** que es el único elemento con carácter. Todo lo demás calla.

**Paleta** (fría; el ámbar se reserva para la acción primaria y el estado `REGISTERED`):

| Token | Hex | Uso |
|---|---|---|
| `--sigo-petrol` | `#10454d` | marca, botón primario, enlaces |
| `--sigo-ink` | `#16262e` | texto principal |
| `--sigo-paper` | `#eef2f1` | fondo |
| `--sigo-surface` | `#ffffff` | fichas y formularios |
| `--sigo-line` | `#cfdad8` | reglas y bordes |
| `--sigo-amber` | `#8f5c12` | foco y acción |

Estados (siempre con **texto**, nunca solo color): registrada `#8f5c12`, aprobada
`#2b5c96`, en curso `#1d6f66`, publicada `#2f6b3a`, rechazada `#9c3b32`. Los cinco pasan
contraste AA sobre blanco.

**Tipografía** (solo familias del sistema, cero peticiones de red):

| Rol | Familia | Por qué |
|---|---|---|
| Títulos | `"Iowan Old Style", "Palatino Linotype", Palatino, Georgia, ui-serif, serif` | tono de registro impreso, no de panel SaaS |
| UI y cuerpo | `system-ui, -apple-system, "Segoe UI", Roboto, sans-serif` | legible y sin descargas |
| Datos y referencias | `ui-monospace, "Cascadia Mono", Consolas, monospace` | IDs y referencias se comparan carácter a carácter |

Escala: `h1` `clamp(1.75rem, 1.2rem + 2vw, 2.5rem)`, `h2` `1.35rem`, cuerpo `1rem/1.6`,
`small` `.875rem`. Longitud de lectura: **68ch** en descripción y requisitos.

**Layout**

```text
ESCRITORIO (>=992px)                          MOVIL (<768px)
+------------------------------+              +------------------+
| SIGO   Panel  Ofertas  [ + ] |              | SIGO         [=] |
+------------------------------+              +------------------+
| Registro de ofertas     12   |              | Registro     12  |
| ---------------------------- |              | [Registrar]      |
| OFERTA      EMPRESA  SELLO   |              | +--------------+ |
| ---------------------------- |              | | Analista     | |
| Analista    ACME     [REG]   |              | | ACME         | |
| Disenador   Beta     [REG]   |              | | [REG] 15 ene | |
+------------------------------+              | +--------------+ |
                                              +------------------+
```

Detalle en escritorio: ficha a la izquierda (máx. 68ch) y metadatos en una rejilla
lateral; en móvil, una sola columna y el sello arriba. El listado es una **cuadrícula que
se reordena**, no una tabla que se rompe ni markup duplicado.

**Principios**

1. El **sello** es lo memorable; no hay sombras grandes, tarjetas idénticas ni degradados.
2. La estructura codifica información: la regla superior es cabecera de registro y el
   filete izquierdo de cada fila es el color del estado.
3. Español de interfaz en sentence case. La única mayúscula es la del sello, porque es una
   metáfora de tampón, no un eyebrow decorativo.
4. Responsive real: una sola plantilla por pantalla.
5. Movimiento solo como respuesta (hover/focus, 120 ms). Nada en carga.
   `prefers-reduced-motion` lo desactiva.

**Qué evité (revisión contra el brief):** crema + serif cálida + terracota, negro casi puro
con acento ácido, tarjetas SaaS con la misma sombra, eyebrows `01 / 02 / 03` (el contenido
no es una secuencia) y etiquetas tipo `PALABRA — fragmento`.

### 13.1 `app.css`

Sustituye el contenido actual (que solo tiene `.login-card`) por este tema. Está pensado
para funcionar **encima** de Bootstrap 5.3: si una regla choca, gana la de aquí porque se
carga después.

```css
/* ==========================================================================
   SIGO - tema "mesa de curaduria"
   Bootstrap 5 aporta rejilla y utilidades; aqui vive la identidad.
   Sin fuentes externas: la app corre en LAN sin internet.
   ========================================================================== */

:root {
  --sigo-petrol: #10454d;
  --sigo-petrol-dark: #0b333a;
  --sigo-ink: #16262e;
  --sigo-muted: #56686e;
  --sigo-paper: #eef2f1;
  --sigo-surface: #ffffff;
  --sigo-line: #cfdad8;
  --sigo-amber: #8f5c12;

  --sigo-status-registered: #8f5c12;
  --sigo-status-approved: #2b5c96;
  --sigo-status-progress: #1d6f66;
  --sigo-status-published: #2f6b3a;
  --sigo-status-rejected: #9c3b32;

  --sigo-font-display: "Iowan Old Style", "Palatino Linotype", Palatino, Georgia, ui-serif, serif;
  --sigo-font-ui: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  --sigo-font-mono: ui-monospace, "Cascadia Mono", "SFMono-Regular", Consolas, monospace;

  --sigo-radius: .375rem;
  --sigo-shadow: 0 1px 2px rgba(22, 38, 46, .06), 0 18px 40px -32px rgba(22, 38, 46, .55);

  /* Tokens de Bootstrap que se pueden redefinir sin recompilar */
  --bs-body-bg: var(--sigo-paper);
  --bs-body-color: var(--sigo-ink);
  --bs-body-font-family: var(--sigo-font-ui);
  --bs-border-color: var(--sigo-line);
  --bs-link-color: var(--sigo-petrol);
  --bs-link-hover-color: var(--sigo-petrol-dark);
  --bs-focus-ring-color: rgba(143, 92, 18, .35);
}

body {
  background: var(--sigo-paper);
  color: var(--sigo-ink);
  font-family: var(--sigo-font-ui);
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

h1, h2, h3 {
  font-family: var(--sigo-font-display);
  font-weight: 600;
  letter-spacing: -.01em;
  color: var(--sigo-ink);
}
h1 { font-size: clamp(1.75rem, 1.2rem + 2vw, 2.5rem); line-height: 1.15; }
h2 { font-size: 1.35rem; }

a { text-underline-offset: .15em; }
.prose { max-width: 68ch; }
.mono { font-family: var(--sigo-font-mono); font-size: .875rem; }
.link-quiet { color: var(--sigo-muted); }
.link-quiet:hover { color: var(--sigo-ink); }

/* Foco visible y consistente: no lo quites, cambia su forma. */
:focus-visible {
  outline: 3px solid var(--sigo-amber);
  outline-offset: 2px;
  border-radius: 2px;
}

/* --- Botones: se redefinen los tokens de Bootstrap ---------------------- */
.btn { --bs-btn-font-weight: 600; --bs-btn-padding-x: 1rem; }

.btn-primary {
  --bs-btn-bg: var(--sigo-petrol);
  --bs-btn-border-color: var(--sigo-petrol);
  --bs-btn-hover-bg: var(--sigo-petrol-dark);
  --bs-btn-hover-border-color: var(--sigo-petrol-dark);
  --bs-btn-active-bg: var(--sigo-petrol-dark);
  --bs-btn-active-border-color: var(--sigo-petrol-dark);
  --bs-btn-disabled-bg: var(--sigo-petrol);
  --bs-btn-disabled-border-color: var(--sigo-petrol);
}

.btn-quiet {
  background: var(--sigo-surface);
  --bs-btn-color: var(--sigo-petrol);
  --bs-btn-border-color: var(--sigo-line);
  --bs-btn-hover-bg: #e3eae8;
  --bs-btn-hover-border-color: var(--sigo-line);
  --bs-btn-hover-color: var(--sigo-petrol-dark);
  --bs-btn-active-bg: #dbe4e1;
  --bs-btn-active-color: var(--sigo-petrol-dark);
}

/* --- Barra de navegacion ------------------------------------------------ */
.app-nav {
  position: sticky;
  top: 0;
  z-index: 1030;
  background: var(--sigo-surface);
  border-bottom: 1px solid var(--sigo-line);
}
.app-nav .navbar-brand {
  font-family: var(--sigo-font-display);
  font-size: 1.4rem;
  font-weight: 600;
  letter-spacing: .06em;
  color: var(--sigo-ink);
}
.app-nav .nav-link {
  color: var(--sigo-muted);
  font-weight: 600;
  border-bottom: 2px solid transparent;
  padding-block: .35rem;
}
.app-nav .nav-link:hover { color: var(--sigo-ink); }
.app-nav .nav-link[aria-current="page"] {
  color: var(--sigo-ink);
  border-bottom-color: var(--sigo-amber);
}
.app-nav .navbar-toggler { border-color: var(--sigo-line); }
@media (max-width: 991.98px) {
  .app-nav .navbar-collapse { padding-block: .75rem; }
}

/* --- Contenedor --------------------------------------------------------- */
.app-main { padding-block: 2rem 4rem; }
@media (min-width: 768px) { .app-main { padding-block: 2.75rem 5rem; } }

/* --- Cabecera de registro (masthead) ------------------------------------ */
.masthead {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  justify-content: space-between;
  gap: 1rem 1.5rem;
  padding-bottom: .9rem;
  border-bottom: 2px solid var(--sigo-ink);
  margin-bottom: 1.5rem;
}
.masthead__count {
  font-family: var(--sigo-font-mono);
  font-size: .8125rem;
  color: var(--sigo-muted);
  letter-spacing: .02em;
}
.masthead__actions { display: flex; flex-wrap: wrap; gap: .5rem; }
@media (max-width: 575.98px) {
  .masthead__actions { width: 100%; }
  .masthead__actions .btn { flex: 1 1 auto; }
}

/* --- Sello de estado (el unico elemento con caracter) ------------------- */
.seal {
  display: inline-flex;
  align-items: center;
  gap: .35rem;
  padding: .18rem .5rem .16rem;
  border: 1.5px solid currentColor;
  border-radius: .2rem;
  outline: 1px solid currentColor;
  outline-offset: 2px;
  background: var(--sigo-surface);
  color: var(--sigo-muted);
  font-size: .6875rem;
  font-weight: 700;
  letter-spacing: .1em;
  line-height: 1.35;
  text-transform: uppercase;
  white-space: nowrap;
}
.seal--registered { color: var(--sigo-status-registered); background: #fbf4e8; }
.seal--approved   { color: var(--sigo-status-approved);   background: #eef3fa; }
.seal--in_progress { color: var(--sigo-status-progress);  background: #eaf4f2; }
.seal--published  { color: var(--sigo-status-published);  background: #edf5ee; }
.seal--rejected   { color: var(--sigo-status-rejected);   background: #fbeeec; }
.seal--lg { font-size: .8125rem; padding: .35rem .8rem; transform: rotate(-1.5deg); }

/* --- Registro: lista tipo ledger ---------------------------------------- */
.ledger { border-top: 1px solid var(--sigo-line); }
.ledger__head,
.ledger__row {
  display: grid;
  grid-template-columns: minmax(0, 2.4fr) minmax(0, 1.3fr) auto minmax(0, .85fr) 1.25rem;
  align-items: center;
  gap: .75rem 1.25rem;
  padding: .85rem .25rem .85rem .9rem;
  position: relative;
}
.ledger__head {
  font-size: .75rem;
  font-weight: 700;
  letter-spacing: .04em;
  color: var(--sigo-muted);
  border-bottom: 1px solid var(--sigo-line);
}
.ledger__body { margin: 0; padding: 0; list-style: none; }
.ledger__row {
  color: inherit;
  text-decoration: none;
  border-bottom: 1px solid var(--sigo-line);
  transition: background-color .12s ease;
}
.ledger__row::before {
  content: "";
  position: absolute;
  inset: .55rem auto .55rem 0;
  width: 3px;
  border-radius: 3px;
  background: var(--sigo-row-accent, transparent);
}
.ledger__row:hover { background: #e7edeb; }
.ledger__row:focus-visible { outline-offset: -3px; }
.ledger__title { font-weight: 600; }
.ledger__company { color: var(--sigo-muted); }
.ledger__date { font-family: var(--sigo-font-mono); font-size: .8125rem; color: var(--sigo-muted); }
.ledger__go { color: var(--sigo-muted); }
.ledger__row:hover .ledger__go { color: var(--sigo-ink); }

.ledger__row--registered  { --sigo-row-accent: var(--sigo-status-registered); }
.ledger__row--approved    { --sigo-row-accent: var(--sigo-status-approved); }
.ledger__row--in_progress { --sigo-row-accent: var(--sigo-status-progress); }
.ledger__row--published   { --sigo-row-accent: var(--sigo-status-published); }
.ledger__row--rejected    { --sigo-row-accent: var(--sigo-status-rejected); }

@media (max-width: 767.98px) {
  .ledger__head { display: none; }
  .ledger__row {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas:
      "title   seal"
      "company date";
    gap: .25rem .75rem;
    padding: 1rem .5rem 1rem .9rem;
  }
  .ledger__title { grid-area: title; }
  .ledger__company { grid-area: company; }
  .ledger__status { grid-area: seal; justify-self: end; }
  .ledger__date { grid-area: date; justify-self: end; }
  .ledger__go { display: none; }
}

/* --- Paginacion --------------------------------------------------------- */
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding-top: 1.25rem;
}
.pager__status {
  font-family: var(--sigo-font-mono);
  font-size: .8125rem;
  color: var(--sigo-muted);
}

/* --- Ficha de detalle --------------------------------------------------- */
.dossier {
  background: var(--sigo-surface);
  border: 1px solid var(--sigo-line);
  border-radius: var(--sigo-radius);
  box-shadow: var(--sigo-shadow);
  padding: clamp(1.25rem, 1rem + 1.5vw, 2.5rem);
}
.dossier__head {
  display: flex;
  flex-wrap: wrap;
  align-items: start;
  justify-content: space-between;
  gap: 1rem;
  padding-bottom: 1.25rem;
  border-bottom: 1px solid var(--sigo-line);
  margin-bottom: 1.5rem;
}
.dossier__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: 1rem 1.5rem;
  margin: 0;
}
.dossier__meta dt {
  font-size: .75rem;
  font-weight: 700;
  color: var(--sigo-muted);
  letter-spacing: .03em;
}
.dossier__meta dd { margin: .15rem 0 0; overflow-wrap: anywhere; }
.dossier__section + .dossier__section {
  margin-top: 2rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--sigo-line);
}
.dossier__section h2 { font-size: 1.1rem; margin-bottom: .5rem; }
.dossier__text { white-space: pre-line; max-width: 68ch; }

/* --- Formulario --------------------------------------------------------- */
.form-sheet {
  background: var(--sigo-surface);
  border: 1px solid var(--sigo-line);
  border-radius: var(--sigo-radius);
  box-shadow: var(--sigo-shadow);
  padding: clamp(1.25rem, 1rem + 1.5vw, 2rem);
}
.form-section { border: 0; padding: 0; margin: 0 0 2rem; min-width: 0; }
.form-section:last-of-type { margin-bottom: 0; }
.form-section > legend {
  float: none;
  width: 100%;
  font-family: var(--sigo-font-display);
  font-size: 1.15rem;
  font-weight: 600;
  padding-bottom: .4rem;
  margin-bottom: 1rem;
  border-bottom: 1px solid var(--sigo-line);
}
.form-label { font-weight: 600; font-size: .875rem; }
.form-text { color: var(--sigo-muted); }
.form-control, .form-select { border-color: var(--sigo-line); }
.form-control:focus, .form-select:focus {
  border-color: var(--sigo-petrol);
  box-shadow: 0 0 0 .2rem rgba(16, 69, 77, .15);
}
.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: .5rem;
  padding-top: 1.25rem;
  margin-top: 1.5rem;
  border-top: 1px solid var(--sigo-line);
}
.req { color: var(--sigo-status-rejected); }
@media (max-width: 575.98px) {
  .form-actions .btn { flex: 1 1 auto; }
}

/* --- Estados vacios y 404 ----------------------------------------------- */
.blank {
  background: var(--sigo-surface);
  border: 1px solid var(--sigo-line);
  border-radius: var(--sigo-radius);
  padding: clamp(2rem, 1.5rem + 3vw, 4rem);
  text-align: center;
  max-width: 40rem;
  margin-inline: auto;
}
.blank__mark {
  font-family: var(--sigo-font-display);
  font-size: 3rem;
  line-height: 1;
  color: var(--sigo-line);
  margin-bottom: 1rem;
}

/* --- Login -------------------------------------------------------------- */
.login-card { max-width: 24rem; border-top: 3px solid var(--sigo-petrol); }

/* --- Movimiento: solo como respuesta ------------------------------------ */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    transition-duration: .01ms !important;
    animation-duration: .01ms !important;
    scroll-behavior: auto !important;
  }
  .seal--lg { transform: none; }
}
```

### 13.2 `layout.html`

El layout no impone el `<main>`: cada plantilla lo declara para poder darle su propio
contenedor y para que el enlace "saltar al contenido" siempre apunte a algo real.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security"
      th:fragment="layout(title, content)"
      lang="es">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title th:replace="${title}">SIGO</title>
  <link rel="stylesheet" th:href="@{/webjars/bootstrap/5.3.8/css/bootstrap.min.css}"/>
  <link rel="stylesheet" th:href="@{/webjars/bootstrap-icons/1.13.2/font/bootstrap-icons.min.css}"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
</head>
<body>

<a class="visually-hidden-focusable position-absolute top-0 start-0 m-2 btn btn-sm btn-primary"
   href="#contenido" th:text="#{nav.skip}">Saltar al contenido</a>

<header class="app-nav navbar navbar-expand-lg" sec:authorize="isAuthenticated()">
  <div class="container">
    <a class="navbar-brand me-4" th:href="@{/dashboard}">SIGO</a>

    <button class="navbar-toggler" type="button"
            data-bs-toggle="collapse" data-bs-target="#navPrincipal"
            aria-controls="navPrincipal" aria-expanded="false"
            aria-label="Abrir menú">
      <span class="navbar-toggler-icon"></span>
    </button>

    <nav class="collapse navbar-collapse" id="navPrincipal" aria-label="Principal">
      <div class="navbar-nav me-auto">
        <a class="nav-link" th:href="@{/dashboard}"
           th:attr="aria-current=${activeNav == 'dashboard'} ? 'page' : null"
           th:text="#{nav.dashboard}">Panel</a>
        <a class="nav-link" th:href="@{/offers}"
           th:attr="aria-current=${activeNav == 'offers'} ? 'page' : null"
           th:text="#{nav.offers}">Ofertas</a>
        <a class="nav-link" sec:authorize="hasAnyRole('REGISTRADOR','ADMIN')"
           th:href="@{/offers/new}"
           th:attr="aria-current=${activeNav == 'new'} ? 'page' : null"
           th:text="#{nav.new}">Registrar</a>
      </div>

      <form class="d-flex align-items-center gap-3 mt-3 mt-lg-0"
            th:action="@{/logout}" method="post">
        <span class="small text-body-secondary" th:text="${#authentication.name}">usuario</span>
        <button type="submit" class="btn btn-sm btn-quiet" th:text="#{nav.logout}">Salir</button>
      </form>
    </nav>
  </div>
</header>

<th:block th:replace="${content}"></th:block>

<script th:src="@{/webjars/bootstrap/5.3.8/js/bootstrap.bundle.min.js}"></script>
</body>
</html>
```

> **`activeNav` es opcional.** Si no lo pones en el modelo, `${activeNav == 'offers'}` es
> `false`, el atributo `aria-current` no se renderiza y la barra simplemente no marca la
> sección. Para activarlo, añade `model.addAttribute("activeNav", "offers")` en
> `renderList`/`detail` y `"new"` en `newForm` (Paso 12.4).
>
> **La barra solo se pinta con sesión iniciada** (`sec:authorize="isAuthenticated()"` en el
> `<header>`). Sin eso, el login enseña "Panel / Ofertas" y un botón "Salir" para
> `anonymousUser`, que no puede cerrar ninguna sesión.

### 13.3 `list.html`

Un solo markup para escritorio y móvil. La fila entera es el enlace (área táctil grande);
el estado se pinta con el sello y con el filete izquierdo.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security"
      th:replace="~{layout :: layout(~{::title}, ~{::main})}">
<head>
  <title th:text="${registered} ? #{offers.registered} : #{offers.title}">Ofertas - SIGO</title>
</head>
<body>
<main id="contenido" class="app-main container">

  <div class="masthead">
    <div>
      <h1 class="mb-1" th:text="${registered} ? #{offers.registered} : #{offers.title}">Ofertas</h1>
      <p class="masthead__count mb-0"
         th:text="${totalElements == 1} ? #{offers.total.one} : #{offers.total.other(${totalElements})}">12 ofertas en el registro</p>
    </div>
    <div class="masthead__actions">
      <a class="btn btn-quiet btn-sm" th:if="${!registered}"
         th:href="@{/offers/registered}" th:text="#{offers.filter.registered}">Solo registradas</a>
      <a class="btn btn-quiet btn-sm" th:if="${registered}"
         th:href="@{/offers}" th:text="#{offers.filter.all}">Ver todas</a>
      <a class="btn btn-primary btn-sm" sec:authorize="hasAnyRole('REGISTRADOR','ADMIN')"
         th:href="@{/offers/new}" th:text="#{offers.new}">Registrar oferta</a>
    </div>
  </div>

  <div class="blank" th:if="${#lists.isEmpty(offers)}">
    <p class="blank__mark" aria-hidden="true">0</p>
    <p class="mb-3" th:text="#{offers.empty}">No hay ofertas para mostrar.</p>
    <a class="btn btn-primary" sec:authorize="hasAnyRole('REGISTRADOR','ADMIN')"
       th:href="@{/offers/new}" th:text="#{offers.new}">Registrar oferta</a>
  </div>

  <div class="ledger" th:unless="${#lists.isEmpty(offers)}">
    <div class="ledger__head" aria-hidden="true">
      <span th:text="#{offers.col.offer}">Oferta</span>
      <span th:text="#{offers.col.company}">Empresa</span>
      <span th:text="#{offers.col.status}">Estado</span>
      <span th:text="#{offers.col.extracted}">Extraída</span>
      <span></span>
    </div>

    <ul class="ledger__body">
      <li th:each="o : ${offers}">
        <!-- o.status es el enum; name() da REGISTERED, IN_PROGRESS... -->
        <a class="ledger__row"
           th:classappend="'ledger__row--' + ${#strings.toLowerCase(o.status.name())}"
           th:href="@{'/offers/' + ${o.id}}">
          <span class="ledger__title" th:text="${o.title}">Analista de datos</span>
          <span class="ledger__company" th:text="${o.company}">ACME</span>
          <span class="ledger__status">
            <span class="seal"
                  th:classappend="'seal--' + ${#strings.toLowerCase(o.status.name())}"
                  th:text="${o.statusLabel}">Registrada</span>
          </span>
          <span class="ledger__date" th:text="${o.extractedAt}">2026-01-15 10:00 UTC</span>
          <i class="bi bi-arrow-right ledger__go" aria-hidden="true"></i>
          <span class="visually-hidden" th:text="#{offers.view}">Ver</span>
        </a>
      </li>
    </ul>
  </div>

  <nav class="pager" th:if="${page.totalPages() > 1}" aria-label="Paginacion">
    <a class="btn btn-sm btn-quiet" th:if="${page.hasPrevious()}"
       th:href="@{${baseUrl}(page=${page.page() - 1})}"
       th:text="#{offers.page.prev}">Anterior</a>
    <span class="pager__status"
          th:text="#{offers.page.status(${page.page() + 1}, ${page.totalPages()})}">Página 1 de 2</span>
    <a class="btn btn-sm btn-quiet" th:if="${page.hasNext()}"
       th:href="@{${baseUrl}(page=${page.page() + 1})}"
       th:text="#{offers.page.next}">Siguiente</a>
  </nav>

</main>
</body>
</html>
```

**Reglas:**

- `th:href="@{${baseUrl}(page=...)}"` construye la URL con el parámetro `page` ya
  codificado; no concatenes `?page=`.
- `#strings.toLowerCase(o.status.name())` produce `registered`, `in_progress`, etc. Por eso
  el CSS define `.seal--in_progress` con guion bajo.
- El `visually-hidden` "Ver" da contexto al lector de pantalla; la fila ya es un enlace.
- No uses `th:utext` con `title` ni `company`: son datos de terceros.

### 13.4 `form.html`

Formulario de dos columnas en escritorio (puesto / condiciones y procedencia) y una sola en
móvil. Los `fieldset`/`legend` agrupan de verdad, no son decoración.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout :: layout(~{::title}, ~{::main})}">
<head>
  <title th:text="#{offers.new} + ' - SIGO'">Registrar oferta - SIGO</title>
</head>
<body>
<main id="contenido" class="app-main container">

  <div class="masthead">
    <div>
      <h1 class="mb-1" th:text="#{offers.new}">Registrar oferta</h1>
      <p class="text-body-secondary mb-0" th:text="#{offers.form.hint}">Los campos con * son obligatorios.</p>
    </div>
    <div class="masthead__actions">
      <a class="btn btn-quiet btn-sm" th:href="@{/offers}" th:text="#{offers.cancel}">Cancelar</a>
    </div>
  </div>

  <form class="form-sheet" th:action="@{/offers}" th:object="${form}"
        method="post" novalidate>

    <div class="alert alert-danger" role="alert" th:if="${#fields.hasGlobalErrors()}">
      <p class="fw-semibold mb-1" th:text="#{offers.form.errors}">Revisa la oferta antes de guardarla.</p>
      <ul class="mb-0">
        <li th:each="e : ${#fields.globalErrors()}" th:text="${e}">Error</li>
      </ul>
    </div>

    <div class="row g-4">
      <div class="col-lg-7">

        <fieldset class="form-section">
          <legend th:text="#{offers.form.section.job}">Puesto</legend>

          <div class="mb-3">
            <label class="form-label" for="title">
              <span th:text="#{offers.field.title}">Título</span>
              <span class="req" aria-hidden="true">*</span>
            </label>
            <input class="form-control" id="title" name="title" type="text"
                   maxlength="200" required th:value="${form.title}"
                   th:classappend="${#fields.hasErrors('title')} ? 'is-invalid'"
                   th:attr="aria-describedby=${#fields.hasErrors('title')} ? 'title-error' : null">
            <div class="invalid-feedback" id="title-error"
                 th:if="${#fields.hasErrors('title')}" th:errors="*{title}">Indica un título.</div>
          </div>

          <div class="mb-3">
            <label class="form-label" for="company">
              <span th:text="#{offers.field.company}">Empresa</span>
              <span class="req" aria-hidden="true">*</span>
            </label>
            <input class="form-control" id="company" name="company" type="text"
                   maxlength="150" required th:value="${form.company}"
                   th:classappend="${#fields.hasErrors('company')} ? 'is-invalid'"
                   th:attr="aria-describedby=${#fields.hasErrors('company')} ? 'company-error' : null">
            <div class="invalid-feedback" id="company-error"
                 th:if="${#fields.hasErrors('company')}" th:errors="*{company}">Indica la empresa.</div>
          </div>

          <div class="mb-3">
            <label class="form-label" for="description">
              <span th:text="#{offers.field.description}">Descripción</span>
              <span class="req" aria-hidden="true">*</span>
            </label>
            <textarea class="form-control" id="description" name="description" rows="6" required
                      th:text="${form.description}"
                      th:classappend="${#fields.hasErrors('description')} ? 'is-invalid'"
                      th:attr="aria-describedby=${#fields.hasErrors('description')} ? 'description-help description-error' : 'description-help'"></textarea>
            <div class="form-text" id="description-help"
                 th:text="#{offers.field.description.help}">Qué se ofrece y a quién.</div>
            <div class="invalid-feedback" id="description-error"
                 th:if="${#fields.hasErrors('description')}" th:errors="*{description}">Describe la oferta.</div>
          </div>

          <div class="mb-0">
            <label class="form-label" for="requirements">
              <span th:text="#{offers.field.requirements}">Requisitos</span>
              <span class="req" aria-hidden="true">*</span>
            </label>
            <textarea class="form-control" id="requirements" name="requirements" rows="6" required
                      th:text="${form.requirements}"
                      th:classappend="${#fields.hasErrors('requirements')} ? 'is-invalid'"
                      th:attr="aria-describedby=${#fields.hasErrors('requirements')} ? 'requirements-help requirements-error' : 'requirements-help'"></textarea>
            <div class="form-text" id="requirements-help"
                 th:text="#{offers.field.requirements.help}">Formación, experiencia y habilidades.</div>
            <div class="invalid-feedback" id="requirements-error"
                 th:if="${#fields.hasErrors('requirements')}" th:errors="*{requirements}">Indica los requisitos.</div>
          </div>
        </fieldset>

      </div>

      <div class="col-lg-5">

        <fieldset class="form-section">
          <legend th:text="#{offers.form.section.conditions}">Condiciones</legend>

          <div class="mb-3">
            <label class="form-label" for="location" th:text="#{offers.field.location}">Ubicación</label>
            <input class="form-control" id="location" name="location" type="text"
                   maxlength="150" th:value="${form.location}"
                   th:classappend="${#fields.hasErrors('location')} ? 'is-invalid'"
                   th:attr="aria-describedby=${#fields.hasErrors('location')} ? 'location-error' : null">
            <div class="invalid-feedback" id="location-error"
                 th:if="${#fields.hasErrors('location')}" th:errors="*{location}">Revisa la ubicación.</div>
          </div>

          <div class="mb-3">
            <label class="form-label" for="salary" th:text="#{offers.field.salary}">Salario</label>
            <input class="form-control" id="salary" name="salary" type="text"
                   maxlength="100" th:value="${form.salary}"
                   th:classappend="${#fields.hasErrors('salary')} ? 'is-invalid'"
                   th:attr="aria-describedby=${#fields.hasErrors('salary')} ? 'salary-error' : null">
            <div class="invalid-feedback" id="salary-error"
                 th:if="${#fields.hasErrors('salary')}" th:errors="*{salary}">Revisa el salario.</div>
          </div>

          <div class="mb-0">
            <label class="form-label" for="employmentType"
                   th:text="#{offers.field.employmentType}">Tipo de contratación</label>
            <input class="form-control" id="employmentType" name="employmentType" type="text"
                   maxlength="50" th:value="${form.employmentType}"
                   th:classappend="${#fields.hasErrors('employmentType')} ? 'is-invalid'"
                   th:attr="aria-describedby=${#fields.hasErrors('employmentType')} ? 'employmentType-error' : null">
            <div class="invalid-feedback" id="employmentType-error"
                 th:if="${#fields.hasErrors('employmentType')}" th:errors="*{employmentType}">Revisa el tipo.</div>
          </div>
        </fieldset>

        <fieldset class="form-section">
          <legend th:text="#{offers.form.section.source}">Procedencia</legend>

          <div class="mb-3">
            <label class="form-label" for="originalSourceUrl">
              <span th:text="#{offers.field.originalSourceUrl}">URL de origen</span>
              <span class="req" aria-hidden="true">*</span>
            </label>
            <input class="form-control" id="originalSourceUrl" name="originalSourceUrl"
                   type="url" inputmode="url" maxlength="500" required
                   placeholder="https://" th:value="${form.originalSourceUrl}"
                   th:classappend="${#fields.hasErrors('originalSourceUrl')} ? 'is-invalid'"
                   th:attr="aria-describedby=${#fields.hasErrors('originalSourceUrl')} ? 'sourceUrl-help sourceUrl-error' : 'sourceUrl-help'">
            <div class="form-text" id="sourceUrl-help"
                 th:text="#{offers.field.originalSourceUrl.help}">Debe empezar por http:// o https://.</div>
            <div class="invalid-feedback" id="sourceUrl-error"
                 th:if="${#fields.hasErrors('originalSourceUrl')}" th:errors="*{originalSourceUrl}">Revisa la URL.</div>
          </div>

          <div class="mb-3">
            <label class="form-label" for="sourceName" th:text="#{offers.field.sourceName}">Fuente</label>
            <input class="form-control" id="sourceName" name="sourceName" type="text"
                   maxlength="60" th:value="${form.sourceName}"
                   th:classappend="${#fields.hasErrors('sourceName')} ? 'is-invalid'"
                   th:attr="aria-describedby='sourceName-help'">
            <div class="form-text" id="sourceName-help"
                 th:text="#{offers.field.sourceName.help}">Portal o bolsa de empleo de dónde salió.</div>
          </div>

          <div class="mb-3">
            <label class="form-label" for="externalReference"
                   th:text="#{offers.field.externalReference}">Referencia externa</label>
            <input class="form-control" id="externalReference" name="externalReference" type="text"
                   maxlength="120" th:value="${form.externalReference}"
                   th:classappend="${#fields.hasErrors('externalReference')} ? 'is-invalid'"
                   th:attr="aria-describedby='externalReference-help'">
            <div class="form-text" id="externalReference-help"
                 th:text="#{offers.field.externalReference.help}">Identificador de la oferta en esa fuente.</div>
          </div>

          <div class="mb-0">
            <label class="form-label" for="extractedAt">
              <span th:text="#{offers.field.extractedAt}">Fecha de extracción</span>
              <span class="req" aria-hidden="true">*</span>
            </label>
            <input class="form-control" id="extractedAt" name="extractedAt"
                   type="datetime-local" step="60" required th:value="${form.extractedAt}"
                   th:classappend="${#fields.hasErrors('extractedAt')} ? 'is-invalid'"
                   th:attr="aria-describedby=${#fields.hasErrors('extractedAt')} ? 'extractedAt-help extractedAt-error' : 'extractedAt-help'">
            <div class="form-text" id="extractedAt-help"
                 th:text="#{offers.field.extractedAt.help}">Cuándo se copió de la fuente.</div>
            <div class="invalid-feedback" id="extractedAt-error"
                 th:if="${#fields.hasErrors('extractedAt')}" th:errors="*{extractedAt}">Indica la fecha.</div>
          </div>

          <p class="form-text mt-3 mb-0" th:text="#{offers.warn.noSource}">Sin fuente y referencia no se puede deduplicar.</p>
        </fieldset>

      </div>
    </div>

    <div class="form-actions">
      <button class="btn btn-primary" type="submit" th:text="#{offers.save}">Guardar oferta</button>
      <a class="btn btn-quiet" th:href="@{/offers}" th:text="#{offers.cancel}">Cancelar</a>
    </div>
  </form>

</main>
</body>
</html>
```

**Reglas:**

- `novalidate` desactiva la validación del navegador para que **mande el servidor**; la
  validación HTML (`required`, `type="url"`, `maxlength`) sigue siendo una ayuda de UX.
- `name` + `th:value` (no `th:field`) porque `th:field` no lee bien los componentes de un
  `record`. `th:errors="*{...}"` si funciona: lee el `BindingResult`.
- En `<textarea>` el valor va como **texto** (`th:text`), no como `th:value`.
- Cada error tiene un `id` y el campo lo referencia con `aria-describedby`: el error no se
  comunica solo por color.
- El aviso de fuente/referencia es informativo, no un error: no bloquea el guardado.

### 13.5 `detail.html`

La ficha: sello grande arriba a la derecha, metadatos en rejilla y texto largo a 68ch.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout :: layout(~{::title}, ~{::main})}">
<head>
  <title th:text="${offer.title} + ' - SIGO'">Oferta - SIGO</title>
</head>
<body>
<main id="contenido" class="app-main container">

  <div class="alert alert-success d-flex align-items-center gap-2" role="status"
       th:if="${success}">
    <i class="bi bi-check2-circle" aria-hidden="true"></i>
    <span th:text="#{offers.created}">Oferta registrada correctamente.</span>
  </div>

  <p class="mb-3">
    <a class="link-quiet text-decoration-none" th:href="@{/offers/registered}">
      <i class="bi bi-arrow-left" aria-hidden="true"></i>
      <span th:text="#{offers.back}">Volver al listado</span>
    </a>
  </p>

  <article class="dossier">
    <header class="dossier__head">
      <div>
        <p class="mono text-body-secondary mb-1"
           th:text="${offer.externalReference != null
                       ? offer.externalReference
                       : #strings.substring(offer.id.toString(), 0, 8)}">REF</p>
        <h1 class="mb-1" th:text="${offer.title}">Analista de datos</h1>
        <p class="mb-0 text-body-secondary" th:text="${offer.company}">ACME</p>
      </div>
      <span class="seal seal--lg"
            th:classappend="'seal--' + ${#strings.toLowerCase(offer.status.name())}"
            th:text="${offer.statusLabel}">Registrada</span>
    </header>

    <dl class="dossier__meta">
      <div>
        <dt th:text="#{offers.field.status}">Estado</dt>
        <dd th:text="${offer.statusLabel}">Registrada</dd>
      </div>
      <div>
        <dt th:text="#{offers.field.location}">Ubicación</dt>
        <dd th:text="${offer.location} ?: #{offers.notAvailable}">Sin dato</dd>
      </div>
      <div>
        <dt th:text="#{offers.field.salary}">Salario</dt>
        <dd th:text="${offer.salary} ?: #{offers.notAvailable}">Sin dato</dd>
      </div>
      <div>
        <dt th:text="#{offers.field.employmentType}">Tipo de contratación</dt>
        <dd th:text="${offer.employmentType} ?: #{offers.notAvailable}">Sin dato</dd>
      </div>
      <div>
        <dt th:text="#{offers.field.extractedAt}">Fecha de extracción</dt>
        <dd class="mono" th:text="${offer.extractedAt}">2026-01-15 10:00 UTC</dd>
      </div>
      <div>
        <dt th:text="#{offers.field.createdAt}">Creada</dt>
        <dd class="mono" th:text="${offer.createdAt}">2026-01-15 10:00 UTC</dd>
      </div>
      <div>
        <dt th:text="#{offers.field.updatedAt}">Actualizada</dt>
        <dd class="mono" th:text="${offer.updatedAt}">2026-01-15 10:00 UTC</dd>
      </div>
    </dl>

    <section class="dossier__section">
      <h2 th:text="#{offers.field.description}">Descripción</h2>
      <p class="dossier__text" th:text="${offer.description}">Texto de la oferta.</p>
    </section>

    <section class="dossier__section">
      <h2 th:text="#{offers.field.requirements}">Requisitos</h2>
      <p class="dossier__text" th:text="${offer.requirements}">Requisitos de la oferta.</p>
    </section>

    <section class="dossier__section">
      <h2 th:text="#{offers.form.section.source}">Procedencia</h2>
      <dl class="dossier__meta">
        <div>
          <dt th:text="#{offers.field.sourceName}">Fuente</dt>
          <dd th:text="${offer.sourceName} ?: #{offers.notAvailable}">Sin dato</dd>
        </div>
        <div>
          <dt th:text="#{offers.field.externalReference}">Referencia externa</dt>
          <dd class="mono" th:text="${offer.externalReference} ?: #{offers.notAvailable}">Sin dato</dd>
        </div>
        <div th:if="${offer.publicationUrl != null}">
          <dt th:text="#{offers.field.publicationUrl}">URL de publicación</dt>
          <dd>
            <a th:href="${offer.publicationUrl}" target="_blank" rel="noopener noreferrer"
               th:text="${offer.publicationUrl}">https://</a>
          </dd>
        </div>
        <div>
          <dt th:text="#{offers.field.originalSourceUrl}">URL de origen</dt>
          <dd>
            <a th:href="${offer.originalSourceUrl}" target="_blank" rel="noopener noreferrer"
               th:text="${offer.originalSourceUrl}">https://</a>
          </dd>
        </div>
      </dl>
    </section>
  </article>

</main>
</body>
</html>
```

**Reglas:**

- `white-space: pre-line` (en `.dossier__text`) conserva los saltos de línea **sin**
  `th:utext`. Es la alternativa segura a renderizar HTML de terceros.
- Los enlaces externos llevan `rel="noopener noreferrer"` y `target="_blank"`.
- `th:href` **no** sanea `javascript:`; la defensa real es validar el esquema en servidor
  (Paso 14.6). No muestres URLs sin esa validación.

### 13.6 `not-found.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout :: layout(~{::title}, ~{::main})}">
<head><title th:text="#{offers.notFound.title} + ' - SIGO'">Oferta no encontrada - SIGO</title></head>
<body>
<main id="contenido" class="app-main container">
  <div class="blank">
    <p class="blank__mark" aria-hidden="true">404</p>
    <h1 class="h3" th:text="#{offers.notFound.title}">Esta oferta no está en el registro</h1>
    <p class="text-body-secondary" th:text="#{offers.notFound}">La oferta solicitada no existe.</p>
    <a class="btn btn-primary" th:href="@{/offers}" th:text="#{offers.back}">Volver al listado</a>
  </div>
</main>
</body>
</html>
```

### 13.7 Ajuste en `login.html`

Un solo cambio obligatorio: el `<main>` necesita el `id` del enlace de salto. Quita
`bg-body-tertiary` para que se vea el papel del tema.

```html
<main id="contenido" class="min-vh-100 d-flex align-items-center justify-content-center">
```

La regla `.login-card` del tema ya le pone el filete petróleo superior a la tarjeta. No
cambies el `th:action` ni el `name` de los campos: Spring Security los necesita tal cual.

### 13.8 Claves nuevas en `messages_es.properties`

Añade este bloque al final (el archivo usa escapes `\uXXXX`; `MessageSource` está en UTF-8,
así que también valdría UTF-8 real):

```properties
# NAVEGACION
nav.skip=Saltar al contenido
nav.dashboard=Panel
nav.offers=Ofertas
nav.new=Registrar
nav.logout=Salir

# OFERTAS - LISTADO
offers.total.one=1 oferta en el registro
offers.total.other={0} ofertas en el registro
offers.filter.registered=Solo registradas
offers.filter.all=Ver todas
offers.col.offer=Oferta
offers.col.company=Empresa
offers.col.status=Estado
offers.col.extracted=Extra\u00EDda
offers.page.status=P\u00E1gina {0} de {1}

# OFERTAS - FORMULARIO
offers.form.hint=Los campos con * son obligatorios.
offers.form.errors=Revisa la oferta antes de guardarla.
offers.form.section.job=Puesto
offers.form.section.conditions=Condiciones
offers.form.section.source=Procedencia
offers.field.description.help=Qu\u00E9 se ofrece y a qui\u00E9n.
offers.field.requirements.help=Formaci\u00F3n, experiencia y habilidades.
offers.field.originalSourceUrl.help=Debe empezar por http:// o https://.
offers.field.sourceName.help=Portal o bolsa de empleo de d\u00F3nde sali\u00F3.
offers.field.externalReference.help=Identificador de la oferta en esa fuente.
offers.field.extractedAt.help=Cu\u00E1ndo se copi\u00F3 de la fuente.

# OFERTAS - DETALLE Y ERRORES
offers.notFound.title=Esta oferta no est\u00E1 en el registro
offers.notAvailable=Sin dato
offers.validation.url=La URL de origen debe empezar por http:// o https://.
```

> Si ves `NoSuchMessageException`, falta la clave o está mal escrita.
>
> **No uses `{0, plural, ...}`.** `java.text.MessageFormat` (el que usa `MessageSource`)
> **no** soporta `plural`: falla con `unknown format type: plural`. El plural se resuelve
> en la plantilla con dos claves (`offers.total.one` / `offers.total.other`) y un ternario
> sobre `${totalElements == 1}`.

### 13.9 Checklist responsive y de accesibilidad

Prueba con las DevTools en **320, 375, 768, 1024 y 1440 px**:

- [ ] No hay scroll horizontal: en consola,
      `document.documentElement.scrollWidth <= window.innerWidth` es `true`.
- [ ] La barra colapsa por debajo de `992px`; el botón abre el menú y se cierra con
      teclado (Enter/Espacio).
- [ ] En móvil, cada fila del registro muestra título, empresa, sello y fecha; el área
      táctil de la fila es de al menos 44 px de alto.
- [ ] En escritorio, la cabecera del registro (`Oferta / Empresa / Estado / Extraída`) está
      alineada con las filas.
- [ ] El formulario es de una columna por debajo de `992px` y de dos columnas por encima.
- [ ] El detalle reordena los metadatos sin cortar palabras largas ni URLs.
- [ ] Todo se puede recorrer con `Tab`; el foco es visible (anillo ámbar) y el orden es
      lógico.
- [ ] El enlace "Saltar al contenido" aparece al primer `Tab` y lleva a `#contenido`.
- [ ] Los errores tienen texto además de color, y `aria-describedby` apunta a su `id`.
- [ ] Zoom al 200 %: no se pierde contenido ni aparecen solapamientos.
- [ ] Con `prefers-reduced-motion: reduce`, no hay transiciones ni sello rotado.
- [ ] El estado nunca se comunica solo por color: siempre hay texto en el sello.

**Cómo verificar:** con la app arrancada, `/offers`, `/offers/new` y `/offers/{id}` se ven
en español, sin errores de plantilla y sin scroll horizontal en 320 px.

---

## Paso 14 — Seguridad

**Objetivo:** que solo quien debe pueda registrar y ver ofertas, que el formulario no se
pueda usar como vector de XSS/CSRF y que los errores no filtren información.

**Dónde:** `infrastructure/security/SecurityConfig.java` (modificar),
`adapters/web/dto/JobOfferForm.java` (validación) y las plantillas (Paso 13).

### 14.1 Matriz de autorización de T3

| Ruta | Método | Quién | Ticket |
|---|---|---|---|
| `/css/**`, `/webjars/**`, `/login`, `/error` | GET | anónimo | T1 |
| `/dashboard` | GET | autenticado | T1 |
| `/offers`, `/offers/registered`, `/offers/{id}` | GET | `REGISTRADOR`, `ADMIN` | T3 |
| `/offers/new`, `POST /offers` | GET/POST | `REGISTRADOR`, `ADMIN` | T3 |
| `/offers/*/edit`, `/offers/*/approve`, `/offers/*/reject` | POST | `REGISTRADOR` | T4/T5 |
| `/api/offers/*/acquire`, `/offers/*/cancel`, `/offers/*/publish` | POST | `PUBLICADOR` | T6/T7 |
| `/reports/**` | GET | `ADMIN`, `REGISTRADOR` | T11 |
| `/audit/**` | GET | `ADMIN` | T11 |

> **El orden de los `requestMatchers` es la regla.** Spring evalúa de arriba abajo y se
> queda con la primera coincidencia. Las rutas concretas de `PUBLICADOR` van **antes** de
> `/offers/**`; si no, `/offers/**` las tapa y el publicador nunca podrá publicar.
>
> **Trampa concreta de T3:** `/offers/new` tiene su propio matcher y **gana** sobre
> `/offers/**`. Si lo dejas solo con `hasRole("REGISTRADOR")`, un `ADMIN` verá el botón
> "Registrar oferta" (la plantilla usa `hasAnyRole`) y recibirá `403`. Por eso el matcher de
> alta es `hasAnyRole("REGISTRADOR","ADMIN")`.

### 14.2 `SecurityConfig` de T3

La cadena completa (la línea de `/offers/**` ya está en el archivo actual; el resto se
mantiene):

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SigAuthenticationFailureHandler failureHandler)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // 1. Recursos publicos
                .requestMatchers("/css/**", "/webjars/**", "/login", "/error").permitAll()
                // 2. Rutas de administracion
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 3. Alta: REGISTRADOR y ADMIN (T3)
                .requestMatchers("/offers/new").hasAnyRole("REGISTRADOR", "ADMIN")
                // 4. Curacion y decisiones: REGISTRADOR (T4/T5)
                .requestMatchers("/offers/*/edit", "/offers/*/approve",
                                 "/offers/*/reject")
                .hasRole("REGISTRADOR")
                // 5. Rutas concretas de PUBLICADOR (T6/T7): ANTES de /offers/**
                .requestMatchers("/api/offers/*/acquire",
                                 "/offers/*/cancel", "/offers/*/publish")
                .hasRole("PUBLICADOR")
                // 6. Resto de ofertas (T3)
                .requestMatchers("/offers/**").hasAnyRole("REGISTRADOR", "ADMIN")
                // 7. Reportes y auditoria
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
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

**Lo que NO hay que tocar:**

- **No desactives CSRF.** `th:action` inyecta el token automáticamente en cada formulario.
- **No cambies el logout a GET.** El logout por POST con token evita CSRF de cierre de
  sesión; `th:action="@{/logout}" method="post"` ya lo hace bien.
- **No añadas `permitAll()` a `/offers/**`** "para probar". Si necesitas probar, usa el
  usuario `registrador` del perfil `dev`.
- Los headers por defecto de Spring Security ya incluyen `X-Content-Type-Options:
  nosniff`, `X-Frame-Options: DENY` y una politica de cache. No los desactives.

### 14.3 CSRF: qué se rompe y cómo se arregla

- Todo `POST` necesita el campo `_csrf`. Con `th:action` lo pone Thymeleaf; con un
  `<form action="/offers">` a mano **no**.
- En MockMvc, todo `POST` necesita `.with(csrf())`; sin el, el resultado esperado es `403`.
- El logout es `POST`; un enlace `<a href="/logout">` daría `405`/`403`.
- Si añades un `fetch` en el futuro, manda el token en la cabecera `X-CSRF-TOKEN`.

### 14.4 XSS: la regla de una sola línea

- Usa **siempre** `th:text`. **Nunca** `th:utext` con datos de la oferta.
- No construyas HTML en Java (`"<b>" + title`) ni lo devuelvas como `@ResponseBody`.
- Para conservar saltos de línea, `white-space: pre-line` en CSS (Paso 13.5), no `utext`.
- Los datos de terceros (`title`, `company`, `description`, `requirements`) van a texto o a
  atributos controlados; nunca a `onclick`, `style` ni `href` sin validar.
- `th:href="${offer.originalSourceUrl}"` es seguro **solo si** el esquema está validado en
  servidor (14.6): `javascript:` en un `href` se ejecuta al hacer clic.

### 14.5 IDOR y autorización por objeto

- En T3 **todos** los `REGISTRADOR`/`ADMIN` ven el registro completo: es el modelo del
  spec (una mesa de curaduría compartida), no un fallo. No inventes propietario por oferta.
- `GET /offers/{id}` no filtra por usuario; filtra por **rol** en `SecurityConfig`.
- Si la oferta no existe, responde `404` con un mensaje genérico
  (`@ExceptionHandler(JobNotFoundException.class)`), no `403` ni el ID en el texto: no
  confirmes si un UUID existe.
- Los IDs son UUIDv4, no secuenciales: no se enumeran.
- El filtrado por `assignedUserId` llega con la adquisición (T6/T7); entonces la regla será
  "solo el asignado o ADMIN", y se implementa en el caso de uso, no en la plantilla.

### 14.6 Validación de la URL de origen (defensa real)

`@URL` de Hibernate Validator comprueba sintaxis, pero **acepta `ftp://` y `file://`**. Un
`href` a `file://` o un esquema raro no es lo que quieres en una app LAN. Añade una segunda
regla de esquema en `JobOfferForm`:

```java
@NotBlank
@Size(max = 500)
@URL
@Pattern(regexp = "^\\s*https?://\\S+\\s*$",
         message = "{offers.validation.url}")
String originalSourceUrl,
```

**Reglas:**

- La validación corre en el **servidor** antes del caso de uso; el `type="url"` del
  navegador es solo ayuda.
- El mensaje `{offers.validation.url}` vive en `messages_es.properties` (Paso 13.8).
- Si añades `publicationUrl` en T7, aplica la **misma** validación.
- No normalices la URL en el controlador antes de validar: valida lo que llega y recorta
  después en el mapper (`trimToNull`/`trim`).

### 14.7 Auditoría: límites de T3

- La auditoría `CREATE` va **después** del `save` y en la **misma transacción**: si falla el
  `INSERT` en `audit_event`, se revierte la oferta. Es correcto.
- En T3 **no** se auditan los intentos fallidos (validación, duplicado, `403`). Eso llega
  con el `@EventListener` de autenticación/errores (T16). No lo simules con `try/catch` en
  el controlador.
- Nunca audites contraseñas, tokens CSRF ni el cuerpo completo del formulario. El snapshot
  de T3 (`title`, `company`, `status`, `sourceName`, `externalReference`) es suficiente.
- `ip_address` sale de `request.getRemoteAddr()`. Detrás de un proxy inverso eso da la IP
  del proxy: configura `server.forward-headers-strategy=framework` en el perfil `lan`
  (deuda anotada para T16), no confies en `X-Forwarded-For` a mano.

### 14.8 Defensa en profundidad en el controlador

- Añade `@PreAuthorize("hasAnyRole('REGISTRADOR','ADMIN')")` a la clase
  `OffersController`. `@EnableMethodSecurity` ya está activo: si mañana alguien relaja un
  matcher, el método sigue protegido.
- `@AuthenticationPrincipal SigUser user` puede ser `null` si el principal no es `SigUser`
  (por ejemplo, en un test con `@WithMockUser`). En producción el login siempre lo crea,
  pero un guard barato evita un `500`:

```java
if (user == null) {
    return "redirect:/login";
}
```

- `BindingResult` va **inmediatamente después** de `@Valid`; si no, Spring lanza
  `IllegalStateException`.
- El duplicado se traduce a **error global** (`binding.reject(...)`), no a un campo suelto:
  afecta a la pareja fuente + referencia.
- No devuelvas `ex.getMessage()` de excepciones técnicas al usuario. `JobNotFoundException`
  ya devuelve un mensaje genérico en la vista 404.

### 14.9 Checklist de seguridad

- [ ] `registrador` entra en `/offers`, `/offers/new` y `/offers/{id}`.
- [ ] `publicador` recibe `403` en `/offers/**` (esperado en T3).
- [ ] `admin` entra en `/offers` y en `/offers/new` (ambos matchers).
- [ ] Anónimo en `/offers` redirige a `/login`.
- [ ] `POST /offers` sin token CSRF devuelve `403`.
- [ ] `POST /offers` con `originalSourceUrl=ftp://x` o `javascript:alert(1)` vuelve al
      formulario con el error de esquema, sin crear nada.
- [ ] `GET /offers/{uuid-inexistente}` devuelve `404` con mensaje genérico.
- [ ] El HTML generado no contiene `th:utext` (busca en `templates/`).
- [ ] Los enlaces externos llevan `rel="noopener noreferrer"`.
- [ ] `audit_event` registra `CREATE` con `success=true` y sin datos sensibles.
- [ ] `curl -I` (o DevTools) muestra `X-Content-Type-Options: nosniff` y
      `X-Frame-Options: DENY`.

**Cómo verificar:** estas comprobaciones se automatizan en `SecurityRulesTest` (Paso 15.4)
y se repiten a mano en el Paso 16.

---

## Paso 15 — Tests

**Objetivo:** probar la orquestación, el mapeo, la web y las reglas de acceso **sin base de
datos**. La persistencia real (`JobOfferPersistenceAdapter`, `JpaAuditAdapter`, `@DataJpaTest`)
se difiere a T9: el plan §4.12 pide `EmbeddedPostgresSupport` con Zonky `embedded-postgres`
y esa infraestructura aún no existe. Un `@DataJpaTest` hoy falla sin PostgreSQL local.

### 15.1 `RegisterJobOfferServiceTest` (Mockito)

Casos:

| Caso | Resultado esperado |
|---|---|
| registro válido con fuente + referencia | `REGISTERED`; `save`; auditoría `CREATE` con snapshot |
| duplicado (fuente + referencia existen) | `DuplicateJobOfferException`; **no** `save`; **no** auditoría |
| sin `sourceName` | no llama a `existsBySourceReference`; guarda |
| `externalReference` en blanco | idem: no deduplica |
| `createdAt`/`updatedAt` | iguales al instante del `Clock` inyectado |
| `data` o `actor` nulos | `NullPointerException` |

```java
package com.evela.sigotrab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.evela.sigotrab.application.port.out.AuditPort;
import com.evela.sigotrab.application.port.out.JobOfferRepository;
import com.evela.sigotrab.domain.exception.DuplicateJobOfferException;
import com.evela.sigotrab.domain.model.ActorInfo;
import com.evela.sigotrab.domain.model.AuditAction;
import com.evela.sigotrab.domain.model.AuditRecord;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;
import com.evela.sigotrab.domain.model.NewJobOffer;

@ExtendWith(MockitoExtension.class)
class RegisterJobOfferServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final ActorInfo ACTOR =
            new ActorInfo(UUID.randomUUID(), "registrador", "127.0.0.1");

    @Mock JobOfferRepository offers;
    @Mock AuditPort audit;

    private RegisterJobOfferService service;

    @BeforeEach
    void setUp() {
        service = new RegisterJobOfferService(offers, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static NewJobOffer data(String sourceName, String externalReference) {
        return new NewJobOffer(
                "Analista de datos", "ACME", "Descripcion", "Requisitos",
                "Remoto", "40.000", "Jornada completa",
                "https://example.com/1", sourceName, externalReference, NOW);
    }

    @Test
    void registersOfferAsRegisteredAndAuditsCreate() {
        when(offers.existsBySourceReference("portal", "ext-1")).thenReturn(false);

        JobOffer offer = service.register(data("portal", "ext-1"), ACTOR);

        assertThat(offer.getStatus()).isEqualTo(JobStatus.REGISTERED);
        assertThat(offer.getCreatedAt()).isEqualTo(NOW);
        assertThat(offer.getCreatedBy()).isEqualTo(ACTOR.userId());
        verify(offers).save(offer);

        var captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit).record(captor.capture());
        AuditRecord event = captor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.CREATE);
        assertThat(event.entityType()).isEqualTo("JOB_OFFER");
        assertThat(event.entityId()).isEqualTo(offer.getId().toString());
        assertThat(event.actor()).isEqualTo(ACTOR);
        assertThat(event.success()).isTrue();
        assertThat(event.newValues())
                .containsEntry("status", "REGISTERED")
                .containsEntry("sourceName", "portal")
                .containsEntry("externalReference", "ext-1");
    }

    @Test
    void rejectsDuplicateWhenSourceAndReferenceMatch() {
        when(offers.existsBySourceReference("portal", "ext-1")).thenReturn(true);

        assertThatThrownBy(() -> service.register(data("portal", "ext-1"), ACTOR))
                .isInstanceOf(DuplicateJobOfferException.class)
                .hasMessageContaining("portal")
                .hasMessageContaining("ext-1");

        verify(offers, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void skipsDuplicateCheckWhenSourceNameIsMissing() {
        JobOffer offer = service.register(data(null, "ext-1"), ACTOR);

        assertThat(offer.getStatus()).isEqualTo(JobStatus.REGISTERED);
        verify(offers, never()).existsBySourceReference(any(), any());
        verify(offers).save(offer);
        verify(audit).record(any());
    }

    @Test
    void skipsDuplicateCheckWhenExternalReferenceIsBlank() {
        JobOffer offer = service.register(data("portal", "   "), ACTOR);

        verify(offers, never()).existsBySourceReference(any(), any());
        verify(offers).save(offer);
    }

    @Test
    void rejectsNullData() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.register(null, ACTOR))
                .withMessage("data");
    }

    @Test
    void rejectsNullActor() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.register(data("portal", "ext-1"), null))
                .withMessage("actor");
    }
}
```

**Reglas:**

- `Clock.fixed(...)` hace deterministas `createdAt`/`updatedAt`; nunca uses
  `Instant.now()` en el test.
- No stubees lo que el test no usa: Mockito es estricto
  (`UnnecessaryStubbingException`).
- `verifyNoInteractions(audit)` es la prueba de que un duplicado **no** deja rastro de
  auditoría (en T3 no se auditan fallos).

### 15.2 `JobOfferEntityMapperTest`

Prueba de traducción pura: detecta un `@Column` mal puesto **antes** de arrancar la app.

```java
package com.evela.sigotrab.adapters.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.evela.sigotrab.adapters.persistence.entity.JobOfferJpaEntity;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;
import com.evela.sigotrab.domain.model.NewJobOffer;

class JobOfferEntityMapperTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private final JobOfferEntityMapper mapper = new JobOfferEntityMapper();

    @Test
    void toDomainMapsEveryColumn() {
        var e = new JobOfferJpaEntity();
        e.setId(UUID.randomUUID());
        e.setTitle("Analista de datos");
        e.setCompany("ACME");
        e.setDescription("Descripcion");
        e.setRequirements("Requisitos");
        e.setLocation("Remoto");
        e.setSalary("40.000");
        e.setEmploymentType("Jornada completa");
        e.setOriginalSourceUrl("https://example.com/1");
        e.setSourceName("portal");
        e.setExternalReference("ext-1");
        e.setExtractedAt(NOW);
        e.setStatus(JobStatus.REGISTERED);
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);

        JobOffer d = mapper.toDomain(e);

        assertThat(d.getId()).isEqualTo(e.getId());
        assertThat(d.getTitle()).isEqualTo("Analista de datos");
        assertThat(d.getOriginalSourceUrl()).isEqualTo("https://example.com/1");
        assertThat(d.getEmploymentType()).isEqualTo("Jornada completa");
        assertThat(d.getSourceName()).isEqualTo("portal");
        assertThat(d.getExternalReference()).isEqualTo("ext-1");
        assertThat(d.getStatus()).isEqualTo(JobStatus.REGISTERED);
        assertThat(d.getExtractedAt()).isEqualTo(NOW);
        assertThat(d.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void toEntityKeepsIdAndDoesNotCopyVersion() {
        UUID actor = UUID.randomUUID();
        JobOffer d = JobOffer.register(
                new NewJobOffer("Analista", "ACME", "D", "R", null, null, null,
                        "https://example.com/1", "portal", "ext-1", NOW),
                actor, NOW);

        JobOfferJpaEntity e = mapper.toEntity(d);

        assertThat(e.getId()).isEqualTo(d.getId());
        assertThat(e.getOriginalSourceUrl()).isEqualTo("https://example.com/1");
        assertThat(e.getStatus()).isEqualTo(JobStatus.REGISTERED);
        assertThat(e.getCreatedBy()).isEqualTo(actor);
        assertThat(e.getVersion()).isNull();   // null => Hibernate hace persist
    }

    @Test
    void toDomainToleratesNullOptionalFields() {
        var e = new JobOfferJpaEntity();
        e.setId(UUID.randomUUID());
        e.setTitle("T");
        e.setCompany("C");
        e.setDescription("D");
        e.setRequirements("R");
        e.setOriginalSourceUrl("https://example.com/1");
        e.setExtractedAt(NOW);
        e.setStatus(JobStatus.REGISTERED);
        e.setCreatedAt(NOW);
        e.setUpdatedAt(NOW);

        JobOffer d = mapper.toDomain(e);

        assertThat(d.getLocation()).isNull();
        assertThat(d.getSalary()).isNull();
        assertThat(d.getSourceName()).isNull();
        assertThat(d.getExternalReference()).isNull();
    }
}
```

### 15.3 `OffersControllerTest` (`@WebMvcTest`)

Cubre render, PRG, errores de campo, error global de duplicado y `404`.

```java
package com.evela.sigotrab.adapters.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import com.evela.sigotrab.adapters.web.mapper.JobOfferWebMapper;
import com.evela.sigotrab.application.port.in.GetJobOffersUseCase;
import com.evela.sigotrab.application.port.in.RegisterJobOfferUseCase;
import com.evela.sigotrab.application.port.out.PageResult;
import com.evela.sigotrab.domain.exception.DuplicateJobOfferException;
import com.evela.sigotrab.domain.exception.JobNotFoundException;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobStatus;
import com.evela.sigotrab.domain.model.NewJobOffer;
import com.evela.sigotrab.infrastructure.config.MessageConfig;
import com.evela.sigotrab.infrastructure.security.SecurityConfig;
import com.evela.sigotrab.infrastructure.security.SigAuthenticationFailureHandler;
import com.evela.sigotrab.infrastructure.security.SigUser;

@WebMvcTest(OffersController.class)
@Import({SecurityConfig.class, JobOfferWebMapper.class, MessageConfig.class})
class OffersControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired MockMvc mvc;

    @MockitoBean RegisterJobOfferUseCase registerJobOffer;
    @MockitoBean GetJobOffersUseCase getJobOffers;
    @MockitoBean SigAuthenticationFailureHandler failureHandler;

    private static SigUser registrador() {
        return new SigUser(UUID.randomUUID(), "registrador", "x", true,
                List.of(new SimpleGrantedAuthority("ROLE_REGISTRADOR")));
    }

    private static JobOffer offer() {
        return JobOffer.register(
                new NewJobOffer("Analista", "ACME", "D", "R", null, null, null,
                        "https://example.com/1", "portal", "ext-1", NOW),
                UUID.randomUUID(), NOW);
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void newFormRenders() throws Exception {
        mvc.perform(get("/offers/new"))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/form"))
           .andExpect(model().attributeExists("form"));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void listRendersOffersAndCount() throws Exception {
        when(getJobOffers.list(isNull(), any()))
                .thenReturn(new PageResult<>(List.of(offer()), 1, 0, 20));

        mvc.perform(get("/offers"))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/list"))
           .andExpect(model().attribute("totalElements", 1L))
           .andExpect(model().attribute("registered", false));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void registeredFiltersByRegisteredStatus() throws Exception {
        when(getJobOffers.list(eq(JobStatus.REGISTERED), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 0, 20));

        mvc.perform(get("/offers/registered"))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/list"))
           .andExpect(model().attribute("registered", true));

        verify(getJobOffers).list(eq(JobStatus.REGISTERED), any());
    }

    @Test
    void createRedirectsToDetailOnSuccess() throws Exception {
        SigUser user = registrador();
        JobOffer created = offer();
        when(registerJobOffer.register(any(), any())).thenReturn(created);

        mvc.perform(post("/offers")
                .with(user(user))
                .with(csrf())
                .param("title", "Analista")
                .param("company", "ACME")
                .param("description", "D")
                .param("requirements", "R")
                .param("originalSourceUrl", "https://example.com/1")
                .param("extractedAt", "2026-01-15T10:00"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/offers/" + created.getId()))
           .andExpect(flash().attribute("success", true));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void createShowsFieldErrorsWhenRequiredDataIsMissing() throws Exception {
        mvc.perform(post("/offers").with(csrf()))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/form"))
           .andExpect(model().attributeHasFieldErrors("form",
                   "title", "company", "description", "requirements",
                   "originalSourceUrl", "extractedAt"));

        verifyNoInteractions(registerJobOffer);
    }

    @Test
    void createShowsGlobalErrorWhenDuplicate() throws Exception {
        when(registerJobOffer.register(any(), any()))
                .thenThrow(new DuplicateJobOfferException("portal", "ext-1"));

        mvc.perform(post("/offers")
                .with(user(registrador()))
                .with(csrf())
                .param("title", "Analista")
                .param("company", "ACME")
                .param("description", "D")
                .param("requirements", "R")
                .param("originalSourceUrl", "https://example.com/1")
                .param("sourceName", "portal")
                .param("externalReference", "ext-1")
                .param("extractedAt", "2026-01-15T10:00"))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/form"))
           .andExpect(model().attributeHasErrors("form"))
           .andExpect(result -> {
               var binding = (BindingResult) result.getModelAndView().getModel()
                       .get(BindingResult.MODEL_KEY_PREFIX + "form");
               assertThat(binding.getGlobalErrors())
                       .extracting(ObjectError::getDefaultMessage)
                       .anyMatch(m -> m.contains("portal"));
           });
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void detailRendersOffer() throws Exception {
        JobOffer offer = offer();
        when(getJobOffers.getById(offer.getId())).thenReturn(offer);

        mvc.perform(get("/offers/{id}", offer.getId()))
           .andExpect(status().isOk())
           .andExpect(view().name("offers/detail"))
           .andExpect(model().attributeExists("offer"));
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void detailReturns404WhenOfferDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(getJobOffers.getById(id)).thenThrow(new JobNotFoundException(id));

        mvc.perform(get("/offers/{id}", id))
           .andExpect(status().isNotFound())
           .andExpect(view().name("offers/not-found"));
    }
}
```

**Reglas y trampas:**

- Boot 4: `@WebMvcTest` está en `org.springframework.boot.webmvc.test.autoconfigure`.
- Boot 4: `@MockBean` ya no existe; usa
  `org.springframework.test.context.bean.override.mockito.MockitoBean`.
- `@WithMockUser` crea un `User` de Spring, **no** un `SigUser`; para el `POST` usa
  `.with(user(sigUser))`.
- Si añades `@PreAuthorize` al controlador, el `SigUser` del test necesita la autoridad
  `ROLE_REGISTRADOR` (por eso el helper usa `SimpleGrantedAuthority`). `@WithMockUser` ya
  la aporta; `.with(user(sigUser))` no.
- Todo `POST` necesita `.with(csrf())`.
- `SecurityConfig` requiere `SigAuthenticationFailureHandler`: mockealo.
- `@WebMvcTest` **renderiza** la plantilla: un error de Thymeleaf aparece como `500` y el
  test falla. Es una ventaja: la plantilla también queda cubierta.
- `isNull()` (no `any()`) deja claro que la lista sin filtro pasa `null` como estado.
- `model().attribute("totalElements", 1L)` compara `Long`; no uses `1`.

### 15.4 `SecurityRulesTest` (matriz de acceso)

```java
package com.evela.sigotrab.adapters.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.evela.sigotrab.adapters.web.mapper.JobOfferWebMapper;
import com.evela.sigotrab.application.port.in.GetJobOffersUseCase;
import com.evela.sigotrab.application.port.in.RegisterJobOfferUseCase;
import com.evela.sigotrab.application.port.out.PageResult;
import com.evela.sigotrab.infrastructure.config.MessageConfig;
import com.evela.sigotrab.infrastructure.security.SecurityConfig;
import com.evela.sigotrab.infrastructure.security.SigAuthenticationFailureHandler;
import com.evela.sigotrab.infrastructure.security.SigUser;

@WebMvcTest(OffersController.class)
@Import({SecurityConfig.class, JobOfferWebMapper.class, MessageConfig.class})
class SecurityRulesTest {

    @Autowired MockMvc mvc;

    @MockitoBean RegisterJobOfferUseCase registerJobOffer;
    @MockitoBean GetJobOffersUseCase getJobOffers;
    @MockitoBean SigAuthenticationFailureHandler failureHandler;

    private void stubEmptyList() {
        when(getJobOffers.list(any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 0, 20));
    }

    private static SigUser registrador() {
        return new SigUser(UUID.randomUUID(), "registrador", "x", true,
                List.of(new SimpleGrantedAuthority("ROLE_REGISTRADOR")));
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/offers"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "PUBLICADOR")
    void publicadorCannotOpenTheRegistry() throws Exception {
        mvc.perform(get("/offers/new"))
           .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REGISTRADOR")
    void registradorCanOpenTheForm() throws Exception {
        mvc.perform(get("/offers/new"))
           .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanOpenTheForm() throws Exception {
        // /offers/new tiene matcher propio: si solo permite REGISTRADOR, este test falla.
        mvc.perform(get("/offers/new"))
           .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanOpenTheRegistry() throws Exception {
        stubEmptyList();

        mvc.perform(get("/offers"))
           .andExpect(status().isOk());
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/offers")
                .with(user(registrador()))
                .param("title", "T"))
           .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PUBLICADOR")
    void publicadorCannotPost() throws Exception {
        mvc.perform(post("/offers")
                .with(csrf())
                .param("title", "T"))
           .andExpect(status().isForbidden());
    }
}
```

**Reglas:**

- `redirectedUrl("/login")` porque `LoginUrlAuthenticationEntryPoint` emite una URL
  **relativa** (`/login`); `redirectedUrlPattern("**/login")` no casa con `/login`.
- `postWithoutCsrfIsForbidden` usa `.with(user(...))` y **no** `.with(csrf())`: lo que se
  prueba es el filtro CSRF, no la autorización.
- `publicadorCannotPost` si lleva `.with(csrf())`: así el `403` viene de la autorización y
  no del CSRF (si no, el test pasaría por el motivo equivocado).
- No uses `@WithMockUser` para el `POST` válido: el controlador necesita un `SigUser`.

### 15.5 ArchUnit

No hay que tocar `HexagonalArchitectureTest`. A partir de T3 la regla
`application_does_not_depend_on_adapters` empieza a comprobar clases de verdad: si un
servicio importa un repositorio JPA, el test falla. Lo mismo con `domain` y Spring.

### 15.6 Cómo verificar

```bash
cd sigotrab

# Solo T3 (sin base de datos)
./mvnw -B test -Dtest="JobOfferTest,JobStatusTransitionTest,HexagonalArchitectureTest,RegisterJobOfferServiceTest,JobOfferEntityMapperTest,OffersControllerTest,SecurityRulesTest"

# Suite completa: sigue necesitando PostgreSQL por SigotrabApplicationTests (deuda T9)
./mvnw -B test
```

**Criterio:** los siete test classes en verde y `HexagonalArchitectureTest` sin cambios.

---

## Paso 16 — Verificación manual (punta a punta)

**Objetivo:** comprobar la rebanada completa contra PostgreSQL real.

1. Levanta la BD (si no está) y la app:

```bash
cd sigotrab
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

2. Entra en `http://localhost:8080/login` como `registrador` / `registrador123`.
3. Ve a `/offers/new`, rellena los obligatorios y guarda.
4. Debes acabar en `/offers/{id}` con el mensaje "Oferta registrada correctamente".
5. `/offers/registered` debe listar la oferta con estado `Registrada`.
6. Repite con la misma `sourceName` + `externalReference`: debe mostrar el error de
   duplicado, sin crear otra fila.
7. Comprueba la base:

```bash
docker exec postgres psql -U sigotrabuser -d sigotrabdb -c \
  "select id, title, status, source_name, external_reference, created_at from job_offer order by created_at desc limit 5;"

docker exec postgres psql -U sigotrabuser -d sigotrabdb -c \
  "select action, entity_type, entity_id, username, success, new_values from audit_event order by id desc limit 5;"
```

**Resultado esperado:** una fila en `job_offer` con `status = 'REGISTERED'` y una fila en
`audit_event` con `action = 'CREATE'`, `success = true` y `new_values` con el snapshot.

> Si `docker compose` se queja del nombre de proyecto, ejecútalo desde `sigotrab/docker/`
> (donde está el `.env`).

### 16.1 Seguridad (manual)

8. Cierra sesión y entra como `publicador` / `publicador123`. `/offers` y `/offers/new`
   deben devolver **403** (en T3 el publicador todavía no participa).
9. Como `registrador`, envía una oferta con `originalSourceUrl=ftp://example.com/1`:
   debe volver al formulario con el error de esquema y **no** crear fila.
10. Repite el duplicado del paso 6: el mensaje debe aparecer como **error global** del
    formulario (arriba, no colgado de un campo), sin crear fila.
11. Abre `/offers/<uuid-aleatorio>`: debe dar **404** con el mensaje genérico, sin eco del
    ID.
12. Comprueba cabeceras:

```bash
curl -sI http://localhost:8080/login | grep -iE "x-content-type-options|x-frame-options"
```

Debe mostrar `nosniff` y `DENY`.

### 16.2 Responsive y accesibilidad (manual)

13. Con DevTools, recorre **320, 375, 768, 1024 y 1440 px** en `/offers`, `/offers/new` y
    el detalle. En cada ancho:
    - no hay scroll horizontal (`document.documentElement.scrollWidth <= innerWidth`);
    - la barra colapsa por debajo de `992px` y el botón abre el menú;
    - en móvil, la fila del registro muestra título, empresa, sello y fecha;
    - el formulario pasa de dos columnas a una.
14. Recorre todo con `Tab`: el foco es visible (anillo ámbar), el primer `Tab` muestra
    "Saltar al contenido" y los errores tienen texto además de color.

---

## Paso 17 — Cierre de T3

```bash
cd sigotrab

# 1) tests de T3
./mvnw -B test -Dtest="JobOfferTest,JobStatusTransitionTest,HexagonalArchitectureTest,RegisterJobOfferServiceTest,JobOfferEntityMapperTest,OffersControllerTest,SecurityRulesTest"

# 2) paquete sin depender de la BD
./mvnw -B -DskipTests package

# 3) dominio puro
grep -RInE "^import (org\.springframework|jakarta\.persistence|org\.hibernate|org\.thymeleaf|lombok)" \
  src/main/java/com/evela/sigotrab/domain || echo "OK: dominio puro"
```

Luego, desde la raíz:

```bash
git diff --check
git status --short
git add sigotrab/src/main/java/com/evela/sigotrab/domain \
        sigotrab/src/main/java/com/evela/sigotrab/application \
        sigotrab/src/main/java/com/evela/sigotrab/adapters \
        sigotrab/src/main/java/com/evela/sigotrab/infrastructure/audit \
        sigotrab/src/main/java/com/evela/sigotrab/infrastructure/security/SecurityConfig.java \
        sigotrab/src/main/resources/templates/offers \
        sigotrab/src/main/resources/templates/layout.html \
        sigotrab/src/main/resources/templates/login.html \
        sigotrab/src/main/resources/static/css/app.css \
        sigotrab/src/main/resources/messages/messages_es.properties \
        sigotrab/src/test/java/com/evela/sigotrab
git commit -m "feat: registro de ofertas con validación y auditoría (#T3)"
git status --short
```

**Criterio:** la definición de "T3 terminado" del final.

---

## Apéndice A — Errores comunes en T3

| Síntoma | Causa probable | Solución |
|---|---|---|
| `Schema-validation: missing column [original_source_url]` | Mapeaste el campo sin `@Column(name="source_url")` | Añade `@Column(name = "source_url")` |
| `wrong column type ... found [text], expecting [varchar(255)]` | `description`/`requirements` son `text` | `@Column(columnDefinition = "text")` |
| `Schema-validation: wrong column type ... timestamp` | Usaste `LocalDateTime` en la entidad | Usa `Instant` (`timestamptz`) |
| Hibernate hace `SELECT` antes del `INSERT` | `@GeneratedValue` + id ya asignado | Quita `@GeneratedValue` |
| `LazyInitializationException` | `@ManyToOne` perezoso con `open-in-view: false` | Mapea `assignedUserId`/`createdBy`/`updatedBy` como `UUID` |
| `audit_event` falla con *jsonb/inet* | Pasas `varchar` a `jsonb`/`inet` | `cast(? as jsonb)` / `cast(? as inet)` |
| `could not determine data type of parameter $N` | `ip_address` nulo con tipo desconocido | `PreparedStatementSetter` + `setNull(i, Types.VARCHAR)` |
| `NullPointerException` al auditar | `Map.of(...)` con un valor `null` | Usa `LinkedHashMap` |
| `IllegalStateException: BindingResult` | `BindingResult` no va justo tras `@Valid` | Reordena los parámetros |
| `Failed to convert ... extractedAt` | Bindea `Instant` desde `datetime-local` | Bindea `LocalDateTime` + `@DateTimeFormat` |
| `@AuthenticationPrincipal SigUser` es `null` | `@WithMockUser` crea otro `User` | `.with(user(sigUser))` en el test |
| `@MockBean` no compila | Boot 4 lo eliminó | `@MockitoBean` |
| `@WebMvcTest` no encontrado | Paquete de Boot 3 | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| `403` al hacer `POST /offers` | Falta regla de seguridad o CSRF | `/offers/**` → `REGISTRADOR`; usa `th:action` |
| `403` en el test | Falta `.with(csrf())` | Añádelo |
| El duplicado da `500` | Solo confías en el `UNIQUE` | Pre-chequeo + `catch (DuplicateJobOfferException)` |
| Se guardan ofertas duplicadas sin fuente | `existsBySourceReference` con `null` | Comprueba solo si ambos están presentes |
| `th:field` no lee el record | Limitación de lectura de records | Usa `name` + `th:value` |
| `#temporals` no existe | Extra no disponible | Pre-formatea en el mapper |
| ArchUnit falla `application` → `adapters` | El servicio importa el repositorio JPA | Inyecta el puerto `out` |
| `Sort.by("created_at")` falla | Es el nombre de columna | Usa la propiedad `"createdAt"` |
| El controlador pide `Clock` como parámetro | Spring MVC no inyecta beans arbitrarios | Inyéctalo en el constructor |
| `TemplateInputException: Error resolving template [offers/detail]` | El archivo se llama `details.html` y el controlador devuelve `offers/detail` | Renombra a `detail.html` **o** cambia el `return`; no dejes los dos nombres |
| `NotNull` no valida nada / import extraño | Importaste `groovyjarjarantlr4...NotNull` | Usa `jakarta.validation.constraints.NotNull` |
| Se guarda una URL `ftp://` o `javascript:` | `@URL` no restringe el esquema | Añade `@Pattern(regexp = "^\\s*https?://\\S+\\s*$")` |
| `NoSuchMessageException: offers.xxx` | Falta la clave o está mal escrita | Añádela a `messages_es.properties` (Paso 13.8) |
| `unknown format type: plural` | `java.text.MessageFormat` no soporta `plural` | Dos claves (`offers.total.one`/`.other`) y ternario en la plantilla |
| El listado se rompe en móvil | Usaste una `<table>` con muchas columnas | Usa la cuadrícula `.ledger` (Paso 13.3) |
| El detalle muestra el HTML de la descripción | Usaste `th:utext` | `th:text` + `white-space: pre-line` |
| `MockMvc` devuelve `500` y el log señala Thymeleaf | La plantilla no compila (clave, expresión o `th:replace`) | Corrige la plantilla: `@WebMvcTest` la renderiza |
| La barra no marca la sección activa | No añadiste `activeNav` al modelo | Es opcional; añade `model.addAttribute("activeNav", "offers")` |
| `403` como `publicador` en `/offers/**` | Comportamiento esperado en T3 | Se relaja en T6/T7, no lo "arregles" ahora |

---

## Apéndice B — Conceptos y por qué

### B.1 ¿Por qué `ActorInfo` y no `SigUser` en la aplicación?

Porque `SigUser` vive en `infrastructure/security` y depende de Spring Security. La
aplicación no debe conocer el framework. `ActorInfo` es un record del dominio con lo
justo: quién y desde dónde.

### B.2 ¿Por qué `reconstitute` y no setters?

El mapper necesita reconstruir un agregado que ya es válido. `reconstitute` es una puerta
explícita y única; los setters permitirían estados imposibles (`PUBLISHED` con lock, por
ejemplo). La regla del dominio sigue siendo que solo las operaciones de negocio cambian el
estado.

### B.3 ¿Por qué pre-chequeo de duplicado si ya hay `UNIQUE`?

El `UNIQUE` es la garantía real (y la única que sobrevive a una carrera). El pre-chequeo
sirve para dar un error de formulario legible en el caso normal, sin esperar a que la base
rechace el `INSERT`.

### B.4 ¿Por qué `AuditPort` y no un `@EventListener`?

Porque la auditoría es parte del caso de uso (misma transacción, mismo resultado) y debe
poder probarse con un mock. Un listener de Spring sería menos explícito y más difícil de
testear. El `@EventListener` llegará para login/logout (T5/T16), no para el `CREATE`.

### B.5 ¿Por qué `JdbcTemplate` para auditar?

Porque `old_values`/`new_values` son `jsonb` e `ip_address` es `inet`. Mapearlos con JPA
obliga a `@JdbcTypeCode`/dialectos y complica el `validate`. Un `INSERT` explícito con
casts es más simple, sigue participando en la transacción y no contamina el dominio.

### B.6 ¿Por qué `PageQuery`/`PageResult`?

Para que `application/` no dependa de `org.springframework.data.domain`. El adaptador
traduce; el caso de uso solo ve una página de dominio.

### B.7 ¿Qué añadirán T4/T5/T6?

| Ticket | Añade |
|---|---|
| T4 | `UpdateJobOfferUseCase` + `updateContent` en web; auditoría `UPDATE` con `old/new` |
| T5 | `Approve`/`Reject` + `StatusHistoryPort` + `/audit` |
| T6 | `UPDATE ... WHERE` atómico, `acquire` real, `409 JOB_ALREADY_LOCKED` |

---

## Definición de "T3 terminado"

**Dominio**

- [ ] `ActorInfo`, `AuditAction`, `AuditRecord` y `DuplicateJobOfferException`.
- [ ] `JobOffer.reconstitute(...)` sin setters públicos y sin `version`.
- [ ] `domain/` sigue sin Spring/JPA/Hibernate/Thymeleaf/Lombok.

**Aplicación**

- [ ] Puertos `in`: `RegisterJobOfferUseCase`, `GetJobOffersUseCase`.
- [ ] Puertos `out`: `JobOfferRepository`, `AuditPort`, `PageQuery`, `PageResult`.
- [ ] `RegisterJobOfferService` (`@Transactional`) con dedupe + auditoría `CREATE`.
- [ ] `GetJobOffersService` (`@Transactional(readOnly = true)`).

**Persistencia y auditoría**

- [ ] `JobOfferJpaEntity` mapea `job_offer` (singular) y pasa `ddl-auto: validate`.
- [ ] `JobOfferJpaRepository` + `JobOfferEntityMapper` + `JobOfferPersistenceAdapter`.
- [ ] `JpaAuditAdapter` inserta en `audit_event` (`jsonb`/`inet` con casts).

**Web**

- [ ] `GET /offers`, `GET /offers/new`, `POST /offers`, `GET /offers/{id}`,
      `GET /offers/registered`.
- [ ] Validación de servidor con Bean Validation; PRG tras crear.
- [ ] Duplicado → **error global** de formulario; oferta inexistente → `404`.
- [ ] Plantillas en español y claves `offers.*` en el bundle.
- [ ] `layout.html` con navbar responsive, enlace "saltar al contenido" y logout `POST`.

**Frontend**

- [ ] `app.css` define paleta, tipografía del sistema y sello; no hay fuentes ni CDN
      externos.
- [ ] Listado: cuadrícula en escritorio, filas apiladas en móvil, fila entera enlazada.
- [ ] Formulario: dos columnas ≥992px y una columna por debajo.
- [ ] Responsive comprobado en 320/375/768/1024/1440 px sin scroll horizontal.
- [ ] Foco visible, `aria-current`, `aria-describedby` en errores y `prefers-reduced-motion`
      respetado.
- [ ] Ningún `th:utext` en `templates/`.

**Seguridad**

- [ ] `/offers/**` restringido a `REGISTRADOR`/`ADMIN`, después de las rutas de
      `PUBLICADOR`.
- [ ] `@PreAuthorize` en `OffersController` (defensa en profundidad).
- [ ] CSRF activo; todos los `POST` usan `th:action`.
- [ ] `originalSourceUrl` solo acepta `http(s)://`.
- [ ] Enlaces externos con `rel="noopener noreferrer"`.
- [ ] `404` genérico sin eco del ID; headers `nosniff` y `DENY`.
- [ ] `audit_event` sin datos sensibles; los fallos no se auditan en T3 (deuda T16).

**Tests y cierre**

- [ ] `RegisterJobOfferServiceTest`, `JobOfferEntityMapperTest`, `OffersControllerTest`,
      `SecurityRulesTest`.
- [ ] `HexagonalArchitectureTest` en verde (ahora sí comprueba `application`).
- [ ] `./mvnw -B test -Dtest="JobOfferTest,JobStatusTransitionTest,HexagonalArchitectureTest,RegisterJobOfferServiceTest,JobOfferEntityMapperTest,OffersControllerTest,SecurityRulesTest"`
      en verde.
- [ ] `./mvnw -B -DskipTests package` en verde.
- [ ] Verificación manual: oferta `REGISTERED` visible y fila `CREATE` en `audit_event`.
- [ ] Verificación manual: `publicador` → `403`, URL `ftp://` rechazada y responsive en
      320 px.
- [ ] `git diff --check` limpio y commit
      `feat: registro de ofertas con validación y auditoría (#T3)`.
- [ ] `git status --short` limpio.

> **Deuda consciente de T3:** no hay tests de persistencia real (necesitan
> `EmbeddedPostgresSupport`, T9), no se auditan fallos (T16), la IP detrás de proxy se
> resuelve en T16 y la carrera del `UNIQUE` se traduce a error de negocio en T9/T16.
