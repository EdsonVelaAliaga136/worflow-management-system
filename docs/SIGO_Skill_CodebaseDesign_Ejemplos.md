# SIGO — Skill `codebase-design`: casos de uso y ejemplos

> Guía práctica para usar la skill **`codebase-design`** dentro de SIGO. Resume su
> vocabulario y muestra ejemplos concretos de cómo invocarla y qué produce, aplicados al
> código y al dominio de este repositorio.
> Fuente: `.agents/skills/codebase-design/` (`SKILL.md`, `DEEPENING.md`, `DESIGN-IT-TWICE.md`).

---

## 1. ¿Para qué sirve?

Para diseñar **módulos profundos**: mucha funcionalidad detrás de una interfaz pequeña,
colocada en una costura limpia y testeable a través de esa interfaz. Si le pides a un agente
"¿cómo estructuro esto?", esta skill fuerza el vocabulario y las preguntas correctas.

**Cuándo usarla**
- Vas a diseñar (o rediseñar) la interfaz de un módulo.
- Quieres encontrar oportunidades de "profundizar" código superficial.
- No sabes dónde poner la costura (¿puerto? ¿adapter?).
- Quieres que el código sea más testeable o más navegable.

**Cómo se invoca** (frases que la activan):
- "Diseña la interfaz de `<módulo>`."
- "¿Dónde pongo la costura entre el caso de uso y la persistencia?"
- "Encuentra oportunidades de profundizar en `<paquete>`."
- "Haz *design it twice* para la adquisición de ofertas."

---

## 2. Vocabulario (úsalo exacto)

| Término | Qué es | Evita |
|---|---|---|
| **Módulo** | Algo con interfaz e implementación (función, clase, paquete, rebanada). | unit, componente, servicio |
| **Interfaz** | Todo lo que un llamador debe saber: firma, invariantes, orden, errores, config, rendimiento. | API, "signatura" (demasiado estrecho) |
| **Implementación** | El cuerpo del módulo. Distinto de **Adapter** (rol, no sustancia). | — |
| **Profundidad** | Apalancamiento en la interfaz: comportamiento por unidad de interfaz aprendida. | — |
| **Costura (seam)** | Lugar donde cambias comportamiento sin editar ahí; donde vive la interfaz. | boundary (se confunde con DDD) |
| **Adapter** | Cosa concreta que satisface una interfaz en una costura. | — |
| **Leverage** | Lo que gana el llamador con la profundidad. | — |
| **Locality** | Lo que gana el mantenedor: cambio/bug/conocimiento concentrado. | — |

**Módulo profundo** = interfaz pequeña + mucha implementación (bien).
**Módulo superficial** = interfaz grande + poca implementación (evitar; suele ser pass-through).

Preguntas clave al diseñar una interfaz: *¿menos métodos? ¿parámetros más simples? ¿puedo
esconder más complejidad dentro?*

---

## 3. Caso 1 — Profundizar el caso de uso de adquisición

**Contexto**: T6 define `AcquireJobOfferUseCase.acquire(UUID jobOfferId, ActorInfo actor)`.
Detrás hay: cálculo de `now`/`expiresAt` con `Clock`, `UPDATE` atómico, auditoría del fallo,
y `JobLockedException` → 409.

**Invocación**:
> "Usa `codebase-design`. ¿Es `AcquireJobOfferUseCase` un módulo profundo? Diseña su interfaz."

**Produce**:
```
┌──────────────────────────────────────────────┐
│ acquire(jobOfferId, actor) -> JobOffer        │  ← interfaz pequeña
├──────────────────────────────────────────────┤
│ Clock + LockProperties + UPDATE atómico        │  ← implementación oculta
│ + auditoría (éxito/fallo) + historial          │
└──────────────────────────────────────────────┘
```
- Interfaz: 1 método, 2 parámetros. Profundidad alta.
- Lo que esconde: la transacción, la concurrencia, la auditoría, el mapeo de errores.
- Costura: el puerto `JobOfferRepository.acquire(...)` es interno (lo usan el caso de uso y
  su test), no se expone al llamador.

**Deletion test**: si borras el caso de uso, la lógica de bloqueo+auditoría reaparecería en
cada controlador. → *Earning its keep.*

