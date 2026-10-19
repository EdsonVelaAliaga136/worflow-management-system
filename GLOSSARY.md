# SIGO — Gestión y Organización de Ofertas Laborales

Contexto único. Glosario del lenguaje del dominio de SIGO: el flujo que lleva una
oferta laboral desde una fuente externa hasta su publicación en el portal institucional,
con control de concurrencia y auditoría.

## Lenguaje

**Oferta** (`JobOffer`):
Una oferta laboral obtenida de una fuente externa y gestionada por SIGO hasta su publicación.
_Avoid_: vacante, puesto, empleo, plaza

**Oferta registrada** (`REGISTERED`):
Oferta recién ingresada al sistema, aún editable por un Registrador.
_Avoid_: borrador, pendiente

**Oferta aprobada** (`APPROVED`):
Oferta validada por un Registrador y disponible para que un Publicador la adquiera.

**Oferta en proceso** (`IN_PROGRESS`):
Oferta bloqueada temporalmente por el Publicador que la adquirió, mientras prepara su publicación.

**Oferta publicada** (`PUBLISHED`):
Oferta confirmada como cargada en el portal institucional. Estado terminal.

**Oferta rechazada** (`REJECTED`):
Oferta descartada por un Registrador con un motivo. Estado terminal.

**Registrador**:
Usuario que ingresa, cura, aprueba o rechaza ofertas.
_Avoid_: capturista, analista

**Publicador**:
Usuario que adquiere ofertas aprobadas y confirma su publicación manual en el portal.
_Avoid_: editor, gestor

**Administrador**:
Usuario que gestiona usuarios y roles, y consulta auditoría, reportes y métricas.

**Adquisición** (`acquire`):
Acto atómico por el cual un Publicador toma una oferta aprobada y la bloquea para sí.

**Bloqueo** (`lock`):
Asignación temporal y exclusiva de una oferta a un Publicador, con fecha de expiración.
_Avoid_: reserva, toma

**Liberación** (`release`):
Devolver una oferta en proceso al estado aprobado, borrando su bloqueo.
_Avoid_: soltar, desasignar

**Liberación por expiración**:
Liberación automática de un bloqueo vencido, acreditada al actor `SYSTEM`.
_Avoid_: timeout, caducidad

**Publicación** (`publish`):
Confirmación manual de que la oferta ya fue cargada en el portal institucional.
_Avoid_: alta, subida

**Rechazo**:
Cierre de una oferta registrada que no cumple las condiciones, siempre con motivo.

**Fuente externa**:
Origen de una oferta, identificado por el par `source_name` + `external_reference`.
_Avoid_: portal origen, sitio

**Tiempo de extracción** (`extracted_at`):
Fecha en que la oferta fue obtenida de su fuente externa, distinta de su fecha de registro.

**URL de publicación** (`publication_url`):
Dirección donde la oferta quedó publicada en el portal institucional, registrada al confirmar la publicación.

**Ofertas propias**:
Las ofertas en proceso o publicadas que están asignadas a un Publicador concreto; cada Publicador ve solo las suyas.

**Trazabilidad**:
Línea de tiempo de los hechos de una oferta: cambios de estado y operaciones auditadas.
