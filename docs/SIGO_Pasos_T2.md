# SIGO — Guía paso a paso de T2 (ciclo de vida del dominio)

> **Cómo usar este documento.** Cada paso tiene **Objetivo**, **Dónde** (paquete/archivo),
> **Qué escribir** (contrato + reglas, *no* el código completo) y **Cómo verificar**.
> **Tú escribes el código; esta guía te orienta.** Si algo diverge, mandan el
> `docs/SIGO_Plan_Desarrollo.md` §4.5/§4.12 y el `docs/SIGO_Especificacion_Proyecto.md`
> §7/§13/§36/§39.
>
> Referencias: plan §3 (estructura), §4.5 (dominio), §4.12 (pruebas/ArchUnit), §5 (T2),
> §6 (pirámide de pruebas), §10 (convenciones); spec §6–§7 (estados), §13 (modelo),
> §36 (excepciones), §39 (tests); tutorial T2; `docs/adr/0008` (sin `CANCELLED`),
> `docs/adr/0012` (Lombok solo en entidades JPA).
>
> **Prerrequisito:** T1 cerrado y commiteado. T2 **no** toca base de datos, web, Flyway ni
> seguridad. Solo añade clases de dominio y tests puros.

---

## Notas previas (leer antes de empezar)

- **Paquete real:** `com.evela.sigotrab` (el plan escribe `com.sigotrab`; seguimos el código).
- **T2 es dominio puro:** `domain/` **no** importa Spring, JPA, Hibernate, Thymeleaf ni
  Lombok. Solo JDK (`java.util`, `java.time`).
- **No hay migración Flyway en T2.** Las tablas ya existen desde T1 (`V1`/`V2`); T2 aún no
  las usa.
- **No hay controladores, servicios, puertos ni repositorios en T2.** Eso empieza en T3.
- **Sin `@Transactional` en el dominio.** Las transacciones viven en `application/` (T3);
  el dominio no conoce Spring.
- **Todos los comandos Maven se ejecutan desde `sigotrab/`**, no desde la raíz.
- **No añadas dependencias:** `JUnit 5`, `AssertJ` y `ArchUnit` ya están en `pom.xml`
  (`archunit-junit5` 1.5.1).
- **Regla de oro:** primero el dominio y sus tests; después el caso de uso (T3), al final
  la web. T2 termina con tests verdes, no con una pantalla nueva.

### Qué NO entra en T2 (para no adelantar trabajo)

| Cosa | Ticket |
|---|---|
| `JobOfferJpaEntity`, repositorio, mapper | T3 |
| `RegisterJobOfferUseCase`, puertos `in`/`out` | T3 |
| `/offers`, `/offers/new`, plantillas | T3 |
| Auditoría, historial de estados | T3/T5 |
| `UPDATE` atómico y concurrencia real | T6/T9 |
| Scheduler de timeout | T8 |
| `ActorInfo`, `AuditAction`, `AuditRecord` | T3/T5 |

---

## Paso 0 — Cerrar T1 antes de empezar T2

**Objetivo:** que el commit de T2 contenga solo T2. Si el árbol de trabajo tiene cambios
de T1 sin commitear, empieza por cerrarlos; si no, mezclarás ambos tickets.

**Dónde:** raíz del repositorio.

**Qué hacer:**

```bash
cd sigotrab
./mvnw -B -DskipTests package
cd ..

git status --short
# Si aparecen cambios de T1, ciérralos primero:
git add -A
git commit -m "feat: esqueleto con base de datos, login y dashboard (#T1)"
git status --short
```

**Cómo verificar:** `git status --short` no muestra cambios pendientes (o solo los que
sepas que son de T2, que aún no existen). El jar de T1 existe:

```bash
ls sigotrab/target/sigotrab-*.jar
```

> Si decides no commitear T1 todavía, trabaja igual, pero al final separa los archivos de
> T2 en el commit. Lo limpio es cerrar T1 primero.

---

## Paso 1 — Crear la estructura de paquetes

**Objetivo:** dejar el esqueleto de carpetas de T2.

**Dónde:** `sigotrab/src/main/java/com/evela/sigotrab/` y
`sigotrab/src/test/java/com/evela/sigotrab/`.

**Qué crear:**

```text
src/main/java/com/evela/sigotrab/domain/
├── model/
│   ├── JobStatus.java
│   ├── NewJobOffer.java
│   ├── JobOfferContent.java
│   └── JobOffer.java
└── exception/
    ├── InvalidTransitionException.java
    ├── InvalidJobStateException.java
    ├── UnauthorizedJobOperationException.java
    ├── RejectionReasonRequiredException.java
    ├── JobLockedException.java
    └── JobNotFoundException.java

src/test/java/com/evela/sigotrab/
├── domain/
│   ├── JobStatusTransitionTest.java
│   └── JobOfferTest.java
└── architecture/
    └── HexagonalArchitectureTest.java
```

