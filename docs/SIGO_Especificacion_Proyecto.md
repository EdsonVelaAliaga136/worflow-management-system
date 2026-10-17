# SIGO — Sistema de Gestión y Organización de Ofertas Laborales

## Nivel: Intermedio · Arquitectura Limpia / Hexagonal (Ports and Adapters)

Sistema web para el **registro, curación, validación, asignación temporal, seguimiento y preparación para publicación de ofertas laborales**, incorporando control de concurrencia y auditoría integral de las operaciones.

---

# 1. Descripción del proyecto

**SIGO** es un sistema web diseñado inicialmente como un **demo instalable en una sola computadora**, utilizando una arquitectura preparada para evolucionar posteriormente a una implementación dentro de una red LAN institucional.

El sistema funcionará como un repositorio y flujo de trabajo intermedio para gestionar ofertas laborales obtenidas desde fuentes externas.

Su objetivo principal es evitar que varios funcionarios intenten publicar manualmente la misma oferta laboral de manera simultánea.

El sistema permitirá:

- Registrar ofertas laborales encontradas en fuentes externas.
- Validar y depurar la información.
- Aprobar ofertas antes de ponerlas disponibles para publicación.
- Asignar temporalmente una oferta a un funcionario.
- Evitar que dos funcionarios procesen simultáneamente la misma oferta.
- Liberar automáticamente ofertas abandonadas.
- Confirmar la publicación manual en el portal institucional.
- Mantener trazabilidad completa de las operaciones.
- Generar reportes sobre las ofertas y actividades realizadas.

---

# 2. Objetivo general

Desarrollar una aplicación web centralizada que permita gestionar el ciclo de vida de ofertas laborales, desde su registro inicial hasta su publicación, garantizando:

- Integridad de los datos.
- Control de concurrencia.
- Separación de responsabilidades.
- Seguridad basada en roles.
- Auditoría integral.
- Trazabilidad.
- Persistencia confiable.
- Facilidad de mantenimiento y evolución.

---

# 3. Alcance inicial

La primera versión será un **demo ejecutado en una sola computadora**.

La arquitectura estará preparada para que posteriormente pueda utilizarse desde varios equipos conectados a una LAN institucional.

## Implementación inicial

```text
Computadora
│
├── Java 25
├── Spring Boot
├── Thymeleaf
├── Bootstrap
└── PostgreSQL
```

El usuario podrá acceder mediante:

```text
http://localhost:8080
```

La futura implementación LAN podrá utilizar:

```text
http://IP-SERVIDOR:8080
```

sin modificar las reglas principales del dominio.

---

# 4. Objetivos específicos

1. Implementar el registro estructurado de ofertas laborales.
2. Implementar un flujo controlado de estados.
3. Separar las responsabilidades entre registradores y publicadores.
4. Implementar control de concurrencia a nivel de base de datos.
5. Implementar bloqueos temporales.
6. Implementar liberación automática por expiración.
7. Mantener historial de cambios de estado.
8. Registrar auditoría integral de operaciones.
9. Implementar autenticación y autorización.
10. Generar reportes básicos.
11. Implementar pruebas unitarias.
12. Implementar pruebas de concurrencia.
13. Mantener el dominio independiente de frameworks e infraestructura.

---

# 5. Usuarios y roles

El sistema tendrá inicialmente los siguientes roles:

## 5.1. REGISTRADOR

Responsable de ingresar y validar las ofertas laborales.

Permisos:

- Registrar ofertas.
- Editar ofertas en estado `REGISTERED`.
- Revisar información.
- Aprobar ofertas.
- Rechazar ofertas.
- Consultar historial de ofertas registradas.

---

## 5.2. PUBLICADOR

Responsable de realizar la publicación manual en el portal institucional.

Permisos:

- Consultar ofertas `APPROVED`.
- Adquirir una oferta.
- Visualizar ofertas asignadas.
- Cancelar una asignación.
- Confirmar publicación.
- Consultar ofertas publicadas.

