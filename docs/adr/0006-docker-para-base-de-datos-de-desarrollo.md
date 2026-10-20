# Docker para la base de datos de desarrollo

El plan excluía Docker (§11/§45) y fijaba PostgreSQL 17 nativo para la demo. Se decide
**incluir un servicio `postgres:17` en Docker Compose** para la base de datos de
desarrollo/demo (con `POSTGRES_DB=sigotrabdb`, `POSTGRES_USER=sigotrabuser` y contraseña
por `.env`, sin secretos reales), manteniendo PostgreSQL nativo como alternativa. Motivo:
el entorno se vuelve reproducible y ya existía un `docker/docker-compose.yml` genérico
(PG16/`appdb`) que no coincide con la base de datos de la aplicación.

> El `.env` actual fija `POSTGRES_VERSION=16-alpine`; unificar la versión (17) es deuda
> aparte (ver T1 8.2.8). El nombre de BD y usuario sí quedan unificados en
> `sigotrabdb`/`sigotrabuser`.

## Consecuencias

- Se revierte la exclusión de Docker del plan §11/§45 (solo como entorno de desarrollo).
- Los tests siguen usando `embedded-postgres` (Zonky), **sin** Docker.