**Cómo verificar:** `find src/main/java/com/evela/sigotrab/domain -type f` y
`find src/test/java/com/evela/sigotrab/domain -type f` muestran los archivos (aún vacíos o
con la clase mínima).

> Git no guarda carpetas vacías. Crea primero un archivo real en cada carpeta; no hace
> falta `.gitkeep` si ya vas a escribir las clases.

---

## Paso 2 — `JobStatus`: la máquina de estados

**Objetivo:** que las transiciones válidas estén en un solo lugar y se puedan probar sin
Spring.

**Dónde:** `domain/model/JobStatus.java`.

**Qué escribir:** un `enum` con los cinco estados y un método `canTransitionTo`.

```java
package com.evela.sigotrab.domain.model;

import java.util.Map;
import java.util.Set;

public enum JobStatus {
    REGISTERED,
    APPROVED,
    IN_PROGRESS,
    PUBLISHED,
    REJECTED;

    private static final Map<JobStatus, Set<JobStatus>> ALLOWED = Map.of(
            REGISTERED, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(IN_PROGRESS),
            IN_PROGRESS, Set.of(PUBLISHED, APPROVED),
            PUBLISHED, Set.of(),
            REJECTED, Set.of()
    );

    public boolean canTransitionTo(JobStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
```

**Reglas:**

- `REJECTED` y `PUBLISHED` son **terminales**: no salen de ahí.
- `IN_PROGRESS → APPROVED` es válido: timeout (T8) o cancelación manual (T7).
- `REGISTERED → REJECTED` es válido: rechazo (T5).
- **No existe `CANCELLED`** (ADR-0008): cancelar vuelve a `APPROVED`.
- No pongas reglas de negocio en el controlador; esta es la autoridad del dominio.

**Matriz completa:**

| Desde \ Hacia | REGISTERED | APPROVED | IN_PROGRESS | PUBLISHED | REJECTED |
|---|---:|---:|---:|---:|---:|
| **REGISTERED** | ✗ | ✓ | ✗ | ✗ | ✓ |
| **APPROVED** | ✗ | ✗ | ✓ | ✗ | ✗ |
| **IN_PROGRESS** | ✗ | ✓ | ✗ | ✓ | ✗ |
| **PUBLISHED** | ✗ | ✗ | ✗ | ✗ | ✗ |
| **REJECTED** | ✗ | ✗ | ✗ | ✗ | ✗ |

**Cómo verificar:** lo prueba `JobStatusTransitionTest` en el Paso 6.1.

---

## Paso 3 — Records de entrada y contenido editable

**Objetivo:** separar los datos de creación (`NewJobOffer`) de los datos que el
`REGISTRADOR` puede corregir (`JobOfferContent`), sin exponer setters públicos.

**Dónde:** `domain/model/NewJobOffer.java` y `domain/model/JobOfferContent.java`.

### 3.1 `NewJobOffer`

```java
package com.evela.sigotrab.domain.model;

import java.time.Instant;

public record NewJobOffer(
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
        Instant extractedAt
) {
}
```

### 3.2 `JobOfferContent`

```java
package com.evela.sigotrab.domain.model;

public record JobOfferContent(
        String title,
        String company,
        String description,
        String requirements,
        String location,
        String salary,
        String employmentType,
        String originalSourceUrl
) {
}
```

**Por qué estos campos:**

- `sourceName`, `externalReference` y `extractedAt` identifican la fuente y participan en
  la deduplicación `(source_name, external_reference)` del T3. **No** se editan en
  `updateContent`: cambiarlos rompería la identidad de la oferta.
- `originalSourceUrl` sí se puede corregir (errata en la URL).
- `publicationUrl` no está aquí: se completa al confirmar la publicación (T7).
- `location`, `salary` y `employmentType` son opcionales en el esquema `V1` (columnas
  nullable). No los hagas obligatorios en el dominio; T3 decidirá qué exige el formulario.

**Campos obligatorios en el dominio** (alineados con `V1`):

| Campo | ¿Obligatorio? |
|---|---|
| `title`, `company`, `description`, `requirements` | Sí |
| `originalSourceUrl` | Sí |
| `extractedAt` | Sí |
| `location`, `salary`, `employmentType` | No |
| `sourceName`, `externalReference` | No (pero T3 los usará para dedupe) |

**Cómo verificar:** compila y los tests de `JobOffer` podrán construir ambos records.

