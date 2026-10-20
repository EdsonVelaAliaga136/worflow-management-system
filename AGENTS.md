# AGENTS.md

## Layout
- The real app is the Maven project in `sigotrab/`. Run all Maven commands from there, not the repo root.
- `docs/` (repo root) is the source of truth, in Spanish:
  - `SIGO_Especificacion_Proyecto.md` — requirements.
  - `SIGO_Plan_Desarrollo.md` — stack, architecture, decisions, ticket order. Supersedes the spec where they conflict (§1).
  - `SIGO_Tutorial_Desarrollo.md` — step-by-step build + "errores comunes" appendix.
  - `SIGO_Guia_Aprendizaje.md` — build-along guide (concepts + why), for learning.
  - `SIGO_Pasos_T1.md` — step-by-step guide for T1 (BD + Flyway + login + dashboard); "you write / I guide" format.
  - `adr/` — decisions `0006`–`0012`; `GLOSSARY.md` at repo root.
- `opencode.jsonc` is empty. `.agents/skills/grill-me` is installed but broken: its SKILL.md only calls `grilling`, which is not installed.

## Current state
- **T1 is implemented** following `docs/SIGO_Pasos_T1.md`: Flyway `V1`/`V2`, JPA entities/repositories, Spring Security form login, `SeedDevUsers` (`dev`), `SeedAdminUser` (`lan`, ADR-0009), `login.html`/`dashboard.html` with logout, and the message bundle.
- Pending closure: create the T1 commit (working tree is dirty). `SeedAdminUser` only seeds when the DB has no users; `lan` requires `SIGOTRAB_ADMIN_PASSWORD` on first start.

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
- `./mvnw test` currently fails without a local PostgreSQL: the only test is `@SpringBootTest`, and no test profile / embedded-PG wiring exists. Plan §4.12 specifies Zonky `embedded-postgres` (dependency already in `pom.xml`); write its `EmbeddedPostgresSupport` before adding integration tests.
- Flyway `V1__baseline.sql` / `V2__seed_roles.sql` (plan §4.4) now exist; the `users`/`roles`/`user_roles` tables are mapped by entities and checked by `ddl-auto: validate`.

## Spring Boot 4 quirks (tutorial App. C)
- Starters are renamed per module: `spring-boot-starter-webmvc` (not `-web`), `spring-boot-starter-flyway`, and test starters like `spring-boot-starter-webmvc-test` / `-data-jpa-test` / `-security-test`.
- `@WebMvcTest` moved to `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`. Use the coordinates in plan §2/§4.2 rather than Boot 3 names.
- Form login needs a controller: `loginPage("/login")` only redirects, so a `LoginController` must map `GET /login` → `login` view (otherwise 404).
- `spring.messages.basename` was not honored in this setup; messages were wired with an explicit `MessageSource` bean (basename `messages/messages`, UTF-8).

## Conventions (plan §3, §10)
- Hexagonal: `domain/`, `application/{port/{in,out},service}`, `adapters/{web,persistence}`, `infrastructure/{security,scheduler,audit,config}`. Domain must not import Spring/JPA/Hibernate/Thymeleaf; ArchUnit is meant to enforce this (test not written yet).
- `@Transactional` only in `application`; controllers depend on `port/in`, never on JPA.
- Concurrency authority is the DB via the atomic `UPDATE ... WHERE status='APPROVED' OR (status='IN_PROGRESS' AND lock_expires_at < :now)`; never `synchronized`.
- Manual mappers + records. Lombok **is** used in JPA entities (`pom.xml` has the dependency + processor path; ADR-0012) but **not** in `domain/`.
- Schema changes via Flyway only; never `ddl-auto=update`.
- Spanish UI/messages, English code identifiers. Git: trunk-based, Conventional Commits.
