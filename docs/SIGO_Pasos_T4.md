# SIGO — Guía paso a paso de T4 (curar/editar oferta)

> **Cómo usar este documento.** Cada paso tiene **Objetivo**, **Dónde** (paquete/archivo),
> **Qué escribir** (contrato + reglas, *no* siempre el código completo) y **Cómo verificar**.
> **Tú escribes el código; esta guía te orienta.** Si algo diverge, mandan el
> `docs/SIGO_Plan_Desarrollo.md` §4.5–§4.10/§4.12 y el
> `docs/SIGO_Especificacion_Proyecto.md` (RF-02, RF-11, §9, §10).
>
> Referencias: plan §3 (estructura), §4.5 (dominio), §4.6 (puertos), §4.7 (persistencia),
> §4.9 (seguridad), §4.10 (web), §4.12 (pruebas), §5 (T4), §10 (convenciones);
> spec RF-02 (curación), RF-11 (auditoría), §9 (`AUDIT_EVENT`), §10 (acciones auditables),
> §24 (duplicados), §35–§36 (errores/excepciones); `docs/adr/0012` (Lombok solo en JPA).
>
> **Prerrequisito:** T3 cerrado (registro de ofertas con auditoría `CREATE`). T4 **no**
> añade tablas ni columnas: `job_offer` y `audit_event` ya existen en `V1__baseline.sql`.
> No hay migración Flyway nueva.

---

## Notas previas (leer antes de empezar)

- **Paquete real:** `com.evela.sigotrab` (el plan escribe `com.sigotrab`; seguimos el código).
- **Hexagonal estricta:** `domain/` no importa Spring/JPA/Hibernate/Thymeleaf/Lombok.
  `application/` solo depende de `domain/` y de sus puertos. `adapters/` e
  `infrastructure/` dependen hacia dentro. `HexagonalArchitectureTest` lo verifica.
- **`@Transactional` solo en `application/`.** El controlador nunca toca JPA.
- **Sin Lombok en `domain/`** (ADR-0012: solo en entidades JPA).
- **Todos los comandos Maven desde `sigotrab/`**, no desde la raíz.
- **No añadas dependencias:** todo lo que necesitas ya está en `pom.xml`.
- **El dominio ya sabe editar.** `JobOffer.updateContent(JobOfferContent, UUID, Instant)`
  existe desde T2 y ya lanza `InvalidJobStateException` si el estado no es `REGISTERED`.
  T4 **no lo reescribe**: lo conecta a la aplicación, lo audita y le pone web.

### Qué es T4 en una frase

> Un `REGISTRADOR` (o `ADMIN`) abre una oferta `REGISTERED`, corrige lo que haga falta y
> guarda. La oferta se actualiza **en sitio** (misma fila, `version + 1`) y se escribe un
> `audit_event` con `action = 'UPDATE'` y los valores **anteriores y posteriores**.

### Qué NO entra en T4 (para no adelantar trabajo)

| Cosa | Ticket |
|---|---|
| Aprobar / rechazar (`REGISTERED → APPROVED/REJECTED`) y `StatusHistoryPort` | T5 |
| `acquire` atómico, `409 JOB_ALREADY_LOCKED`, `/offers/approved` | T6 |
| Cancelar, publicar, `/offers/in-progress`, `/offers/published` | T7 |
| Scheduler de timeout | T8 |
| `/audit` consultable con filtros | T11 |
| Línea de tiempo de la oferta en el detalle | T10 |
| Editar la **procedencia** (`source_name`, `external_reference`, `extracted_at`) | nunca en v1 |

> **Delta documentado 1:** el plan §4.6 no nombra un `UpdateJobOfferUseCase`; T4 lo crea
> porque la edición es un caso de uso propio (con su auditoría), no un método del registro.
>
> **Delta documentado 2:** el plan §4.9 restringe `/offers/*/edit` a `REGISTRADOR`. En el
> código, `ADMIN` ya entra a `/offers/new` (T3); dejar la edición solo en `REGISTRADOR`
> produciría un `403` incoherente para un `ADMIN` que ve el botón. T4 usa
> `hasAnyRole("REGISTRADOR", "ADMIN")` en `/offers/*/edit`.

---

## Paso 0 — Punto de partida (checklist de T3)

Antes de tocar nada, comprueba que T3 está entero:

```bash
cd sigotrab
./mvnw -B test -Dtest="RegisterJobOfferServiceTest,JobOfferEntityMapperTest,OffersControllerTest,SecurityRulesTest"
```

Debe salir en verde (24 tests). Si no, cierra T3 antes de empezar.

Archivos que T4 **va a tocar** (no los reescribas desde cero):

- `domain/model/AuditRecord.java` (una factoría nueva).
- `adapters/persistence/mapper/JobOfferEntityMapper.java` y `.../JobOfferPersistenceAdapter.java`.
- `adapters/web/controller/OffersController.java`.
- `adapters/web/mapper/JobOfferWebMapper.java`.
- `templates/offers/form.html`, `templates/offers/detail.html`.
- `messages/messages_es.properties`.
- `infrastructure/security/SecurityConfig.java`.

---

## Paso 1 — Estructura de archivos de T4

```
sigotrab/src/main/java/com/evela/sigotrab/
├── application/
│   ├── port/in/UpdateJobOfferUseCase.java          (nuevo)
│   └── service/UpdateJobOfferService.java          (nuevo)
├── adapters/
│   ├── persistence/
│   │   ├── JobOfferPersistenceAdapter.java         (modificado: upsert)
│   │   └── mapper/JobOfferEntityMapper.java        (modificado: copyTo)
│   └── web/
│       ├── dto/JobOfferEditForm.java               (nuevo)
│       ├── mapper/JobOfferWebMapper.java           (modificado)
│       └── controller/OffersController.java        (modificado)
├── domain/model/AuditRecord.java                   (modificado: factoría old/new)
└── infrastructure/security/SecurityConfig.java     (modificado: /offers/*/edit)

sigotrab/src/main/resources/
├── messages/messages_es.properties                 (claves nuevas)
└── templates/offers/{form,detail}.html             (modificados)

sigotrab/src/test/java/com/evela/sigotrab/
├── application/service/UpdateJobOfferServiceTest.java   (nuevo)
├── adapters/persistence/mapper/JobOfferEntityMapperTest.java  (+1)
└── adapters/web/controller/{OffersControllerTest,SecurityRulesTest}.java  (+7, +3)
```