---

## 5.3. ADMIN

Responsable de la administración general.

Permisos:

- Gestionar usuarios.
- Gestionar roles.
- Consultar ofertas.
- Consultar auditoría.
- Consultar reportes.
- Configurar parámetros del sistema.
- Consultar métricas generales.

---

# 6. Ciclo de vida de una oferta

Los estados principales serán:

```text
REGISTERED
APPROVED
IN_PROGRESS
PUBLISHED
```

Opcionalmente se podrá incorporar:

```text
REJECTED
CANCELLED
```

## Flujo principal

```text
                 ┌──────────────┐
                 │  REGISTERED  │
                 └──────┬───────┘
                        │
                     Aprobar
                        │
                        ▼
                 ┌──────────────┐
                 │   APPROVED   │
                 └──────┬───────┘
                        │
                     Adquirir
                        │
                        ▼
                 ┌──────────────┐
                 │ IN_PROGRESS  │
                 └──────┬───────┘
                        │
                     Publicar
                        │
                        ▼
                 ┌──────────────┐
                 │  PUBLISHED   │
                 └──────────────┘
```

En caso de expiración:

```text
IN_PROGRESS
     │
     │ Timeout
     ▼
APPROVED
```

---

# 7. Reglas de transición

Las transiciones permitidas serán estrictas.

```text
REGISTERED → APPROVED
APPROVED → IN_PROGRESS
IN_PROGRESS → PUBLISHED
IN_PROGRESS → APPROVED
```

Opcionalmente:

```text
REGISTERED → REJECTED
```

No se permitirá realizar transiciones arbitrarias.

Ejemplos:

```text
REGISTERED → PUBLISHED       ❌
REGISTERED → IN_PROGRESS     ❌
APPROVED → PUBLISHED         ❌
PUBLISHED → APPROVED         ❌
PUBLISHED → IN_PROGRESS      ❌
```

Las reglas deberán ser validadas en el **dominio**, no únicamente en la interfaz web.

---

# 8. Requerimientos funcionales

## RF-01 — Registro de ofertas laborales

El sistema deberá permitir al usuario con rol `REGISTRADOR` ingresar una nueva oferta laboral.

Los datos mínimos serán:

- Título.
- Empresa.
- Descripción.
- Requisitos.
- Ubicación.
- Salario.
- Tipo de contratación.
- URL de origen.
- Fecha de extracción.

Al registrarse, la oferta tendrá automáticamente el estado:

```text
REGISTERED
```

---

## RF-02 — Curación de ofertas

El `REGISTRADOR` podrá revisar y modificar la información registrada.

La finalidad será:

- Corregir errores.
- Completar información.
- Normalizar datos.
- Eliminar información incorrecta.
- Verificar la fuente original.

Toda modificación deberá generar un evento de auditoría.

---

## RF-03 — Aprobación

El `REGISTRADOR` podrá aprobar una oferta que haya sido revisada.

Transición:

```text
REGISTERED → APPROVED
```

Una oferta `APPROVED` estará disponible para los usuarios `PUBLICADOR`.

---

## RF-04 — Rechazo

Opcionalmente, el registrador podrá rechazar una oferta que no cumpla las condiciones necesarias.

Transición:

```text
REGISTERED → REJECTED
```

El rechazo deberá registrar obligatoriamente un motivo.

---

## RF-05 — Adquisición de una oferta

Cuando un `PUBLICADOR` seleccione una oferta `APPROVED`, el sistema deberá intentar adquirirla de manera atómica.

Transición:

```text
APPROVED → IN_PROGRESS
```

La oferta deberá registrar:

- Usuario asignado.
- Fecha/hora de adquisición.
- Fecha/hora de expiración.

---

## RF-06 — Control de concurrencia

El cambio a `IN_PROGRESS` deberá realizarse de forma atómica en la base de datos.

Si dos usuarios intentan adquirir la misma oferta simultáneamente:

```text
Usuario A ──┐
            ├──► Oferta #1001
Usuario B ──┘
```

solo uno podrá adquirirla.

Resultado esperado:

```text
Usuario A → ÉXITO
Usuario B → CONFLICTO
```

El segundo usuario deberá recibir una respuesta controlada indicando que la oferta ya está siendo procesada.

---

## RF-07 — Bloqueo temporal

Una oferta `IN_PROGRESS` quedará temporalmente asignada al usuario que la adquirió.

Durante este periodo:

- Otros publicadores no podrán adquirirla.
- Solo el usuario asignado podrá continuar el proceso.
- El sistema deberá mostrar el tiempo restante.

Duración configurable:

```text
Demo: 2 minutos
Producción: 20 minutos
```

---

## RF-08 — Timeout automático

Si una oferta permanece en `IN_PROGRESS` después de su fecha de expiración, deberá regresar automáticamente a:

```text
APPROVED
```

Se deberá limpiar la información del bloqueo:

```text
assigned_user_id = NULL
locked_at = NULL
lock_expires_at = NULL
```

El sistema deberá registrar la liberación como una operación realizada por:

```text
SYSTEM
```

---

## RF-09 — Cancelación manual

El usuario asignado podrá cancelar el procesamiento.

Transición:

```text
IN_PROGRESS → APPROVED
```

La operación deberá registrar:

- Usuario.
- Fecha/hora.
- Motivo de cancelación.

---

## RF-10 — Confirmación de publicación

Una vez que el funcionario haya cargado manualmente la oferta en el portal institucional, podrá confirmar la publicación.

Transición:

```text
IN_PROGRESS → PUBLISHED
```

Se registrará:

- Usuario publicador.
- Fecha/hora de publicación.
- Fecha/hora de confirmación.
- Auditoría correspondiente.

---

## RF-11 — Auditoría integral

**Toda operación de creación, modificación, eliminación lógica, asignación, liberación, cambio de estado, aprobación, rechazo, publicación, autenticación y operación administrativa deberá generar un registro de auditoría cuando corresponda.**

La auditoría deberá permitir identificar:

- Usuario.
- Fecha/hora.
- Acción.
- Entidad.
- Identificador del registro.
- Resultado.
- Dirección IP cuando esté disponible.
- Valores anteriores.
- Valores posteriores.
- Motivo de la operación.

---

# 9. Auditoría

La auditoría será transversal al sistema.

Se utilizará una entidad:

```text
AUDIT_EVENT
```

Estructura conceptual:

```text
AUDIT_EVENT
--------------------------------
id
timestamp

user_id
username

action

entity_type
entity_id

old_values
new_values

ip_address

success
error_code
```

Los campos:

```text
old_values
new_values
```

podrán almacenarse como `JSONB` en PostgreSQL.

---

# 10. Acciones auditables

El catálogo inicial podrá incluir:

```text
CREATE
UPDATE
DELETE
LOGIN
LOGOUT

APPROVE
REJECT

ACQUIRE
RELEASE
TIMEOUT_RELEASE

PUBLISH
UNPUBLISH

ASSIGN
REASSIGN
```

No todas las acciones serán aplicables a todas las entidades.

---

# 11. Historial de estados

La auditoría general no reemplazará el historial específico de estados.

Se utilizará:

```text
JOB_OFFER_STATUS_HISTORY
```

Estructura:

```text
JOB_OFFER_STATUS_HISTORY
--------------------------------
id
job_offer_id

old_status
new_status

changed_by
changed_at

reason
```

Ejemplo:

```text
REGISTERED  → APPROVED
APPROVED    → IN_PROGRESS
IN_PROGRESS → APPROVED
APPROVED    → IN_PROGRESS
IN_PROGRESS → PUBLISHED
```

Esto permitirá reconstruir el ciclo de vida completo de una oferta.

---

# 12. Trazabilidad

El sistema deberá permitir consultar la trazabilidad de una oferta.

Ejemplo:

