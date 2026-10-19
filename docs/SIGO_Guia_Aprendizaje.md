# SIGO — Guía de aprendizaje (construir y entender)

> Companion de `SIGO_Tutorial_Desarrollo.md`, pero pensada para **aprender**: aquí
> explicamos el *por qué* de cada paso, cómo verificar que funcionó y qué hacer cuando algo
> falla. Sigue el mismo orden de tickets (`SIGO_Plan_Desarrollo.md` §5).
> Estado actual: **solo andamiaje (T0)**. Lo de abajo describe lo que construiremos en T1
> paso a paso; úsalo como mapa mientras lo hacemos juntos.

---

## 1. La idea en una frase

SIGO separa **qué hace el negocio** (centro) de **cómo llega por web o se guarda en
PostgreSQL** (bordes). Eso es la *arquitectura hexagonal*: el `domain/` es el centro puro,
`application/` orquesta los casos de uso y `adapters/` + `infrastructure/` son los bordes.

Por eso el `pom.xml` **no** mete Spring dentro de `domain/`: si mañana cambias PostgreSQL
por otro motor, el centro no se toca.

---

## 2. Conceptos que ejercita T1

| Concepto | Dónde lo ves | Por qué importa |
|---|---|---|
| Perfiles (`dev`/`lan`) | `application*.yml`, `@Profile` | Misma app, configuración distinta por entorno. |
| Flyway | `db/migration/V1…`, `V2…` | El esquema se versiona con SQL; nunca `ddl-auto=update`. |
| Spring Security | `SecurityConfig`, `SigUserDetailsService` | Autenticación (quién eres) + autorización (qué puedes). |
| Hashing de contraseñas | `PasswordEncoder` (BCrypt) | Nunca se guarda la contraseña en claro. |
| `ApplicationRunner` | `SeedDevUsers` | Sembrar datos al arrancar sin meter hashes en SQL. |
| Thymeleaf + layout | `templates/layout.html` | Una plantilla base reutilizable para todas las páginas. |
| WebJars | `pom.xml`, `layout.html` | Bootstrap viaja dentro del `.jar` (funciona sin internet/LAN). |
| `MessageSource` | `MessageConfig`, `messages_es.properties` | Textos de UI en español, separados del código. |

---

## 3. Levantar el proyecto (dev)

```bash
# 1) Base de datos (usa el servicio Docker; ADR-0006)
cd sigotrab/src/main/resources/docker
docker compose up -d --build          # crea sigotrab-db, postgres:17
docker compose ps                     # debe decir "healthy"

# 2) La aplicación
cd ../../../..
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Abre http://localhost:8080 y entra con `registrador` / `registrador123`.

**Cómo verificar de verdad** (no a ojo):
```bash
docker exec sigotrab-db psql -U sigotrab -d sigotrab -c "\dt"
# ↑ 7 tablas: roles, users, user_roles, job_offer,
#   job_offer_status_history, audit_event, flyway_schema_history
```

---

## 4. Recorrido del código de T1 (léelo en este orden)

1. `SigotrabApplication` — el arranque. Aquí vive el `@SpringBootApplication`.
2. `db/migration/V1__baseline.sql` — el esquema. Fíjate en los `CHECK` (invariantes) y en
   `unique (source_name, external_reference)` (dedupe, spec §24).
3. `db/migration/V2__seed_roles.sql` — los tres roles.
4. `adapters/persistence/entity/*JpaEntity` — cómo una tabla se vuelve objeto.
5. `infrastructure/security/SigUserDetailsService` — cómo un `username` se vuelve un
   usuario con roles para Spring Security.
6. `infrastructure/security/SecurityConfig` — las reglas de URL y el `PasswordEncoder`.
7. `infrastructure/config/SeedDevUsers` — los usuarios demo.
8. `adapters/web/controller/LoginController` + `DashboardController` — las rutas web.
9. `templates/layout.html` → `login.html` / `dashboard.html` — la vista.

**Ejercicio de lectura**: sigue una petición `GET /dashboard`.
`SecurityConfig` decide si estás autenticado → `DashboardController` devuelve `"dashboard"`
→ Thymeleaf monta `dashboard` dentro de `layout`.

---

## 5. Problemas reales que aparecieron (y cómo se resolvieron)

Estos son los "errores comunes" que de verdad ocurren; apúntalos.

### 5.1 `GET /login` daba 404
`loginPage("/login")` **solo redirige**; no crea la página. Hay que mapear la vista:
```java
@Controller
class LoginController {
    @GetMapping("/login") String login() { return "login"; }
}
```
> Lección: Spring Security configura el *filtro*, no la *vista*.

### 5.2 Los textos salían como `??login.title_es_BO??`
El `ResourceBundle` sí encontraba `messages/messages_es.properties`, pero la app no lo
resolvía con `spring.messages.basename`. Se arregló declarando el bean explícito en
`MessageConfig` (`basename "messages/messages"`, UTF-8).
> Lección: cuando la autoconfiguración "no obedece", define el bean tú y sigues.

### 5.3 `BUILD FAILURE` con Lombok
El `pom.xml` tenía un `annotationProcessorPath` de Lombok **sin** la dependencia Lombok.
Se eliminó ese plugin: mappers manuales, sin Lombok (plan D11).
> Lección: un `annotationProcessorPaths` sin versión/dependencia rompe el build.

### 5.4 "No hay ningún atributo de manifiesto principal"
`java -jar` falló porque el build anterior no terminó el *repackage* (el `.jar` estaba
bloqueado por una instancia anterior aún viva). Solución: matar el proceso Java correcto y
reconstruir.
> Lección en Windows/Git Bash: `taskkill //F //PID <pid>` mata el proceso, pero el PID del
> *shell* no es el del JVM. Usa `jps -l` para ver el PID real del `.jar`.

---

## 6. Cómo se ve que T1 quedó bien

- [x] La app arranca y Flyway aplica V1 y V2.
- [x] `/login` responde 200 con el formulario.
- [x] Login correcto → 302 a `/dashboard` (200).
- [x] Login incorrecto → 302 a `/login?error`.
- [x] `/dashboard` sin sesión → 302 a `/login`.
- [x] Los textos se ven en español (no `??clave??`).
- [x] Existen `admin`, `registrador`, `publicador` con sus roles.

---

## 7. Siguiente paso (T2)

El centro del sistema: `JobStatus` (enum con `canTransitionTo`) y el agregado `JobOffer`
con sus invariantes, **sin Spring**. Se prueba con JUnit puro y ArchUnit vigila que
`domain/` no importe frameworks.

> Regla de oro para avanzar: **primero el dominio, después el caso de uso, al final la
> web**. Cada ticket añade una rebanada vertical (dominio + aplicación + web + test).