**Cómo verificar:** `git status --short` debe mostrar exactamente esos archivos.

---

## Paso 2 — Dominio: `AuditRecord` con `old` y `new`

**Objetivo:** poder describir una modificación con los valores de antes y de después en
un solo evento.

**Dónde:** `domain/model/AuditRecord.java`.

**Contrato:** añade una factoría de seis argumentos (no toques las que ya hay):

```java
public static AuditRecord success(AuditAction action, String entityType,
        String entityId, ActorInfo actor,
        Map<String, Object> oldValues, Map<String, Object> newValues) {
    return new AuditRecord(action, entityType, entityId, actor, true, null, null,
            oldValues, newValues);
}
```

**Reglas:**

- `oldValues`/`newValues` son `Map<String, Object>` **nulables** (un `CREATE` no tiene
  “antes”; un `DELETE` no tendría “después”). No uses `Map.of(...)` con valores `null`:
  revienta con `NullPointerException`. En el servicio se usa `LinkedHashMap`.
- `AuditAction.UPDATE` ya existe en el enum. No lo dupliques.
- **No** añadas un campo `timestamp` al record: lo pone la base (`default now()`).

**Cómo verificar:** `./mvnw -B -DskipTests compile` compila y `JobOfferTest` sigue verde.

---

## Paso 3 — Puerto de entrada `UpdateJobOfferUseCase`

**Objetivo:** que la web dependa de una interfaz de aplicación, no del servicio.

**Dónde:** `application/port/in/UpdateJobOfferUseCase.java`.

**Contrato:**

```java
public interface UpdateJobOfferUseCase {
    JobOffer update(UUID id, JobOfferContent content, ActorInfo actor);
}
```

**Por qué `JobOfferContent` y no `JobOffer`:** el contenido editable es exactamente lo que
el dominio permite cambiar (`updateContent`). Pasar el agregado entero invitaría a tocar
estado, procedencia o bloqueo desde fuera.

**Cómo verificar:** `./mvnw -B -DskipTests compile`.

---

## Paso 4 — `UpdateJobOfferService`

**Objetivo:** orquestar la curación: cargar, fotografiar el “antes”, delegar en el dominio,
guardar y auditar `UPDATE` con `old/new`, todo en una transacción.

**Dónde:** `application/service/UpdateJobOfferService.java`.

**Contrato:**

```java
@Service
@Transactional
public class UpdateJobOfferService implements UpdateJobOfferUseCase {

    private final JobOfferRepository offers;
    private final AuditPort audit;
    private final Clock clock;

    // constructor con los tres

    @Override
    public JobOffer update(UUID id, JobOfferContent content, ActorInfo actor) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(actor, "actor");
        Instant now = clock.instant();

        JobOffer offer = offers.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        Map<String, Object> oldValues = snapshot(offer);

        // El dominio decide: fuera de REGISTERED lanza InvalidJobStateException.
        offer.updateContent(content, actor.userId(), now);

        offers.save(offer);
        audit.record(AuditRecord.success(
                AuditAction.UPDATE, "JOB_OFFER", offer.getId().toString(), actor,
                oldValues, snapshot(offer)));
        return offer;
    }

    private static Map<String, Object> snapshot(JobOffer offer) {
        var values = new LinkedHashMap<String, Object>();
        values.put("title", offer.getTitle());
        values.put("company", offer.getCompany());
        values.put("description", offer.getDescription());
        values.put("requirements", offer.getRequirements());
        values.put("location", offer.getLocation());
        values.put("salary", offer.getSalary());
        values.put("employmentType", offer.getEmploymentType());
        values.put("originalSourceUrl", offer.getOriginalSourceUrl());
        return values;
    }
}
```

**Reglas y decisiones:**

1. **El orden importa.** `findById` → `snapshot` → `updateContent` → `save` → `audit`.
   Si fotografías después de mutar, el “antes” es el “después” y la auditoría miente.
2. **La autoridad del estado es el dominio.** No repitas `if (status != REGISTERED)` en el
   servicio: `updateContent` ya lo comprueba y lanza `InvalidJobStateException`.
3. **`snapshot` cubre solo los ocho campos mutables.** La procedencia no se edita, así que
   no aparece en el diff. Si algún día se edita, este es el sitio donde añadirla.
4. **`LinkedHashMap`, no `Map.of`.** `location`, `salary` y `employmentType` pueden ser
   `null` y `Map.of` no admite `null`.
5. **Sin `catch` de `InvalidJobStateException`:** el servicio propaga; la web decide qué
   hacer (Paso 8). La capa de aplicación no conoce HTTP.
6. **`@Transactional`** (de `jakarta.transaction`, como el resto del proyecto): el `UPDATE`
   y el `INSERT` de auditoría viven o mueren juntos.

**Cómo verificar:** `UpdateJobOfferServiceTest` (Paso 12). Debe comprobar que `audit`
recibe un `AuditRecord` con `oldValues.title = "Analista"` y
`newValues.title = "Analista senior"`.

---

## Paso 5 — Persistencia: actualizar sin romper `@Version`

**Objetivo:** que `save()` actualice la fila existente conservando el bloqueo optimista.

**Dónde:** `adapters/persistence/mapper/JobOfferEntityMapper.java` y
`adapters/persistence/JobOfferPersistenceAdapter.java`.

### 5.1 El problema

