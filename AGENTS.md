# AGENTS.md

## Layout
- The real app is the Maven project in `sigotrab/`. Run all Maven commands from there, not the repo root.
- `docs/` (repo root) is the source of truth, in Spanish:
  - `SIGO_Especificacion_Proyecto.md` — requirements.
  - `SIGO_Plan_Desarrollo.md` — stack, architecture, decisions, ticket order. Supersedes the spec where they conflict (§1).
  - `SIGO_Tutorial_Desarrollo.md` — step-by-step build + "errores comunes" appendix.
  - `SIGO_Guia_Aprendizaje.md` — build-along guide (concepts + why), for learning.
  - `SIGO_Pasos_T1.md` — step-by-step guide for T1 (BD + Flyway + login + dashboard); "you write / I guide" format.
  - `SIGO_Pasos_T2.md` — step-by-step guide for T2 (domain lifecycle + tests + ArchUnit); "you write / I guide" format.
  - `SIGO_Pasos_T3.md` — step-by-step guide for T3 (register offer: ports, persistence, audit, web); "you write / I guide" format.
  - `SIGO_Pasos_T4.md` — step-by-step guide for T4 (edit/curate offer: `UPDATE` audit with old/new, optimistic locking); "you write / I guide" format.
  - `adr/` — decisions `0006`–`0012`; `GLOSSARY.md` at repo root.
- `opencode.jsonc` is empty. `.agents/skills/` ships the project skills (`grilling`, `grill-me`, `code-review`, `tdd`, `implement`, …); `grill-me` delegates to `grilling`, which is installed.

## Current state
- **T1 and T2 are implemented and committed** (`4aa7f84` T1, `bc1f660` T2).
  - T1: Flyway `V1`/`V2`, JPA users/roles, Spring Security form login, `SeedDevUsers` (`dev`), `SeedAdminUser` (`lan`, ADR-0009), `login.html`/`dashboard.html`, i18n bundle.
  - T2: pure `domain/` (`JobOffer`, `JobStatus`, `NewJobOffer`, `JobOfferContent`, six exceptions), `JobOfferTest`, `JobStatusTransitionTest` and `HexagonalArchitectureTest`.
- **T3 is implemented** (`docs/SIGO_Pasos_T3.md` is the guide), pending commit. Domain (`reconstitute`, `ActorInfo`, `AuditRecord`, `DuplicateJobOfferException`), ports/services, JPA entity/repo/mapper/adapter, `JpaAuditAdapter` (Jackson 3 `tools.jackson`), `OffersController` (`@PreAuthorize`, global duplicate error), `SecurityConfig`, the full `app.css` theme, `layout.html` (navbar hidden for anonymous) and `offers/list|form|detail|not-found.html`.
- T3 tests: `RegisterJobOfferServiceTest`, `JobOfferEntityMapperTest`, `OffersControllerTest` (8) and `SecurityRulesTest` (7). `./mvnw -B test` is green (60 tests) when PostgreSQL is up, including `SigotrabApplicationTests` (Flyway + `ddl-auto: validate`).
- Two pitfalls found while verifying T3 (already fixed in guide + code): `java.text.MessageFormat` does **not** support `plural` (use `offers.total.one`/`.other` + ternary); `LoginUrlAuthenticationEntryPoint` redirects to the **relative** `/login`, so tests assert `redirectedUrl("/login")`, not `**/login`.
- **T4 is implemented** (`docs/SIGO_Pasos_T4.md` is the guide), pending commit. `UpdateJobOfferUseCase`/`UpdateJobOfferService` (snapshot → `updateContent` → `save` → audit `UPDATE` with `old/new`), `AuditRecord.success(..., old, new)`, `JobOfferEditForm` + `toContent`/`toEditForm`, `GET|POST /offers/{id}/edit`, one `form.html` with an `editing` flag (source fields read-only on edit), detail with `updated`/`error` flashes and an Edit button only for `REGISTERED`, `/offers/*/edit` for `REGISTRADOR`+`ADMIN`.
- T4 persistence fix: `JobOfferPersistenceAdapter.save` is now an upsert (`findById` + `JobOfferEntityMapper.copyTo`) because saving a detached entity with an assigned id and `version = null` can INSERT or skip `@Version`. Real edit bumps `version` 0→1 (verified against PostgreSQL).
- T4 tests: `UpdateJobOfferServiceTest` (4), `JobOfferEntityMapperTest` (4), `OffersControllerTest` (15), `SecurityRulesTest` (10) → **75 tests** with `./mvnw -B verify` green.
- `OffersControllerTest` pins the dual-mode of `form.html`: `newFormRenders` asserts the `sourceName`/`extractedAt` inputs exist, `editFormIsPrefilledWithCurrentValues` asserts they do **not** (read-only `<dl>`). A broken `editing` guard fails a test, not only the browser.
- Pending debt: persistence tests (`@DataJpaTest` for `JobOfferPersistenceAdapter`/`JpaAuditAdapter`) wait for `EmbeddedPostgresSupport` (T9); T4 edits are verified manually (Paso 13 of the guide) until then.
- `SeedAdminUser` only seeds when the DB has no users; `lan` requires `SIGOTRAB_ADMIN_PASSWORD` on first start. Demo users (`dev`): `admin/admin123`, `registrador/registrador123`, `publicador/publicador123`.