---

## Paso 4 — Excepciones de dominio

**Objetivo:** expresar errores de negocio sin depender de Spring ni de HTTP.

**Dónde:** `domain/exception/`.

**Qué escribir:** clases que extiendan `RuntimeException`, con campos `final`, getters y
un mensaje claro en español. No pongas `@ResponseStatus`, `@Entity` ni anotaciones de
Spring.

| Excepción | Constructor sugerido | Cuándo |
|---|---|---|
| `InvalidTransitionException` | `(UUID jobOfferId, JobStatus from, JobStatus to)` | Transición no permitida |
| `InvalidJobStateException` | `(UUID jobOfferId, JobStatus status, String operation)` | Operación no válida en ese estado (p. ej. editar en `APPROVED`) |
| `UnauthorizedJobOperationException` | `(UUID jobOfferId, UUID userId)` | El usuario no es el asignado |
| `RejectionReasonRequiredException` | `(UUID jobOfferId)` | Rechazo sin motivo |
| `JobLockedException` | `(UUID jobOfferId)` | Conflicto de adquisición (se usa en T6) |
| `JobNotFoundException` | `(UUID jobOfferId)` | Oferta inexistente (se usa en T3/T6) |

**Contrato de una excepción** (ejemplo; replica el patrón):

```java
package com.evela.sigotrab.domain.exception;

import com.evela.sigotrab.domain.model.JobStatus;

import java.util.UUID;

public class InvalidTransitionException extends RuntimeException {

    private final UUID jobOfferId;
    private final JobStatus from;
    private final JobStatus to;

    public InvalidTransitionException(UUID jobOfferId, JobStatus from, JobStatus to) {
        super("Transición no permitida para la oferta %s: %s -> %s"
                .formatted(jobOfferId, from, to));
        this.jobOfferId = jobOfferId;
        this.from = from;
        this.to = to;
    }

    public UUID getJobOfferId() {
        return jobOfferId;
    }

    public JobStatus getFrom() {
        return from;
    }

    public JobStatus getTo() {
        return to;
    }
}
```

**Reglas:**

- Guarda los datos relevantes como campos con getters: T3/T6 los usarán para mapear a
  mensajes web y a `409`/`404`.
- Los mensajes van en español; los tests no deberían depender del texto exacto salvo que
  sea parte del contrato.
- `JobLockedException` y `JobNotFoundException` pueden quedar sin uso en T2; son parte del
  vocabulario del dominio y se usan en T3/T6. No las borres.

**Cómo verificar:** `./mvnw -B -DskipTests compile` compila; ninguna excepción importa
Spring.

---

## Paso 5 — `JobOffer`: el agregado

**Objetivo:** que la oferta no sea un saco de setters; que solo pueda cambiar por
operaciones de negocio válidas.

**Dónde:** `domain/model/JobOffer.java`.

### 5.1 Campos y finalidad

| Campo | Tipo | `final` | Notas |
|---|---|---:|---|
| `id` | `UUID` | Sí | Se genera en `register` con `UUID.randomUUID()` |
| `title`, `company`, `description`, `requirements` | `String` | No | Contenido editable solo en `REGISTERED` |
| `location`, `salary`, `employmentType` | `String` | No | Opcionales |
| `originalSourceUrl` | `String` | No | Editable en `REGISTERED` |
| `sourceName`, `externalReference` | `String` | Sí | Identidad/dedupe; no se editan |
| `extractedAt` | `Instant` | Sí | Fecha de extracción de la fuente |
| `publicationUrl` | `String` | No | Se completa en T7; en T2 queda `null` |
| `status` | `JobStatus` | No | Lo cambian solo las transiciones |
| `assignedUserId` | `UUID` | No | Lo setea `acquire`; lo limpia `release`/`publish` |
| `lockedAt`, `lockExpiresAt` | `Instant` | No | Bloqueo temporal |
| `approvedAt`, `publishedAt` | `Instant` | No | Hitos del flujo |
| `createdAt` | `Instant` | Sí | `now` en `register` |
| `createdBy` | `UUID` | Sí | Actor que registra |
| `updatedAt` | `Instant` | No | Lo actualizan las operaciones |
| `updatedBy` | `UUID` | No | Actor de la última operación |

**No añadas `version` al dominio.** El optimistic locking es un detalle del adaptador JPA
(T6); la base ya tiene `version bigint`.

**No generes setters públicos.** Expón getters (`getId()`, `getStatus()`, etc.) y métodos
de negocio. Si necesitas un setter para que un test pase, probablemente el diseño esté mal.