`JobOfferJpaEntity` tiene `@Version Long version`. El mapper construye una entidad **nueva**
con `version = null`. Hasta T3 eso era correcto (siempre se insertaba). En T4 el id ya
existe, y `jpa.save(entidadConIdYVersionNull)` puede acabar en un `INSERT` (o en un `merge`
que ignora la versión): o revientas la PK, o pierdes el bloqueo optimista sin enterarte.

### 5.2 `copyTo`: copiar sobre la entidad gestionada

```java
public JobOfferJpaEntity toEntity(JobOffer d) {
    var e = new JobOfferJpaEntity();
    e.setId(d.getId());
    copyTo(e, d);
    return e;
}

/**
 * Copia el estado del agregado sobre una entidad ya gestionada.
 * No toca {@code id} ni {@code version}: Hibernate los necesita para el UPDATE.
 */
public void copyTo(JobOfferJpaEntity e, JobOffer d) {
    e.setTitle(d.getTitle());
    e.setCompany(d.getCompany());
    // ... el resto de columnas, igual que en toEntity ...
    e.setUpdatedAt(d.getUpdatedAt());
    e.setUpdatedBy(d.getUpdatedBy());
}
```

### 5.3 `save` como upsert

```java
@Override
public void save(JobOffer jobOffer) {
    // Upsert: si la fila existe se actualiza la entidad gestionada (conserva @Version);
    // si no existe se inserta.
    jpa.findById(jobOffer.getId()).ifPresentOrElse(
            entity -> mapper.copyTo(entity, jobOffer),
            () -> jpa.save(mapper.toEntity(jobOffer)));
}
```

**Por qué funciona:** dentro de la transacción, `findById` devuelve la entidad
**gestionada**; `copyTo` la muta y Hibernate hace *dirty checking*: un `UPDATE` con
`WHERE id = ? AND version = ?` y `version = version + 1`. Si otro usuario guardó antes,
la versión no coincide → `ObjectOptimisticLockingFailureException`.

**Coste:** un `SELECT` extra por `save` (también en el alta). Es el precio de no adivinar
si la fila existe; en T6 el `acquire` atómico tendrá su propio `UPDATE ... WHERE`.

**Cómo verificar:**

- `JobOfferEntityMapperTest.copyToUpdatesManagedEntityWithoutTouchingIdOrVersion`:
  fija `version = 7`, llama a `copyTo` y comprueba que sigue siendo `7`.
- Manual (Paso 13): tras editar, `select version from job_offer` debe pasar de `0` a `1`.

---

## Paso 6 — DTO de entrada `JobOfferEditForm`

**Objetivo:** un contrato de formulario que solo exponga lo editable.

**Dónde:** `adapters/web/dto/JobOfferEditForm.java`.

**Contrato:**

```java
public record JobOfferEditForm(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 150) String company,
        @NotBlank String description,
        @NotBlank String requirements,
        @Size(max = 150) String location,
        @Size(max = 100) String salary,
        @Size(max = 50) String employmentType,
        @NotBlank @Size(max = 500) @URL
        @Pattern(regexp = "^\\s*https?://\\S+\\s*$", message = "{offers.validation.url}")
        String originalSourceUrl
) {
}
```

**Reglas:**

- **No reutilices `JobOfferForm`.** Ese exige `extractedAt` (`@NotNull`) y arrastra
  `sourceName`/`externalReference`, que aquí no se editan.
- Las validaciones deben ser **las mismas** que en el alta para los campos comunes; si
  divergen, el mismo dato se acepta al crear y se rechaza al editar.
- `@URL` acepta `ftp://` y `javascript:`; el `@Pattern` es la defensa real (T3, §14.6).
- El mensaje de la URL se resuelve por clave (`{offers.validation.url}`), no se escribe
  el texto en la anotación.

**Cómo verificar:** `POST /offers/{id}/edit` sin `title` debe devolver el formulario con
error de campo `title` (Paso 12).

---

## Paso 7 — Mapper web: `toContent` y `toEditForm`

**Objetivo:** traducir entre el DTO de edición y el dominio, sin lógica en el controlador.

**Dónde:** `adapters/web/mapper/JobOfferWebMapper.java`.

**Contrato:**

```java
public JobOfferContent toContent(JobOfferEditForm f) {
    return new JobOfferContent(
            f.title().trim(),
            f.company().trim(),
            f.description(),
            f.requirements(),
            trimToNull(f.location()),
            trimToNull(f.salary()),
            trimToNull(f.employmentType()),
            f.originalSourceUrl().trim());
}

public JobOfferEditForm toEditForm(JobOffer o) {
    return new JobOfferEditForm(
            o.getTitle(), o.getCompany(), o.getDescription(), o.getRequirements(),
            o.getLocation(), o.getSalary(), o.getEmploymentType(), o.getOriginalSourceUrl());
}
```

**Reglas:** `trim()` en los obligatorios y `trimToNull` en los opcionales, igual que en el
alta; así “ ” no se guarda como valor y `""` no viaja a la base como cadena vacía.

**Cómo verificar:** el formulario de edición debe llegar **precargado** (test
`editFormIsPrefilledWithCurrentValues`).

---

## Paso 8 — Controlador: `GET` y `POST /offers/{id}/edit`

**Objetivo:** pintar el formulario precargado, validar, delegar y volver al detalle (PRG).

**Dónde:** `adapters/web/controller/OffersController.java`.

**Contrato:**