---

## 4. Caso 2 — Decidir dónde va una costura (puerto sí o no)

**Contexto**: `JobOfferRepository`. Producción = Postgres; test = **otro adapter** (fake o
`@DataJpaTest` con PG embebido).

**Invocación**:
> "Aplica `codebase-design`: ¿esta dependencia merece un puerto o es indirección?"

**Regla que aplica la skill**: *un adapter es una costura hipotética; dos adapters son una
costura real.* Aquí hay dos (Postgres + doble de test) ⇒ **sí** merece puerto.
Contraejemplo: un `Clock` se justifica igual (real + `Clock.fixed` en tests), pero un
`JobOfferMapper` con un solo uso **no** merece interfaz.

---

## 5. Caso 3 — Hacer testeable el dominio (aceptar, no crear)

**Contexto**: `JobOffer.acquire(userId, lockDuration, now)`.

**Invocación**:
> "Con `codebase-design`, revisa la testabilidad de `JobOffer`."

**Aplica los tres principios de testabilidad**:
1. **Acepta dependencias, no las creas**: `now` entra por parámetro; nada de `Instant.now()`
   dentro. El `Clock` se inyecta en la capa `application`.
2. **Devuelve resultados, no efectos**: `canTransitionTo(target)` es una función pura;
   `transitionTo(...)` cambia estado de forma controlada y valida invariantes.
3. **Superficie pequeña**: pocos métodos públicos (`approve/reject/acquire/release/publish`).

---

## 6. Caso 4 — Classificar dependencias antes de profundizar

Usa `DEEPENING.md` para clasificar la dependencia del clúster y decidir cómo se prueba:

| Categoría | Ejemplo en SIGO | Estrategia |
|---|---|---|
| In-process | `JobStatus.canTransitionTo` | Fusionar y probar directo; sin adapter. |
| Local-substitutable | Postgres | `@DataJpaTest` con PG embebido; costura interna. |
| Remota pero propia | — (SIGO es monolito) | Puerto + adapter HTTP/gRPC (futuro). |
| Externa real | Fuentes/portal institucional (futuro) | Puerto inyectado + mock adapter. |

**Disciplina de costura**: no expongas una costura interna solo porque tu test la usa.

---

## 7. Caso 5 — *Design It Twice* sobre la adquisición

Cuando la primera interfaz probablemente no sea la mejor, `DESIGN-IT-TWICE.md` lanza 3+
sub-agentes en paralelo, cada uno con una restricción distinta:
- Agente 1: minimizar la interfaz (1–3 puntos de entrada).
- Agente 2: maximizar flexibilidad.
- Agente 3: optimizar el caso común (que adquirir sea trivial).
- Agente 4: ports & adapters para dependencias cruzadas.

**Invocación**:
> "Design it twice para `AcquireJobOfferUseCase`; quiero 3 interfaces radicalmente distintas."

**Comparación** que produce: por **profundidad** (leverage), **localidad** (dónde se
concentra el cambio) y **colocación de la costura**. Termina con **una recomendación
opinada**, no un menú.

---

## 8. Anti-patrones que la skill te hace ver

- **Pass-through**: una capa que solo reenvía ⇒ bórrala (deletion test).
- **Interfaz filtrada**: exponer `JobOfferJpaEntity` al controlador ⇒ el llamador aprende
  detalles de JPA.
- **Costura de un solo adapter**: puerto que solo tiene la implementación de producción ⇒
  indirección sin valor.
- **Testear más allá de la interfaz**: tests que verifican estado interno y se rompen al
  refactorizar ⇒ están midiendo implementación, no comportamiento.

---

## 9. Mini-checklist para pegar en el PR

- [ ] Interfaz pequeña: ¿cuántos métodos? ¿cuántos parámetros?
- [ ] Invariantes, orden, errores y config **documentados en la interfaz**.
- [ ] ¿La complejidad está **dentro**, no repartida entre llamadores? (deletion test)
- [ ] ¿Hay **≥ 2 adapters** justificando cada costura?
- [ ] ¿Se prueba **a través de la interfaz**, sin espiar detalles internos?
- [ ] Dependencias **inyectadas** (Clock, repos), no creadas dentro.