```text
16/10/2026 20:02
Juan
CREÓ la oferta

16/10/2026 20:05
Juan
MODIFICÓ el salario

16/10/2026 20:08
Juan
APROBÓ la oferta

16/10/2026 20:10
Pedro
ADQUIRIÓ la oferta

16/10/2026 20:30
SYSTEM
LIBERÓ POR TIMEOUT

16/10/2026 20:32
María
ADQUIRIÓ la oferta

16/10/2026 20:36
María
PUBLICÓ la oferta
```

---

# 13. Modelo de dominio

## JobStatus

```java
public enum JobStatus {

    REGISTERED,
    APPROVED,
    IN_PROGRESS,
    PUBLISHED,
    REJECTED
}
```

---

## JobOffer

Conceptualmente:

```java
public class JobOffer {

    private UUID id;

    private String title;
    private String company;
    private String description;
    private String requirements;
    private String location;
    private String salary;
    private String employmentType;

    private String originalSourceUrl;

    private JobStatus status;

    private UUID assignedUserId;

    private Instant lockedAt;
    private Instant lockExpiresAt;

    private Instant approvedAt;
    private Instant publishedAt;

    private Instant createdAt;
    private UUID createdBy;

    private Instant updatedAt;
    private UUID updatedBy;
}
```

La entidad de dominio no deberá depender de:

```text
Spring
JPA
Hibernate
PostgreSQL
Thymeleaf
```

---

# 14. Control de concurrencia

El sistema no deberá depender de:

```java
synchronized
```

para garantizar la exclusividad de la adquisición.

La base de datos será la autoridad para resolver la concurrencia.

La adquisición podrá realizarse mediante una operación atómica conceptualmente equivalente a:

```sql
UPDATE job_offer
SET
    status = 'IN_PROGRESS',
    assigned_user_id = :userId,
    locked_at = CURRENT_TIMESTAMP,
    lock_expires_at = CURRENT_TIMESTAMP + INTERVAL '20 minutes'
WHERE id = :jobId
AND (
    status = 'APPROVED'
    OR (
        status = 'IN_PROGRESS'
        AND lock_expires_at < CURRENT_TIMESTAMP
    )
);
```

El resultado determinará si la adquisición fue exitosa.

```text
1 fila afectada  → adquisición exitosa
0 filas afectadas → conflicto
```

---

# 15. Optimistic Locking

La entidad de persistencia podrá utilizar:

```java
@Version
private Long version;
```

El objetivo será detectar modificaciones concurrentes.

El control de concurrencia se dividirá conceptualmente en:

```text
@Version
    ↓
Detección de modificaciones concurrentes

UPDATE atómico
    ↓
Adquisición exclusiva de ofertas
```

---

# 16. Scheduler

Spring Scheduler se utilizará para realizar tareas periódicas de mantenimiento.

Ejemplo conceptual:

```java
@Scheduled(fixedDelay = 60000)
public void releaseExpiredOffers() {
    // liberar ofertas expiradas
}
```

El scheduler deberá considerarse un mecanismo de limpieza y mantenimiento.

La seguridad del timeout no deberá depender exclusivamente del scheduler.

La propia operación de adquisición deberá considerar si el bloqueo ya expiró.

---

# 17. Persistencia

Se utilizará:

```text
PostgreSQL
```

con:

```text
Spring Data JPA
Hibernate
Flyway
```

No se utilizará:

```properties
spring.jpa.hibernate.ddl-auto=update
```

como mecanismo principal de evolución de la base de datos.

Las modificaciones estructurales serán administradas mediante:

```text
Flyway
```

---

# 18. Modelo de base de datos

Las tablas iniciales serán:

```text
USERS
ROLES
USER_ROLES

JOB_OFFER

JOB_OFFER_STATUS_HISTORY

AUDIT_EVENT
```

---

# 19. USERS

```text
USERS
--------------------------------
id UUID PK
username
password
full_name
enabled

created_at
updated_at
```

Las contraseñas deberán almacenarse utilizando un algoritmo seguro de hashing administrado por Spring Security.