```java
@GetMapping("/{id}/edit")
public String editForm(@PathVariable UUID id, Model model, RedirectAttributes ra) {
    JobOffer offer = getJobOffers.getById(id);
    if (!isEditable(offer)) {
        ra.addFlashAttribute("error", "offers.edit.notEditable");
        return "redirect:/offers/" + id;
    }
    model.addAttribute("form", mapper.toEditForm(offer));
    addEditModel(id, offer, model);
    return "offers/form";
}

@PostMapping("/{id}/edit")
public String update(@PathVariable UUID id,
                     @Valid @ModelAttribute("form") JobOfferEditForm form,
                     BindingResult binding,
                     @AuthenticationPrincipal SigUser user,
                     HttpServletRequest request,
                     Model model,
                     RedirectAttributes ra) {
    if (binding.hasErrors()) {
        addEditModel(id, getJobOffers.getById(id), model);
        return "offers/form";
    }
    if (user == null) {
        return "redirect:/login";
    }
    ActorInfo actor = new ActorInfo(user.id(), user.getUsername(), request.getRemoteAddr());
    try {
        updateJobOffer.update(id, mapper.toContent(form), actor);
    } catch (InvalidJobStateException ex) {
        ra.addFlashAttribute("error", "offers.edit.notEditable");
        return "redirect:/offers/" + id;
    } catch (ObjectOptimisticLockingFailureException ex) {
        ra.addFlashAttribute("error", "offers.edit.conflict");
        return "redirect:/offers/" + id;
    }
    ra.addFlashAttribute("updated", true);
    return "redirect:/offers/" + id;
}

private static boolean isEditable(JobOffer offer) {
    return offer.getStatus() == JobStatus.REGISTERED;
}

private void addEditModel(UUID id, JobOffer offer, Model model) {
    model.addAttribute("offer", mapper.toView(offer));
    model.addAttribute("offerId", id);
    model.addAttribute("editing", true);
    model.addAttribute("activeNav", "offers");
}
```

**Reglas y decisiones:**

1. **`BindingResult` va justo después de `@Valid`.** Si lo separas, Spring lanza
   `IllegalStateException: An Errors/BindingResult argument is expected...`.
2. **Primero los errores, después el actor.** Con `@WithMockUser` el principal no es
   `SigUser`; si compruebas `user == null` antes, el formulario con errores no se puede
   ni renderizar en tests.
3. **El `id` viaja en la ruta, no en un campo oculto.** El path es el que autoriza y el que
   evita IDOR por manipulación del body (§14.5 de T3).
4. **`GET` comprueba el estado y redirige**, no devuelve un formulario inútil. `POST` **no**
   se fía: el estado pudo cambiar entre el `GET` y el `POST` (otro usuario aprobó) y el
   dominio lanza `InvalidJobStateException`.
5. **El flash lleva una clave, no un texto.** La plantilla la resuelve con
   `#messages.msg(error)`; así el mensaje se traduce y no se cuela HTML en el modelo.
6. **PRG (Post/Redirect/Get):** tras guardar, `302` al detalle con `updated = true`. Nada
   de “reload = reenvío” (F5 no vuelve a editar).
7. **El duplicado no aplica aquí:** la pareja `source_name + external_reference` no se
   edita, así que no hay `catch (DuplicateJobOfferException)`.

**Cómo verificar:** los siete tests nuevos de `OffersControllerTest` (Paso 12).

---

## Paso 9 — Plantilla: un formulario, dos modos

**Objetivo:** reutilizar `form.html` para alta y edición sin duplicar 150 líneas de campos.

**Dónde:** `templates/offers/form.html` y `templates/offers/detail.html`.

### 9.1 `form.html`

El controlador inyecta tres datos que la plantilla usa como conmutador:

| Atributo | Alta | Edición |
|---|---|---|
| `editing` | `false` | `true` |
| `offerId` | — | UUID |
| `offer` | — | `JobOfferView` (solo para la procedencia de lectura) |

Fragmentos clave:

```html
<title th:text="(${editing} ? #{offers.edit.title} : #{offers.new}) + ' - SIGO'">...</title>

<form class="form-sheet" th:object="${form}"
      th:action="${editing} ? @{/offers/{id}/edit(id=${offerId})} : @{/offers}"
      method="post" novalidate>
```

En la sección “Procedencia”, la URL se edita siempre; fuente, referencia y fecha solo se
editan al **crear**:

```html
<th:block th:if="${editing != true}">
  <!-- inputs de sourceName, externalReference y extractedAt (solo alta) -->
</th:block>

<th:block th:if="${editing == true}">
  <dl class="dossier__meta mb-0">
    <div>
      <dt th:text="#{offers.field.sourceName}">Fuente</dt>
      <dd th:text="${offer.sourceName} ?: #{offers.notAvailable}">Sin dato</dd>
    </div>
    <div>
      <dt th:text="#{offers.field.externalReference}">Referencia externa</dt>
      <dd class="mono" th:text="${offer.externalReference} ?: #{offers.notAvailable}">Sin dato</dd>
    </div>
    <div>
      <dt th:text="#{offers.field.extractedAt}">Fecha de extracción</dt>
      <dd class="mono" th:text="${offer.extractedAt}">2026-01-15 10:00 UTC</dd>
    </div>
  </dl>
  <p class="form-text mt-3 mb-0" th:text="#{offers.edit.sourceLocked}">La procedencia no se puede modificar.</p>
</th:block>
```

El botón y el “Cancelar” cambian de texto y destino:

```html
<button class="btn btn-primary" type="submit"
        th:text="${editing} ? #{offers.edit.save} : #{offers.save}">Guardar oferta</button>
<a class="btn btn-quiet"
   th:href="${editing} ? @{/offers/{id}(id=${offerId})} : @{/offers}"
   th:text="#{offers.cancel}">Cancelar</a>
```

> **Trampa real:** `${!editing}` **explotó** con
> `EL1001E: Type conversion problem, cannot convert from null to boolean` porque el alta no
> ponía `editing` en el modelo. Dos arreglos, ambos necesarios:
> 1. `newForm` añade `model.addAttribute("editing", false)` (contrato explícito).
> 2. La plantilla usa guardas **null-safe**: `editing != true` / `editing == true`.
>
> El ternario `${editing} ? ... : ...` sí tolera `null` (Thymeleaf lo evalúa como falso),
> pero `!editing` no: es una expresión SpEL y `null` no es `boolean`.

### 9.2 `detail.html`

