# Campos obligatorios de la oferta y fecha de extracción

RF-01 listaba todos los datos como mínimos, pero el esquema solo exigía `title` y
`company`, y faltaba la fecha de extracción que RF-01 también pedía. Se decide que los
campos **obligatorios** son `title`, `company`, `description`, `requirements` y
`source_url`; los **opcionales** son `location`, `salary` y `employment_type`; y se añade
`extracted_at` (fecha en que la oferta se obtuvo de su fuente, distinta de `created_at`).
