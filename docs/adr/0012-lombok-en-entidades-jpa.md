# Uso de Lombok en entidades JPA

El plan (D11) decidió **no** usar Lombok ni MapStruct y escribir getters/setters a mano. Se
decide **permitir Lombok** (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`)
en las **entidades JPA** de `adapters/persistence/entity` (p. ej. `UserJpaEntity`,
`RoleJpaEntity`), manteniendo el resto de las reglas. Motivo: las entidades son un detalle de
persistencia (no dominio) y Lombok elimina el ruido de *boilerplate*; ya venía declarado en el
`pom.xml`.

## Consecuencias

- Se permite `org.projectlombok:lombok` (scope `provided`) y su `annotationProcessorPath` en
  `pom.xml`.
- **No** se usa Lombok en `domain/` (el dominio debe seguir siendo Java puro, sin frameworks)
  ni para generar mappers (los mappers siguen siendo manuales).
- JPA exige un constructor sin argumentos: usa `@NoArgsConstructor`. **No** combines
  `@NoArgsConstructor` con `@RequiredArgsConstructor` cuando no hay campos `final`, porque
  ambos generan un constructor sin argumentos (error de compilación).