- Aviso verde de “creada” (`${success}`) y aviso verde de “actualizada” (`${updated}`).
- Aviso rojo con el error del flash: `th:text="${#messages.msg(error)}"`.
- Botón **Editar** solo si el estado es `REGISTERED` y el usuario puede curar:

```html
<div class="d-flex justify-content-between align-items-center gap-2 mb-3">
  <a class="link-quiet text-decoration-none" th:href="@{/offers/registered}">
    <i class="bi bi-arrow-left" aria-hidden="true"></i>
    <span th:text="#{offers.back}">Volver al listado</span>
  </a>
  <a class="btn btn-primary btn-sm"
     sec:authorize="hasAnyRole('REGISTRADOR','ADMIN')"
     th:if="${offer.status.name() == 'REGISTERED'}"
     th:href="@{'/offers/' + ${offer.id} + '/edit'}"
     th:text="#{offers.edit}">Editar oferta</a>
</div>
```

**Reglas:**

- `xmlns:sec="http://www.thymeleaf.org/extras/spring-security"` es **obligatorio** en
  `detail.html`; sin él, `sec:authorize` no se procesa.
- El botón es comodidad, no seguridad: el `GET` y el `POST` vuelven a comprobar estado y
  rol. Si el estado cambia mientras miras el detalle, el botón desaparece en la siguiente
  recarga y el `POST` responde con el aviso de “ya no está REGISTRADO”.
- `offer.status.name() == 'REGISTERED'` evita comparar el enum con una cadena desde SpEL.

**Cómo verificar:** `GET /offers/{id}/edit` devuelve el formulario con el título precargado,
sin `input name="sourceName"` y con el aviso “La procedencia no se puede modificar”.

---

## Paso 10 — Claves nuevas en `messages_es.properties`

**Dónde:** `sigotrab/src/main/resources/messages/messages_es.properties` (acentos como
`\u00XX`, igual que el resto del archivo).

```properties
# OFERTAS - EDICION (T4)
offers.edit=Editar oferta
offers.edit.title=Editar oferta
offers.edit.save=Guardar cambios
offers.edit.sourceLocked=La procedencia no se puede modificar.
offers.edit.notEditable=La oferta ya no est\u00E1 en estado REGISTRADO; no se puede editar.
offers.edit.conflict=Otro usuario modific\u00F3 la oferta mientras la editabas. Recarga e int\u00E9ntalo de nuevo.
offers.updated=Oferta actualizada correctamente.
```

**Reglas:**

- No inventes plurales: `java.text.MessageFormat` no soporta `plural` (lección de T3).
- Las claves que usa `#messages.msg(error)` deben existir **exactamente**; si falta, el
  detalle revienta con `NoSuchMessageException` (500).

**Cómo verificar:** `grep -c "offers.edit" src/main/resources/messages/messages_es.properties`
→ **6** claves (`offers.edit`, `.title`, `.save`, `.sourceLocked`, `.notEditable`, `.conflict`).
`offers.updated` no entra en ese `grep`: compruébala aparte.

---

## Paso 11 — Seguridad: matriz de T4

**Dónde:** `infrastructure/security/SecurityConfig.java`.

**Contrato:**

```java
// /offers/new tiene matcher propio: gana sobre /offers/**
.requestMatchers("/offers/new").hasAnyRole("REGISTRADOR", "ADMIN")
// /offers/*/edit cubre GET y POST: editar es curar, no publicar
.requestMatchers("/offers/*/edit").hasAnyRole("REGISTRADOR", "ADMIN")
.requestMatchers("/offers/*/approve", "/offers/*/reject").hasRole("REGISTRADOR")
```

| Ruta | Método | Anónimo | PUBLICADOR | REGISTRADOR | ADMIN |
|---|---|---|---|---|---|
| `/offers/{id}` | GET | 302 `/login` | 403 | 200 | 200 |
| `/offers/{id}/edit` | GET | 302 `/login` | 403 | 200 | 200 |
| `/offers/{id}/edit` | POST | 302 `/login` | 403 | 302 | 302 |

**Reglas:**

- **Un matcher cubre los dos métodos.** `/offers/*/edit` no distingue `GET` de `POST`; no
  hace falta duplicarlo.
- **`@PreAuthorize` de clase** (`hasAnyRole('REGISTRADOR','ADMIN')`, T3) sigue actuando
  como defensa en profundidad: aunque una regla de URL se relaje por error, el método no.
- **CSRF activo.** El `POST` de edición usa `th:action`, que inyecta el token; sin token,
  `403` (no `500`).
- **IDOR:** el `id` va en la ruta y el servicio lo usa para cargar; no hay forma de editar
  “otra” oferta cambiando el body.
- **Auditoría de intentos fallidos:** T4 registra la modificación **efectiva**. Un intento
  sobre una oferta ya aprobada se rechaza y se avisa, pero no genera `audit_event`; el
  catálogo de fallos se completará con `/audit` (T11) y el endurecimiento (T16).

**Cómo verificar:** `SecurityRulesTest` (Paso 12) y el smoke test del Paso 13.

---

## Paso 12 — Tests

**Objetivo:** cubrir la rebanada en sus tres costuras: servicio (Mockito), mapeo y web
(`@WebMvcTest`), más la matriz de acceso.

### 12.1 `UpdateJobOfferServiceTest` (Mockito)

Casos:

| Caso | Resultado esperado |
|---|---|
| oferta `REGISTERED` | campos actualizados; `save`; auditoría `UPDATE` con `old`/`new` |
| `id` inexistente | `JobNotFoundException`; sin `save`; sin auditoría |
| oferta `APPROVED` | `InvalidJobStateException`; sin `save`; sin auditoría; no muta |
| `content` o `actor` nulos | `NullPointerException`; sin interacciones |

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
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.evela.sigotrab.application.port.out.AuditPort;
import com.evela.sigotrab.application.port.out.JobOfferRepository;
import com.evela.sigotrab.domain.exception.InvalidJobStateException;
import com.evela.sigotrab.domain.exception.JobNotFoundException;
import com.evela.sigotrab.domain.model.ActorInfo;
import com.evela.sigotrab.domain.model.AuditAction;
import com.evela.sigotrab.domain.model.AuditRecord;
import com.evela.sigotrab.domain.model.JobOffer;
import com.evela.sigotrab.domain.model.JobOfferContent;
import com.evela.sigotrab.domain.model.NewJobOffer;

