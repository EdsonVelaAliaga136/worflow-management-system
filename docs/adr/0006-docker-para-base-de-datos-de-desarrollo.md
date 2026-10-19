# Docker para la base de datos de desarrollo

El plan excluía Docker (§11/§45) y fijaba PostgreSQL 17 nativo para la demo. Se decide
**incluir un servicio `postgres:17` en Docker Compose** para la base de datos de
desarrollo/demo (con `POSTGRES_DB=sigotrab`, `POSTGRES_USER=sigotrab` y contraseña por
`.env`, sin secretos reales), manteniendo PostgreSQL nativo como alternativa. Motivo: el
entorno se vuelve reproducible y ya existía un `docker/docker-compose.yml` genérico
(PG16/`appdb`) que no coincide con la base de datos de la aplicación.

## Consecuencias

- Se revierte la exclusión de Docker del plan §11/§45 (solo como entorno de desarrollo).
- Los tests siguen usando `embedded-postgres` (Zonky), **sin** Docker.