---

# 20. ROLES

```text
ROLES
--------------------------------
id UUID PK
name
```

Roles iniciales:

```text
ROLE_ADMIN
ROLE_REGISTRADOR
ROLE_PUBLICADOR
```

---

# 21. USER_ROLES

```text
USER_ROLES
--------------------------------
user_id UUID FK
role_id UUID FK
```

Se deberá establecer una restricción única:

```text
(user_id, role_id)
```

---

# 22. JOB_OFFER

```text
JOB_OFFER
--------------------------------
id UUID PK

title
company
description
requirements
location
salary
employment_type

source_url

status

assigned_user_id
locked_at
lock_expires_at

approved_at
published_at

created_at
created_by

updated_at
updated_by

version
```

---

# 23. Integridad de datos

Se deberán utilizar restricciones de base de datos cuando corresponda.

Ejemplos:

- `NOT NULL`
- `UNIQUE`
- `FOREIGN KEY`
- `CHECK`
- índices
- restricciones de integridad

La validación no deberá depender únicamente del frontend.

---

# 24. Detección de duplicados

El sistema deberá contemplar mecanismos para evitar el registro repetido de una misma oferta.

Cuando la fuente proporcione un identificador externo:

```text
source_name
external_reference
```

podrá utilizarse una restricción única:

```text
(source_name, external_reference)
```

La URL de origen podrá utilizarse como información adicional, pero no necesariamente deberá considerarse un identificador único.

---

# 25. Arquitectura

Se utilizará:

> **Arquitectura Limpia / Hexagonal (Ports and Adapters)**

La estructura será:

```text
src/main/java/com/sigotrab/
│
├── domain/
│   ├── model/
│   ├── exception/
│   └── service/
│
├── application/
│   ├── port/
│   │   ├── in/
│   │   └── out/
│   │
│   └── service/
│
├── adapters/
│   ├── web/
│   │   ├── controller/
│   │   ├── dto/
│   │   └── mapper/
│   │
│   └── persistence/
│       ├── entity/
│       ├── repository/
│       └── mapper/
│
└── infrastructure/
    ├── security/
    ├── scheduler/
    ├── audit/
    └── config/
```

---

# 26. Domain

El dominio contendrá:

```text
JobOffer
JobStatus
UserId
Reglas de negocio
Excepciones de dominio
```

No tendrá dependencias directas de frameworks.

---

# 27. Application

Contendrá los casos de uso.

Ejemplos:

```text
RegisterJobOfferUseCase
UpdateJobOfferUseCase
ApproveJobOfferUseCase
RejectJobOfferUseCase
AcquireJobOfferUseCase
ReleaseJobOfferUseCase
PublishJobOfferUseCase
GetJobOfferHistoryUseCase
GetAuditEventsUseCase
```

---

# 28. Ports — entrada

Ejemplo:

```java
public interface AcquireJobOfferUseCase {

    void acquire(UUID jobOfferId, UUID userId);
}
```

Los controladores dependerán de estos puertos, no directamente de JPA.

---

# 29. Ports — salida

Ejemplo:

```java
public interface JobOfferRepository {

    Optional<JobOffer> findById(UUID id);

    List<JobOffer> findApprovedOffers();

    boolean acquire(UUID jobOfferId, UUID userId, Instant expiresAt);

    void save(JobOffer jobOffer);
}
```

La aplicación no sabrá si los datos vienen de:

```text
PostgreSQL
Oracle
MySQL
```

La implementación estará en infraestructura.

---

# 30. Web

La interfaz inicial utilizará:

```text
Spring MVC
Thymeleaf
Bootstrap
```

No será necesario implementar un SPA.

Pantallas principales:

```text
/login

/dashboard

/offers

/offers/new

/offers/{id}

/offers/registered

/offers/approved

/offers/in-progress

/offers/published

/audit

/reports
```

---

# 31. Dashboard

El dashboard deberá mostrar información resumida:

