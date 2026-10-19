# Ciclo de vida cerrado: sin despublicar ni borrar ofertas

El spec catalogaba las acciones `DELETE` (§10) y `UNPUBLISH` (§10) y hablaba de
"eliminación lógica" (§11/RF-11), sin flujo ni transición que las respaldara, y trataba
`REJECTED` como opcional (§6). Se decide que **`REJECTED` y `PUBLISHED` son terminales**,
que **no existe despublicación** ni **borrado de ofertas** en v1, y que el rechazo solo
ocurre desde `REGISTERED`. Motivo: mantener un ciclo de vida honesto y auditable sin
estados ni acciones sin dueño.