> T3 necesitará reconstruir la oferta desde JPA. Añadirá entonces un `reconstitute(...)`
> (o un constructor específico para el mapper); en T2 no lo necesitas.

### 5.2 `register(...)`

Firma:

```java
public static JobOffer register(NewJobOffer data, UUID actor, Instant now)
```

**Contrato:**

1. Valida `data`, `actor` y `now` no nulos.
2. Valida los campos obligatorios de `NewJobOffer` (no blank / no null).
3. Genera `id = UUID.randomUUID()`.
4. Copia el contenido y los metadatos de fuente.
5. Estado inicial: `REGISTERED`.
6. `createdAt = now`, `createdBy = actor`, `updatedAt = now`, `updatedBy = actor`.
7. `approvedAt`, `publishedAt`, `assignedUserId`, `lockedAt`, `lockExpiresAt` y
   `publicationUrl` quedan `null`.

**Sugerencia de diseño:** un constructor privado que reciba `(UUID id, NewJobOffer data,
UUID actor, Instant now)` evita un constructor de 20 parámetros y mantiene los `final`
inicializados una sola vez.

**Pseudocódigo:**

```text
register(data, actor, now):
  exigir data, actor, now
  exigir title, company, description, requirements, originalSourceUrl
  exigir extractedAt
  id = randomUUID()
  status = REGISTERED
  createdAt = updatedAt = now
  createdBy = updatedBy = actor
  copiar content + sourceName + externalReference + extractedAt
  return oferta
```

### 5.3 `updateContent(...)`

Firma:

```java
public void updateContent(JobOfferContent content, UUID actor, Instant now)
```

**Contrato:**

1. Solo si `status == REGISTERED`; si no, lanza `InvalidJobStateException(id, status, "update")`.
2. Valida el contenido obligatorio igual que `register`.
3. Copia `title`, `company`, `description`, `requirements`, `location`, `salary`,
   `employmentType` y `originalSourceUrl`.
4. **No** toca `sourceName`, `externalReference` ni `extractedAt`.
5. `updatedAt = now`, `updatedBy = actor`.

**Por qué:** la curación (RF-02) solo tiene sentido antes de aprobar. Una oferta
`APPROVED` ya está disponible para publicadores y no debe cambiar por debajo.

### 5.4 `approve(...)` y `reject(...)`

```java
public void approve(UUID actor, Instant now)
public void reject(UUID actor, String reason, Instant now)
```

**`approve`:**

1. Llama a `transitionTo(APPROVED, actor, now)` (valida la transición).
2. **Después** de que la transición tenga éxito, `approvedAt = now`.
3. No toques el contenido ni los locks.

> El orden importa: si pones `approvedAt = now` antes de `transitionTo`, una transición
> inválida deja el agregado a medias. Primero se valida/cambia el estado; después se
> registra el hito.

**`reject`:**

1. Si `reason == null || reason.isBlank()`, lanza `RejectionReasonRequiredException(id)`.
2. Llama a `transitionTo(REJECTED, actor, now)`.
3. No hay `rejectedAt` en el modelo; el historial/auditoría guarda el motivo (T5).

### 5.5 `acquire(...)`, `release(...)` y `publish(...)`

```java
public void acquire(UUID userId, Duration lockDuration, Instant now)
public void release(UUID actor, String reason, Instant now)
public void publish(UUID userId, Instant now)
```

**`acquire`:**

1. Valida `userId`, `lockDuration` y `now` no nulos.
2. `lockDuration` debe ser positivo (no cero, no negativo).
3. Llama a `transitionTo(IN_PROGRESS, userId, now)`.
4. `assignedUserId = userId`.
5. `lockedAt = now`.
6. `lockExpiresAt = now.plus(lockDuration)`.

> En T2 esto sirve para tests en memoria. **La concurrencia real no se resuelve con
> `synchronized`**: T6/T9 usan un `UPDATE ... WHERE` atómico en PostgreSQL. `acquire()`
> también se usará para rehidratar/validar después de una adquisición exitosa.

**`release`:**

1. Llama a `transitionTo(APPROVED, actor, now)`.
2. `clearLock()`: `assignedUserId`, `lockedAt` y `lockExpiresAt` a `null`.
3. `reason` queda como parámetro para el historial/auditoría (T7/T8). No lo guardes en el
   agregado y no lo valides aquí: el timeout de T8 no tiene un motivo escrito por un
   usuario; la cancelación manual de T7 sí lo exigirá en el caso de uso.

**`publish`:**

1. Si `!isAssignedTo(userId)`, lanza `UnauthorizedJobOperationException(id, userId)`.
2. Llama a `transitionTo(PUBLISHED, userId, now)`.
3. `publishedAt = now`.
4. `clearLock()`.
5. `publicationUrl` no se setea en T2; T7 decidirá si se recibe al confirmar.