```text
REGISTERED
APPROVED
IN_PROGRESS
PUBLISHED
REJECTED
```

Ejemplo:

```text
┌───────────────────────────────────────┐
│                 SIGO                  │
├───────────────────────────────────────┤
│                                       │
│ REGISTERED   APPROVED   IN_PROGRESS  │
│     12           8           2        │
│                                       │
│              PUBLISHED                │
│                  35                   │
│                                       │
└───────────────────────────────────────┘
```

---

# 32. Reportes

El sistema deberá proporcionar inicialmente:

## Reporte de ofertas

Filtros:

- Fecha desde.
- Fecha hasta.
- Estado.
- Empresa.
- Usuario.

---

## Reporte de publicaciones

Deberá permitir consultar:

- Oferta.
- Empresa.
- Publicador.
- Fecha de publicación.
- Tiempo de procesamiento.

---

## Reporte de actividad

Deberá permitir consultar:

- Usuario.
- Acción.
- Fecha.
- Entidad.
- Resultado.

---

## Reporte de auditoría

Permitirá consultar los eventos registrados:

```text
Fecha
Usuario
Acción
Entidad
Registro
Resultado
```

---

# 33. Métricas

El sistema deberá almacenar las fechas necesarias para obtener métricas.

### Tiempo de aprobación

```text
approved_at - created_at
```

### Tiempo hasta publicación

```text
published_at - approved_at
```

### Tiempo de procesamiento

```text
published_at - locked_at
```

### Cantidad de expiraciones

Se obtendrá a partir de:

```text
AUDIT_EVENT
```

y/o:

```text
JOB_OFFER_STATUS_HISTORY
```

---

# 34. Seguridad

Se utilizará:

```text
Spring Security
```

La autorización deberá realizarse en el servidor.

El frontend no deberá considerarse un mecanismo de seguridad.

Por ejemplo, ocultar un botón:

```text
"Publicar"
```

no impide que un usuario intente acceder directamente al endpoint.

Por ello, las reglas deberán validarse también en backend.

---

# 35. Manejo de errores

Las operaciones deberán devolver errores controlados.

Ejemplo:

```text
409 CONFLICT
```

cuando otro usuario haya adquirido la oferta.

Código conceptual:

```json
{
    "code": "JOB_ALREADY_LOCKED",
    "message": "La oferta ya está siendo procesada por otro usuario."
}
```

Otros códigos:

```text
400 BAD_REQUEST
401 UNAUTHORIZED
403 FORBIDDEN
404 NOT_FOUND
409 CONFLICT
500 INTERNAL_SERVER_ERROR
```

---

# 36. Excepciones de dominio

Se podrán definir:

```text
InvalidJobStateException
JobLockedException
JobNotFoundException
UnauthorizedJobOperationException
InvalidTransitionException
```

Esto permitirá separar los errores de negocio de los errores técnicos.

---

# 37. Logging

Se utilizará logging técnico mediante:

```text
SLF4J
Logback
```

El logging servirá para:

- errores.
- excepciones.
- problemas de infraestructura.
- scheduler.
- conexión a BD.
- diagnóstico.

La auditoría funcional se almacenará en:

```text
AUDIT_EVENT
```

No deberán confundirse ambos mecanismos.

---

# 38. Backup

Aunque inicialmente sea un demo, se deberá considerar la posibilidad de realizar backup de PostgreSQL.

Ejemplo:

```text
sigotrab_2026-10-16.dump
```

En una implementación institucional se podrá establecer una política de:

```text
Backup diario
Retención semanal
Retención mensual
```

---

# 39. Pruebas unitarias

Se utilizará:

```text
JUnit 5
Mockito
```

Se deberán probar las reglas del dominio.

Ejemplo:

```text
REGISTERED → APPROVED       ✓

REGISTERED → PUBLISHED      ✗

APPROVED → IN_PROGRESS      ✓

APPROVED → PUBLISHED        ✗

IN_PROGRESS → PUBLISHED     ✓

IN_PROGRESS → APPROVED      ✓

PUBLISHED → APPROVED        ✗
```