@ExtendWith(MockitoExtension.class)
class UpdateJobOfferServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-01T09:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();
    private static final ActorInfo ACTOR =
            new ActorInfo(UUID.randomUUID(), "registrador", "127.0.0.1");

    @Mock JobOfferRepository offers;
    @Mock AuditPort audit;

    private UpdateJobOfferService service;

    @BeforeEach
    void setUp() {
        service = new UpdateJobOfferService(offers, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // registeredOffer(), content(title) ...

    @Test
    void updatesRegisteredOfferAndAuditsOldAndNewValues() {
        JobOffer offer = registeredOffer();
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));

        JobOffer result = service.update(offer.getId(), content("Analista senior"), ACTOR);

        assertThat(result.getTitle()).isEqualTo("Analista senior");
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        assertThat(result.getUpdatedBy()).isEqualTo(ACTOR.userId());
        verify(offers).save(offer);

        var captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit).record(captor.capture());
        AuditRecord event = captor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.UPDATE);
        assertThat(event.oldValues()).containsEntry("title", "Analista");
        assertThat(event.newValues()).containsEntry("title", "Analista senior");
    }

    @Test
    void offerOutsideRegisteredIsRejectedWithoutSideEffects() {
        JobOffer approved = registeredOffer();
        approved.approve(OWNER, NOW);
        when(offers.findById(approved.getId())).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.update(approved.getId(), content("X"), ACTOR))
                .isInstanceOf(InvalidJobStateException.class);

        assertThat(approved.getTitle()).isEqualTo("Analista");
        verify(offers, never()).save(any());
        verifyNoInteractions(audit);
    }
}
```

> El archivo completo (con `unknownOfferThrowsNotFoundWithoutSideEffects` y
> `nullArgumentsAreRejected`) está en el repo: `UpdateJobOfferServiceTest` (4 tests).

### 12.2 `JobOfferEntityMapperTest` (+1)

```java
@Test
void copyToUpdatesManagedEntityWithoutTouchingIdOrVersion() {
    JobOffer d = /* oferta registrada */;
    JobOfferJpaEntity e = mapper.toEntity(d);
    e.setVersion(7L);   // entidad ya gestionada por Hibernate

    d.updateContent(/* contenido nuevo */, actor, NOW.plusSeconds(60));
    mapper.copyTo(e, d);

    assertThat(e.getId()).isEqualTo(d.getId());
    assertThat(e.getVersion()).isEqualTo(7L);
    assertThat(e.getTitle()).isEqualTo("Analista senior");
}
```

### 12.3 `OffersControllerTest` (+7 → 15)

| Test | Qué fija |
|---|---|
| `editFormIsPrefilledWithCurrentValues` | 200, vista `offers/form`, `editing`, `offerId`, `form` precargado |
| `editFormRedirectsWhenOfferIsNotRegistered` | 302 al detalle + flash `offers.edit.notEditable` |
| `editFormReturns404WhenOfferDoesNotExist` | 404 + `offers/not-found` |
| `updateRedirectsToDetailOnSuccess` | 302 al detalle + flash `updated` |
| `updateShowsFieldErrorsWhenRequiredDataIsMissing` | 200 con errores de campo, sin llamar al caso de uso |
| `updateRedirectsWhenOfferIsNoLongerRegistered` | `InvalidJobStateException` → 302 + flash |
| `updateRedirectsWhenAnotherUserChangedTheOffer` | `ObjectOptimisticLockingFailureException` → 302 + flash |

Tres detalles que cuestan un rato si no los sabes:

```java
// 1) El controlador ahora pide otro puerto: sin este mock, el contexto no arranca.
@MockitoBean UpdateJobOfferUseCase updateJobOffer;

// 2) Los records NO tienen getters JavaBean: hasProperty("title", ...) falla.
.andExpect(model().attribute("form", instanceOf(JobOfferEditForm.class)))
.andExpect(result -> {
    var form = (JobOfferEditForm) result.getModelAndView().getModel().get("form");
    assertThat(form.title()).isEqualTo("Analista");
});

// 3) Con @WithMockUser el principal no es SigUser: para POST usa .with(user(registrador())).
```

### 12.4 `SecurityRulesTest` (+3 → 10)

```java
@Test
void anonymousCannotOpenEditForm() throws Exception {
    mvc.perform(get("/offers/{id}/edit", UUID.randomUUID()))
       .andExpect(status().is3xxRedirection())
       .andExpect(redirectedUrl("/login"));
}

@Test
@WithMockUser(roles = "PUBLICADOR")
void publicadorCannotOpenEditForm() throws Exception {
    mvc.perform(get("/offers/{id}/edit", UUID.randomUUID()))
       .andExpect(status().isForbidden());
}

@Test
@WithMockUser(roles = "ADMIN")
void adminCanOpenEditForm() throws Exception {
    // Si /offers/*/edit queda en hasRole("REGISTRADOR"), este test da 403.
    when(getJobOffers.getById(offer.getId())).thenReturn(offer);

    mvc.perform(get("/offers/{id}/edit", offer.getId()))
       .andExpect(status().isOk());
}
```

### 12.5 Cómo verificar

```bash
cd sigotrab

# T4 sin base de datos
./mvnw -B test -Dtest="JobOfferTest,JobStatusTransitionTest,HexagonalArchitectureTest,RegisterJobOfferServiceTest,UpdateJobOfferServiceTest,JobOfferEntityMapperTest,OffersControllerTest,SecurityRulesTest"