> El orden también importa: primero se comprueba la asignación, luego la transición. Un
> usuario distinto debe recibir `UnauthorizedJobOperationException`, no un cambio de
> estado. Por eso, llamar a `publish` sobre una oferta `APPROVED` sin asignar da
> `UnauthorizedJobOperationException` (no es tu oferta), aunque la matriz de `JobStatus`
> también diga que `APPROVED → PUBLISHED` no es una transición válida.

### 5.6 Consultas de estado

```java
public boolean isLockExpired(Instant now)
public boolean isAssignedTo(UUID userId)
```

- `isLockExpired`: `true` solo si `status == IN_PROGRESS`, `lockExpiresAt != null` y
  `lockExpiresAt.isBefore(now)`. En el instante exacto de expiración devuelve `false`;
  la consulta SQL de T8 usa `lock_expires_at < :now`, así que es coherente.
- `isAssignedTo`: `true` solo si `userId != null` y coincide con `assignedUserId`.

### 5.7 Helpers privados

```java
private void transitionTo(JobStatus target, UUID actor, Instant now)
private void clearLock()
```

**`transitionTo`:**

1. Si `!status.canTransitionTo(target)`, lanza `InvalidTransitionException(id, status, target)`.
2. `status = target`.
3. `updatedAt = now`, `updatedBy = actor`.

**`clearLock`:** pone a `null` `assignedUserId`, `lockedAt` y `lockExpiresAt`.

**Regla:** ningún método público cambia `status` directamente; todo pasa por
`transitionTo`.

### 5.8 Diagrama del flujo

```text
REGISTERED ──approve──► APPROVED ──acquire──► IN_PROGRESS ──publish──► PUBLISHED
     │                                          │
     └──reject──► REJECTED                      └──release/timeout──► APPROVED
```

---

## Paso 6 — Tests

**Objetivo:** probar las reglas sin Spring, sin base de datos y sin arrancar la app.

**Dónde:** `src/test/java/com/evela/sigotrab/domain/` y
`src/test/java/com/evela/sigotrab/architecture/`.

### 6.1 `JobStatusTransitionTest`

**Qué probar:** la matriz de transiciones. Usa `@ParameterizedTest` + `@CsvSource`.

```java
package com.evela.sigotrab.domain;

import com.evela.sigotrab.domain.model.JobStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobStatusTransitionTest {

    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "REGISTERED,  APPROVED,    true",
            "REGISTERED,  REJECTED,    true",
            "REGISTERED,  IN_PROGRESS, false",
            "REGISTERED,  PUBLISHED,   false",
            "APPROVED,    IN_PROGRESS, true",
            "APPROVED,    PUBLISHED,   false",
            "APPROVED,    REJECTED,    false",
            "IN_PROGRESS, PUBLISHED,   true",
            "IN_PROGRESS, APPROVED,    true",
            "IN_PROGRESS, REGISTERED,  false",
            "PUBLISHED,   APPROVED,    false",
            "PUBLISHED,   IN_PROGRESS, false",
            "REJECTED,    APPROVED,    false",
            "REJECTED,    IN_PROGRESS, false"
    })
    void onlyConfiguredTransitionsAreAllowed(JobStatus from, JobStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
```

**Ampliación recomendada:** añade una prueba para cada estado terminal
(`import org.junit.jupiter.params.provider.EnumSource;`):

```java
@ParameterizedTest
@EnumSource(JobStatus.class)
void terminalStatesHaveNoOutgoingTransitions(JobStatus target) {
    assertThat(JobStatus.PUBLISHED.canTransitionTo(target)).isFalse();
    assertThat(JobStatus.REJECTED.canTransitionTo(target)).isFalse();
}
```

### 6.2 `JobOfferTest`

**Qué probar como mínimo:**

| Caso | Resultado esperado |
|---|---|
| `register` | `REGISTERED`, `createdAt/By`, `updatedAt/By`, contadores nulos |
| `register` con `title` blank | `IllegalArgumentException` |
| `approve` desde `REGISTERED` | `APPROVED`, `approvedAt`, `updatedAt/By` |
| `approve` desde `PUBLISHED` | `InvalidTransitionException` |
| `reject` con motivo blank/null | `RejectionReasonRequiredException` |
| `reject` con motivo | `REJECTED` |
| `updateContent` en `REGISTERED` | Campos cambiados; fuente intacta |
| `updateContent` en `APPROVED` | `InvalidJobStateException` |
| `acquire` desde `APPROVED` | `IN_PROGRESS`, asignado, `lockedAt`, `lockExpiresAt` |
| `acquire` desde `REGISTERED` | `InvalidTransitionException` |
| `acquire` con duración cero/negativa | `IllegalArgumentException` |
| `publish` por el asignado | `PUBLISHED`, `publishedAt`, lock limpio |
| `publish` por otro usuario | `UnauthorizedJobOperationException`; sigue `IN_PROGRESS` |
| `release` desde `IN_PROGRESS` | `APPROVED`, lock limpio |
| `isLockExpired` antes/justo/después | `false/false/true` |
| `isAssignedTo` con el asignado, otro y `null` | `true/false/false` |
| `published` no vuelve a `APPROVED` | `InvalidTransitionException` |