## Docs-vs-code mismatches (do not "fix" toward the docs)
- Package/groupId are `com.evela.sigotrab`; plan/spec consistently say `com.sigotrab`. Follow the code.
- Config already normalized per Q2: `application.yml`/`-dev.yml`/`-lan.yml` and `messages/` (plural) are in place.
- App DB is `sigotrabdb` / `sigotrabuser@localhost:5432`; `application*.yml`, `docker-compose.yml`, `.env` and docs use the same names. (ADR-0006 wants `postgres:17`; the `.env` currently uses `16-alpine` — version debt, not name.)
- `docker/` lives at `sigotrab/docker/` (not `src/main/resources`); `.env` is ignored by `sigotrab/.gitignore`, and `.env.example` is the template. Never add real secrets.

## Build / run / test (from `sigotrab/`)
```
./mvnw -B verify                                        # what CI runs
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # 2-min lock, seeds demo users
./mvnw test -Dtest="Job*Test"                           # single/focused tests
./mvnw -DskipTests package                              # → target/sigotrab-0.0.1-SNAPSHOT.jar
java -jar target/sigotrab-0.0.1-SNAPSHOT.jar --spring.profiles.active=lan
```
- Prereqs: Java 25 + PostgreSQL (17/18). Create the DB first: `CREATE ROLE sigotrabuser LOGIN PASSWORD 'change-me'; CREATE DATABASE sigotrabdb OWNER sigotrabuser;`
- `./mvnw test` still fails without a local PostgreSQL because `SigotrabApplicationTests` is a `@SpringBootTest`. Focused domain tests run without a DB: `./mvnw test -Dtest="Job*Test,HexagonalArchitectureTest"`. Plan §4.12 specifies Zonky `embedded-postgres` (dependency already in `pom.xml`); write `EmbeddedPostgresSupport` before adding `@DataJpaTest`/integration tests (debt for T9).
- Flyway `V1__baseline.sql` / `V2__seed_roles.sql` (plan §4.4) exist. `users`/`roles`/`user_roles` are mapped today; `job_offer`, `job_offer_status_history` and `audit_event` already exist and are used from T3. All are checked by `ddl-auto: validate`.

## Spring Boot 4 quirks (tutorial App. C)
- Starters are renamed per module: `spring-boot-starter-webmvc` (not `-web`), `spring-boot-starter-flyway`, and test starters like `spring-boot-starter-webmvc-test` / `-data-jpa-test` / `-security-test`.
- `@WebMvcTest` moved to `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`. Use the coordinates in plan §2/§4.2 rather than Boot 3 names.
- Form login needs a controller: `loginPage("/login")` only redirects, so a `LoginController` must map `GET /login` → `login` view (otherwise 404).
- `spring.messages.basename` was not honored in this setup; messages were wired with an explicit `MessageSource` bean (basename `messages/messages`, UTF-8).

## Conventions (plan §3, §10)
- Hexagonal: `domain/`, `application/{port/{in,out},service}`, `adapters/{web,persistence}`, `infrastructure/{security,scheduler,audit,config}`. Domain must not import Spring/JPA/Hibernate/Thymeleaf; `HexagonalArchitectureTest` (ArchUnit) enforces it.
- `@Transactional` only in `application`; controllers depend on `port/in`, never on JPA.
- Concurrency authority is the DB via the atomic `UPDATE ... WHERE status='APPROVED' OR (status='IN_PROGRESS' AND lock_expires_at < :now)`; never `synchronized`.
- Manual mappers + records. Lombok **is** used in JPA entities (`pom.xml` has the dependency + processor path; ADR-0012) but **not** in `domain/`.
- Schema changes via Flyway only; never `ddl-auto=update`.
- Spanish UI/messages, English code identifiers. Git: trunk-based, Conventional Commits.
