# Deshabilitar un usuario libera sus bloqueos

Un usuario deshabilitado podía dejar ofertas `IN_PROGRESS` bloqueadas sin dueño activo,
cuando solo el asignado o `SYSTEM` pueden liberar. Se decide que, al deshabilitar un
usuario, sus ofertas en proceso se liberan de inmediato a `APPROVED` y la operación se
audita como `SYSTEM` (acción `RELEASE`).