**Helper sugerido** (constantes fijas para que los fallos sean legibles):

```java
private static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");
private static final UUID REGISTRADOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
private static final UUID PUBLICADOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
private static final UUID OTRO = UUID.fromString("00000000-0000-0000-0000-000000000003");

private JobOffer registeredOffer() {
    return JobOffer.register(new NewJobOffer(
            "Analista de datos",
            "ACME",
            "Descripción",
            "SQL y Java",
            "Remoto",
            "1000 USD",
            "Tiempo completo",
            "https://example.com/oferta/1",
            "portal",
            "ext-1",
            T0
    ), REGISTRADOR, T0);
}
```

**Aserciones clave de `register`:**

```java
var offer = registeredOffer();

assertThat(offer.getId()).isNotNull();
assertThat(offer.getStatus()).isEqualTo(JobStatus.REGISTERED);
assertThat(offer.getCreatedAt()).isEqualTo(T0);
assertThat(offer.getCreatedBy()).isEqualTo(REGISTRADOR);
assertThat(offer.getUpdatedAt()).isEqualTo(T0);
assertThat(offer.getUpdatedBy()).isEqualTo(REGISTRADOR);
assertThat(offer.getApprovedAt()).isNull();
assertThat(offer.getPublishedAt()).isNull();
assertThat(offer.getAssignedUserId()).isNull();
assertThat(offer.getLockedAt()).isNull();
assertThat(offer.getLockExpiresAt()).isNull();
```

**Aserciones clave de `acquire`/`publish`:**

```java
var lock = Duration.ofMinutes(20);
var offer = registeredOffer();
offer.approve(REGISTRADOR, T0.plusSeconds(60));
offer.acquire(PUBLICADOR, lock, T0.plusSeconds(120));

assertThat(offer.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
assertThat(offer.getAssignedUserId()).isEqualTo(PUBLICADOR);
assertThat(offer.getLockedAt()).isEqualTo(T0.plusSeconds(120));
assertThat(offer.getLockExpiresAt()).isEqualTo(T0.plusSeconds(120).plus(lock));

offer.publish(PUBLICADOR, T0.plusSeconds(180));

assertThat(offer.getStatus()).isEqualTo(JobStatus.PUBLISHED);
assertThat(offer.getPublishedAt()).isEqualTo(T0.plusSeconds(180));
assertThat(offer.getAssignedUserId()).isNull();
assertThat(offer.getLockedAt()).isNull();
assertThat(offer.getLockExpiresAt()).isNull();
```

**Aserciones de error** (cada caso con su propia oferta para no encadenar estados):

```java
var wrongUserOffer = registeredOffer();
wrongUserOffer.approve(REGISTRADOR, T0.plusSeconds(60));
wrongUserOffer.acquire(PUBLICADOR, Duration.ofMinutes(20), T0.plusSeconds(120));

assertThatThrownBy(() -> wrongUserOffer.publish(OTRO, T0.plusSeconds(180)))
        .isInstanceOf(UnauthorizedJobOperationException.class);
assertThat(wrongUserOffer.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);

var rejectedOffer = registeredOffer();
assertThatThrownBy(() -> rejectedOffer.reject(REGISTRADOR, "  ", T0.plusSeconds(60)))
        .isInstanceOf(RejectionReasonRequiredException.class);

var approvedOffer = registeredOffer();
approvedOffer.approve(REGISTRADOR, T0.plusSeconds(60));
assertThatThrownBy(() -> approvedOffer.updateContent(
        new JobOfferContent("Otro", "ACME", "D", "R", null, null, null, "https://example.com/2"),
        REGISTRADOR, T0.plusSeconds(120)))
        .isInstanceOf(InvalidJobStateException.class);
```

> Para datos de entrada inválidos (campos obligatorios vacíos, duración no positiva) usa
> `IllegalArgumentException`; para reglas de estado, las excepciones de dominio.

### 6.3 `HexagonalArchitectureTest`