---

# 40. Pruebas de concurrencia

Se deberá realizar una prueba específica para verificar que dos usuarios no puedan adquirir simultáneamente la misma oferta.

Escenario:

```text
Oferta = APPROVED

Usuario A ───────┐
                 ├──► acquire()
Usuario B ───────┘
```

Resultado esperado:

```text
Usuario A → SUCCESS
Usuario B → CONFLICT
```

La base de datos deberá garantizar que solamente una operación sea exitosa.

---

# 41. Prueba de timeout

Escenario:

```text
status = IN_PROGRESS

lock_expires_at < NOW()
```

Resultado:

```text
status = APPROVED
assigned_user_id = NULL
locked_at = NULL
lock_expires_at = NULL
```

Y deberá existir un evento de auditoría:

```text
SYSTEM
TIMEOUT_RELEASE
```

---

# 42. Pruebas de seguridad

Se deberán comprobar como mínimo:

```text
REGISTRADOR → puede aprobar
PUBLICADOR  → no puede aprobar

PUBLICADOR  → puede adquirir
REGISTRADOR → no puede adquirir

PUBLICADOR  → puede publicar
REGISTRADOR → no puede publicar
```

Los permisos deberán comprobarse en backend.

---

# 43. Configuración

Los valores variables deberán estar externalizados.

Ejemplo:

```properties
server.port=8080

spring.datasource.url=jdbc:postgresql://localhost:5432/sigotrab

sigotrab.lock.duration-minutes=20

sigotrab.scheduler.fixed-delay-ms=60000
```

Para el demo:

```properties
sigotrab.lock.duration-minutes=2
```

La aplicación no deberá requerir modificaciones de código para cambiar estos valores.

---

# 44. Despliegue inicial

El demo podrá empaquetarse como:

```text
sigotrab.jar
```

La computadora deberá tener:

```text
Java 25
PostgreSQL
```

Luego:

```bash
java -jar sigotrab.jar
```

y acceder:

```text
http://localhost:8080
```

---

# 45. Evolución futura

La arquitectura deberá permitir evolucionar desde:

```text
DEMO
│
└── Una computadora
```

a:

```text
LAN
│
├── PC Usuario 1
├── PC Usuario 2
├── PC Usuario 3
│
└── Servidor
    ├── Spring Boot
    └── PostgreSQL
```

sin modificar las reglas fundamentales del dominio.

En una etapa posterior podrán incorporarse:

- Docker.
- Nginx.
- HTTPS.
- Backup automatizado.
- Integración con fuentes externas.
- Integración con portal institucional.
- Mayor número de usuarios.
- API REST pública/interna.
- almacenamiento de documentos.
- notificaciones.
- métricas avanzadas.

Estas características quedan fuera del alcance inicial.

---

# 46. Tecnologías

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 25 |
| Framework | Spring Boot |
| Web | Spring MVC |
| Frontend | Thymeleaf |
| UI | Bootstrap |
| Seguridad | Spring Security |
| Persistencia | Spring Data JPA |
| ORM | Hibernate |
| Base de datos | PostgreSQL |
| Migraciones | Flyway |
| Scheduler | Spring Scheduler |
| Logging | SLF4J + Logback |
| Testing | JUnit 5 + Mockito |
| Build | Maven |

---

# 47. Principios de diseño

El proyecto deberá respetar:

### Separación de responsabilidades

Cada capa deberá tener una responsabilidad clara.

### Dependency Inversion

El dominio no deberá depender de infraestructura.

### Single Responsibility

Los casos de uso deberán representar operaciones específicas.

### Validación en servidor

Nunca confiar exclusivamente en las validaciones del navegador.

### Persistencia como autoridad

La base de datos deberá garantizar la integridad y concurrencia.

### Auditoría

Toda operación relevante deberá quedar registrada.

### Seguridad por roles