# suite completa (necesita PostgreSQL por SigotrabApplicationTests; deuda T9)
./mvnw -B test
```

**Criterio:** los ocho grupos de T3/T4 en verde y **75 tests en la suite** (60 de T3 + 15
nuevos), con `HexagonalArchitectureTest` intacto.

---

## Paso 13 — Verificación manual (punta a punta)

**Objetivo:** comprobar que la edición **persiste** y que la auditoría guarda el diff.

1. Levanta la base y la app:

```bash
cd sigotrab
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

2. Entra como `registrador` / `registrador123`, crea una oferta y abre su detalle.
3. Pulsa **Editar oferta**: el formulario llega precargado; la sección “Procedencia”
   muestra fuente, referencia y fecha en **solo lectura**.
4. Cambia título y salario, guarda. Debes volver al detalle con
   “Oferta actualizada correctamente” y los datos nuevos.
5. Comprueba la fila y el evento:

```bash
docker exec postgres psql -U sigotrabuser -d sigotrabdb -c \
  "select title, salary, status, version, updated_at > created_at as newer from job_offer where id = '<uuid>';"

docker exec postgres psql -U sigotrabuser -d sigotrabdb -c \
  "select action, success, old_values->>'title' as old_title, new_values->>'title' as new_title
     from audit_event where entity_id = '<uuid>' order by id;"
```

**Resultado esperado:** `version = 1`, `newer = t` y dos eventos: `CREATE` y `UPDATE` con
`old_title` y `new_title` distintos.

6. Fuerza el estado para ver la guardia:

```bash
docker exec postgres psql -U sigotrabuser -d sigotrabdb -c \
  "update job_offer set status = 'APPROVED' where id = '<uuid>';"
```

Abre `/offers/<uuid>/edit`: debe redirigir al detalle con
“La oferta ya no está en estado REGISTRADO; no se puede editar.” y **sin** botón Editar.

7. Seguridad: como `publicador` / `publicador123`, `/offers/<uuid>/edit` → **403**; sin
   sesión → **302** a `/login`. Como `admin` / `admin123` → **200**.
8. XSS: guarda un título `<script>alert(1)</script>`; el detalle debe mostrarlo escapado
   (`&lt;script&gt;`) y el diálogo no debe abrirse.

> **Sobre el flash:** Spring consume el `FlashMap` en la **primera** petición cuyo path
> coincide con el destino del redirect. Si encadenas a mano dos flujos que redirigen a
> `/offers/{id}`, el primer `GET` puede comerse el aviso del flujo anterior (visto en el
> smoke test). En uso normal no pasa: el navegador sigue el `302` inmediatamente.

### 13.1 Checklist de accesibilidad de la edición

- El botón **Editar** es un `<a>` con texto (no solo un icono) y `aria-hidden` en el icono.
- Los avisos usan `role="status"` (éxito) y `role="alert"` (error).
- El formulario conserva `novalidate` + validación de servidor; los errores van con
  `aria-describedby` y texto, no solo color.
- La sección de procedencia en solo lectura usa `<dl>` (definición), no inputs
  deshabilitados que confundan al lector de pantalla.
- Responsive: la fila “volver / editar” y el formulario siguen apilándose por debajo de
  `992px` (mismas clases de T3).

---

## Paso 14 — Cierre de T4

```bash
cd sigotrab

# 1) tests de T3 + T4
./mvnw -B test -Dtest="JobOfferTest,JobStatusTransitionTest,HexagonalArchitectureTest,RegisterJobOfferServiceTest,UpdateJobOfferServiceTest,JobOfferEntityMapperTest,OffersControllerTest,SecurityRulesTest"

# 2) lo que corre CI
./mvnw -B verify

# 3) dominio puro
grep -RInE "^import (org\.springframework|jakarta\.persistence|org\.hibernate|org\.thymeleaf|lombok)" \
  src/main/java/com/evela/sigotrab/domain || echo "OK: dominio puro"
```

Luego, desde la raíz:

```bash
git diff --check
git status --short
git add sigotrab/src/main/java/com/evela/sigotrab/application \
        sigotrab/src/main/java/com/evela/sigotrab/adapters \
        sigotrab/src/main/java/com/evela/sigotrab/domain/model/AuditRecord.java \
        sigotrab/src/main/java/com/evela/sigotrab/infrastructure/security/SecurityConfig.java \
        sigotrab/src/main/resources/templates/offers \
        sigotrab/src/main/resources/messages/messages_es.properties \
        sigotrab/src/test/java/com/evela/sigotrab \
        docs/SIGO_Pasos_T4.md \
        AGENTS.md
git commit -m "feat: edición de ofertas con auditoría UPDATE (#T4)"
```

**Criterio:** la definición de “T4 terminado” del final.

---

## Apéndice A — Errores comunes en T4