**Objetivo:** que `domain/` no dependa de frameworks y que `application/` no dependa de
`adapters/`.

**Dónde:** `src/test/java/com/evela/sigotrab/architecture/HexagonalArchitectureTest.java`.

```java
package com.evela.sigotrab.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.evela.sigotrab",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_pure = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "org.thymeleaf..",
                    "..adapters..",
                    "..application.."
            );

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapters..")
            .allowEmptyShould(true);
}
```

**Puntos a vigilar:**

- El paquete es `com.evela.sigotrab`, **no** `com.sigotrab`.
- `DoNotIncludeTests` evita que las clases de test (JUnit/AssertJ) se analicen como si
  fueran dominio.
- `application_does_not_depend_on_adapters` no tiene clases en T2 (aún no existe
  `application/`). ArchUnit 1.x falla si una regla no comprueba ninguna clase; por eso
  lleva `.allowEmptyShould(true)`. En T3, cuando existan clases, la regla empieza a
  vigilar de verdad.
- No añadas reglas de web/seguridad todavía; llegan en tickets posteriores.

### 6.4 Ejecutar los tests

```bash
cd sigotrab

# Solo T2 (no necesita PostgreSQL ni arrancar la app)
./mvnw -B test -Dtest=JobStatusTransitionTest,JobOfferTest,HexagonalArchitectureTest
```

**Resultado esperado:** `BUILD SUCCESS`, tres clases de test en verde.

> `./mvnw test` completo también ejecuta `SigotrabApplicationTests`, que es un
> `@SpringBootTest` y depende de la BD local. Si falla por PostgreSQL, no es un fallo de
> T2: usa el comando enfocado o resuelve la deuda de tests embebidos antes de T9.

---

## Paso 7 — Cierre de T2

**Objetivo:** dejar T2 empaquetado, revisado y commiteado.

```bash
cd sigotrab

# 1) tests de T2
./mvnw -B test -Dtest=JobStatusTransitionTest,JobOfferTest,HexagonalArchitectureTest

# 2) compilación/paquete (sin tests, para no depender de la BD)
./mvnw -B -DskipTests package

# 3) revisar que no haya imports prohibidos en domain/
grep -RInE "^import (org\.springframework|jakarta\.persistence|org\.hibernate|org\.thymeleaf|lombok)" \
  src/main/java/com/evela/sigotrab/domain || echo "OK: dominio puro"
```

Luego, desde la raíz:

```bash
git diff --check
git status --short
git add sigotrab/src/main/java/com/evela/sigotrab/domain \
        sigotrab/src/test/java/com/evela/sigotrab/domain \
        sigotrab/src/test/java/com/evela/sigotrab/architecture
git commit -m "feat: dominio con transiciones, excepciones y ArchUnit (#T2)"
git status --short
```

**Criterio:** la definición de "T2 terminado" del final.

> Si T1 aún no está commiteado, hazlo antes (Paso 0). Si no, el commit de T2 arrastrará
> login, dashboard, Docker y configuración de T1.

---

## Apéndice A — Errores comunes en T2

| Síntoma | Causa probable | Solución |
|---|---|---|
| ArchUnit no encuentra clases | `@AnalyzeClasses(packages = "com.sigotrab")` | Usa `com.evela.sigotrab` |
| ArchUnit marca tests como dominio | Se importaron las clases de test | Añade `ImportOption.DoNotIncludeTests.class` |
| ArchUnit falla con "failed to check any classes" | Una regla no encontró clases (p. ej. `application/` aún no existe) | Añade `.allowEmptyShould(true)` o crea la regla en T3 |
| `Map.of` lanza `IllegalArgumentException` | Más de 10 pares o claves duplicadas | Aquí son 5 estados; revisa que no haya duplicados |
| `canTransitionTo` permite todo | `getOrDefault` mal escrito o mapa incompleto | Revisa la matriz y usa `Set.of()` vacío para terminales |
| `register` deja `updatedAt` nulo | No inicializaste `updatedAt/updatedBy` | Inicialízalos con `now`/`actor` |
| `approve` deja `approvedAt` seteado tras fallar | Seteas el hito antes de validar la transición | Llama primero a `transitionTo` |
| `reject` acepta motivo vacío | Falta la comprobación `isBlank` | Lanza `RejectionReasonRequiredException` |
| `updateContent` funciona en `APPROVED` | Falta la comprobación de estado | Lanza `InvalidJobStateException` |
| `publish` no comprueba al asignado | Falta `isAssignedTo` | Lanza `UnauthorizedJobOperationException` |
| `publish` deja el lock | No llamas a `clearLock()` | Limpia los tres campos |
| `release` deja el lock | No llamas a `clearLock()` | Limpia los tres campos |
| `isLockExpired` lanza NPE | `lockExpiresAt` es nulo y no estás en `IN_PROGRESS` | Comprueba estado y nulo antes de `isBefore` |
| Tests con `Instant.now()` fallan a veces | Dependen del reloj real | Usa un `Instant` fijo (`T0`) |
| `domain` importa Spring/JPA | Se coló una anotación o `StringUtils` | Usa JDK; el test de ArchUnit lo detecta |
| `./mvnw test` falla sin BD | `SigotrabApplicationTests` es `@SpringBootTest` | Usa `-Dtest=...` o resuelve la deuda T9 |
| El commit de T2 incluye T1 | T1 no estaba commiteado | Cierra T1 primero (Paso 0) |