Los permisos deberán validarse en backend.

### Configuración externa

Los parámetros variables no deberán estar codificados directamente.

### Testabilidad

Las reglas de negocio deberán poder probarse sin levantar toda la aplicación.

---

# 48. Estructura funcional final

```text
                         SIGO
      Sistema de Gestión y Organización
             de Ofertas Laborales
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
    REGISTRO          CURACIÓN       PUBLICACIÓN
        │                │                │
        └────────────────┼────────────────┘
                         │
                         ▼
                    JOB OFFER
                         │
          ┌──────────────┼──────────────┐
          │              │              │
          ▼              ▼              ▼
       Estados       Concurrencia    Auditoría
          │              │              │
          ▼              ▼              ▼
      Workflow       PostgreSQL     AUDIT_EVENT
                         │
                         ▼
                  STATUS_HISTORY
                         │
                         ▼
                    REPORTES
```

---

# 49. Flujo completo

```text
                REGISTRADOR
                     │
                     ▼
                ┌─────────┐
                │REGISTERED│
                └────┬────┘
                     │
                Validación
                     │
              ┌──────▼──────┐
              │   APPROVED  │
              └──────┬──────┘
                     │
             ┌───────┴────────┐
             │                │
             ▼                ▼
       PUBLICADOR A      PUBLICADOR B
             │                │
             │ ADQUIRIR       │ ADQUIRIR
             ▼                ▼
       ┌─────────────┐       ❌
       │ IN_PROGRESS │    CONFLICT
       └──────┬──────┘
              │
       ┌──────┴───────────┐
       │                  │
   PUBLICAR            TIMEOUT
       │                  │
       ▼                  ▼
  ┌───────────┐      ┌─────────┐
  │ PUBLISHED │      │APPROVED │
  └───────────┘      └─────────┘
```

En **cada punto relevante del flujo** se generará auditoría.

---

# 50. Criterios de aceptación

El demo se considerará funcional cuando:

- [ ] Un registrador pueda crear una oferta.
- [ ] La oferta inicie en `REGISTERED`.
- [ ] El registrador pueda modificarla.
- [ ] Las modificaciones queden auditadas.
- [ ] El registrador pueda aprobarla.
- [ ] Solo ofertas `APPROVED` estén disponibles para publicadores.
- [ ] Un publicador pueda adquirir una oferta.
- [ ] La adquisición sea atómica.
- [ ] Dos publicadores no puedan adquirir la misma oferta.
- [ ] La oferta muestre usuario asignado.
- [ ] La oferta tenga tiempo de expiración.
- [ ] Una oferta expirada pueda volver a `APPROVED`.
- [ ] El timeout quede auditado.
- [ ] El usuario asignado pueda cancelar.
- [ ] El usuario asignado pueda confirmar la publicación.
- [ ] Una oferta publicada no pueda volver al flujo normal.
- [ ] Todas las transiciones sean validadas por el dominio.
- [ ] Los roles tengan permisos diferentes.
- [ ] Las operaciones relevantes queden registradas en auditoría.
- [ ] Se pueda consultar el historial de una oferta.
- [ ] Se puedan generar reportes básicos.
- [ ] Existan pruebas unitarias de las transiciones.
- [ ] Exista al menos una prueba de concurrencia.
- [ ] El sistema pueda ejecutarse en una sola computadora.

---

# 51. Resultado esperado

SIGO deberá demostrar que es posible implementar un sistema web pequeño pero estructurado profesionalmente, aplicando:

```text
Arquitectura Hexagonal
        +
Spring Boot
        +
PostgreSQL
        +
Spring Security
        +
Control de Concurrencia
        +
Auditoría Integral
        +
Workflow
        +
Reportes
        +
Pruebas Automatizadas
```

Esta versión será deliberadamente sencilla en infraestructura, pero deberá mantener una arquitectura que permita evolucionar posteriormente hacia una implementación multiusuario dentro de una LAN institucional (La demo debe ser funcional en red LAN).
