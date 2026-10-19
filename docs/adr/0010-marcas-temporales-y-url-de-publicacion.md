# Marcas temporales y URL de publicación

El ciclo puede repetirse (aprobada → expira → vuelve a aprobada). Se decide que
`approved_at` se fija en la **primera** aprobación y no se recalcula; `published_at` se
fija al publicar; `extracted_at` (fecha en que la oferta se obtuvo de su fuente) es
obligatoria y distinta de `created_at`; y se añade `publication_url` opcional como
evidencia de dónde quedó publicada la oferta. Las métricas del §33 del spec usan estas
marcas.