---

## Apéndice B — Conceptos y por qué

### B.1 ¿Por qué un `enum` con `canTransitionTo`?

Porque las transiciones son una regla de negocio única. Si las repartes por controladores,
servicios y plantillas, tarde o temprano una ruta permitirá `REGISTERED → PUBLISHED`. El
`enum` es fácil de testear y no depende de Spring.

### B.2 ¿Por qué un agregado y no setters?

Un `JobOffer` con `setStatus`, `setAssignedUserId`, etc. permite estados imposibles:
`PUBLISHED` con lock, `REJECTED` con `approvedAt`, `IN_PROGRESS` sin asignado. El agregado
expone operaciones (`approve`, `acquire`, `publish`) que mantienen la coherencia. Los
getters son para leer; no hay setters públicos.

### B.3 ¿Por qué `Instant now` en vez de `Instant.now()` dentro?

Para que los tests sean deterministas. El servicio (T3/T6) inyecta un `Clock` y pasa
`clock.instant()`; el dominio no conoce el reloj global. Es la misma idea que un
`Clock` fijo en T9.

### B.4 ¿Por qué excepciones de dominio?

Porque `false` o `null` no explican qué falló. `InvalidTransitionException` lleva la oferta,
el estado actual y el destino; T3/T6 pueden mapearlo a un mensaje web o a un `409`. No
extiendas `ResponseStatusException` ni uses HTTP en el dominio.

### B.5 ¿Por qué ArchUnit?

Para que la regla "el dominio no depende de frameworks" no sea solo una convención oral.
ArchUnit la convierte en un test que falla en CI. En T3 añadirás más reglas (por ejemplo,
controladores que no usan repositorios JPA).

### B.6 ¿Qué añadirán T3/T6/T8?

| Ticket | Añade |
|---|---|
| T3 | `JobOfferJpaEntity`, mapper, repositorio, `RegisterJobOfferUseCase`, web |
| T5 | aprobar/rechazar con historial y auditoría |
| T6 | `UPDATE ... WHERE` atómico; `acquire` real; `409 JOB_ALREADY_LOCKED` |
| T7 | `release` manual, `publish` con URL, pantallas de en curso/publicadas |
| T8 | scheduler que usa `isLockExpired` + `release(SYSTEM, ...)` |

T2 deja el vocabulario y las reglas listos para que esos tickets no las reinventen.

---

## Definición de "T2 terminado"

**Dominio**

- [ ] `JobStatus` con la matriz de transiciones y estados terminales.
- [ ] `NewJobOffer` y `JobOfferContent` como records.
- [ ] `JobOffer` con `register`, `updateContent`, `approve`, `reject`, `acquire`,
      `release`, `publish`, `isLockExpired` e `isAssignedTo`.
- [ ] Sin setters públicos de estado/locks.
- [ ] Excepciones de dominio con mensajes en español y datos útiles.
- [ ] `domain/` no importa Spring, JPA, Hibernate, Thymeleaf ni Lombok.

**Tests**

- [ ] `JobStatusTransitionTest` cubre la matriz (incluidos terminales).
- [ ] `JobOfferTest` cubre registro, aprobación, rechazo, edición, adquisición, liberación,
      publicación, autorización, expiración y estados terminales.
- [ ] `HexagonalArchitectureTest` pasa y excluye tests.
- [ ] `./mvnw -B test -Dtest=JobStatusTransitionTest,JobOfferTest,HexagonalArchitectureTest`
      en verde.
- [ ] `./mvnw -B -DskipTests package` en verde.

**Cierre**

- [ ] `git diff --check` sin errores de whitespace.
- [ ] Commit `feat: dominio con transiciones, excepciones y ArchUnit (#T2)`.
- [ ] `git status --short` limpio.
- [ ] T1 ya estaba commiteado (no se mezclan tickets).