| Síntoma | Causa probable | Solución |
|---|---|---|
| `duplicate key value violates unique constraint "job_offer_pkey"` al guardar | `save()` con id existente y `version = null` → `INSERT` | Upsert con `findById` + `copyTo` (Paso 5) |
| `ObjectOptimisticLockingFailureException` en cada edición | La entidad se reconstruye con versión vieja y otra petición la subió | Es la señal de que el bloqueo funciona: recarga y reintenta (Paso 8) |
| `version` no sube tras editar | Actualizaste con un `UPDATE` nativo sin `version = version + 1` | No uses SQL nativo aquí; `copyTo` + *dirty checking* |
| `EL1001E: Type conversion problem, cannot convert from null to boolean` | `${!editing}` con `editing` ausente | `editing != true` / `editing == true` y `editing=false` en `newForm` |
| `Model attribute 'form' ... hasProperty` falla | `hasProperty` necesita getters JavaBean; un record tiene `title()` | `instanceOf` + `assertThat(form.title())` |
| El formulario de edición muestra inputs de fuente/referencia | Guardas invertidas (`editing` en vez de `editing == true`) | Revisa los dos `<th:block>` del Paso 9 |
| `NoSuchMessageException: offers.edit.notEditable` | Falta la clave o el flash lleva el **texto** en vez de la clave | Añade la clave y pasa la clave a `#messages.msg` |
| El aviso de “actualizada” no aparece | El flash se consumió en una petición anterior al mismo path | Sigue el `302`; en pruebas manuales, no saltes el redirect |
| `403` como `ADMIN` en `/offers/{id}/edit` | El matcher sigue en `hasRole("REGISTRADOR")` | `hasAnyRole("REGISTRADOR","ADMIN")` (Paso 11) |
| `403` en el `POST` de edición | Falta CSRF (o `th:action` mal puesto) | Usa `th:action`; en tests `.with(csrf())` |
| `IllegalStateException: BindingResult` | `BindingResult` no va justo tras `@Valid` | Reordena los parámetros |
| `TemplateInputException: offers/form` | La plantilla no compila (ternario o guarda) | `@WebMvcTest` la renderiza: mira el log de Thymeleaf |
| El detalle no muestra el botón Editar | La oferta no está `REGISTERED` o falta `xmlns:sec` | Revisa estado y namespace |
| `sec:authorize` no hace nada | Falta `xmlns:sec` en `detail.html` | Añádelo en `<html>` |
| Se puede editar `sourceName` por POST manual | El formulario no lo pinta pero el dominio lo ignora | Correcto: `updateContent` no toca la procedencia |
| `Could not resolve view with name 'offers/edit'` | Creaste una plantilla nueva en vez de reutilizar `form.html` | Usa `offers/form` con `editing=true` |

---

## Apéndice B — Conceptos y por qué

### B.1 ¿Por qué la edición es un caso de uso y no un método del registro?

Porque tiene su propia transacción, su propia auditoría (`UPDATE` con `old/new`) y su
propio permiso. Meterlo en `RegisterJobOfferService` haría un servicio con dos motivos para
cambiar (SRP) y un `@Transactional` que mezcla alta y curación.

### B.2 ¿Por qué `old` y `new` completos y no solo el campo cambiado?

Porque reconstruir un cambio a posteriori exige cruzar eventos y no siempre es posible
(por ejemplo, si un campo pasa de `null` a valor). Guardar los dos snapshots de los ocho
campos mutables hace la auditoría **autosuficiente**: con un evento sabes cómo estaba y
cómo quedó.

### B.3 ¿Por qué la procedencia no se edita?

`source_name + external_reference` es la identidad de la oferta frente a la fuente
(deduplicación, §24) y `extracted_at` es un hecho histórico (“cuándo se copió”). Si se
pudieran cambiar, el `UNIQUE` dejaría de proteger nada y la trazabilidad se rompería.

### B.4 ¿Por qué el flash lleva una clave y no un texto?

Porque el texto vive en el bundle (i18n) y porque un mensaje construido con datos del
usuario en el controlador es una puerta a XSS si algún día se pinta con `th:utext`. La
plantilla resuelve la clave y la escapa con `th:text`.

### B.5 ¿Por qué `@Version` si el `UPDATE` ya es de una sola fila?

Porque dos `REGISTRADOR` pueden abrir la misma oferta y guardar a la vez: sin versión, el
segundo pisa al primero en silencio (*lost update*). Con versión, el segundo recibe
`ObjectOptimisticLockingFailureException` y la web le pide recargar.

### B.6 ¿Qué añadirán T5/T6?

| Ticket | Añade |
|---|---|
| T5 | `Approve`/`Reject` sobre `REGISTERED`, `StatusHistoryPort`, motivo obligatorio |
| T6 | `acquire` atómico (`UPDATE ... WHERE status = 'APPROVED'`), `409 JOB_ALREADY_LOCKED` |

---

## Definición de “T4 terminado”

**Dominio**

- [ ] `AuditRecord.success(...)` con `oldValues` y `newValues`; `AuditAction.UPDATE` usado.
- [ ] `JobOffer.updateContent` sigue siendo la única puerta de edición (sin setters).
- [ ] `domain/` sin Spring/JPA/Hibernate/Thymeleaf/Lombok.

**Aplicación**

- [ ] `UpdateJobOfferUseCase` y `UpdateJobOfferService` (`@Transactional`).
- [ ] Carga → snapshot → `updateContent` → `save` → auditoría `UPDATE` con `old/new`.
- [ ] `JobNotFoundException` e `InvalidJobStateException` se propagan sin `catch`.

**Persistencia**

- [ ] `copyTo` copia sin tocar `id` ni `version`.
- [ ] `save` es upsert: actualiza la entidad gestionada y conserva `@Version`.
- [ ] Tras editar, `version` sube y `updated_at > created_at`.

**Web**

- [ ] `GET /offers/{id}/edit` precarga y solo abre si `REGISTERED`.
- [ ] `POST /offers/{id}/edit` valida, delega y hace PRG al detalle.
- [ ] Errores de campo repintan el formulario; estado inválido y conflicto vuelven con aviso.
- [ ] Un solo `form.html` para alta y edición (`editing`, `offerId`).
- [ ] Detalle con avisos `updated`/`error` y botón Editar solo si procede.

**Seguridad**

- [ ] `/offers/*/edit` (GET y POST) para `REGISTRADOR` y `ADMIN`.
- [ ] `@PreAuthorize` de clase intacto; CSRF en el `POST`.
- [ ] `PUBLICADOR` → 403; anónimo → 302 `/login`.
- [ ] Título con `<script>` se muestra escapado.

**Auditoría**

- [ ] Un `UPDATE` por edición efectiva, con `old_values` y `new_values` en `jsonb`.
- [ ] La procedencia no aparece en el diff (no se edita).

**Tests**

- [ ] `UpdateJobOfferServiceTest` (4), `JobOfferEntityMapperTest` (4),
      `OffersControllerTest` (15), `SecurityRulesTest` (10).
- [ ] `./mvnw -B verify` en verde (75 tests con PostgreSQL arriba).
