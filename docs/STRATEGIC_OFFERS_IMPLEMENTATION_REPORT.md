# Strategic Offers — reporte de implementación y continuidad

Fecha: 2026-11-10. Repositorio: BodeGo Backend, paquete raíz `com.bodego.platform`.

## 1. Resumen ejecutivo

Implementado `strategicoffers` con Recommendation y Offer independientes: dominio, Commands/Queries, cuatro interfaces CQRS, cuatro servicios de aplicación, persistencia JPA, ACL Catalog/Monitoring, Resources, Assemblers y dos controladores (10 operaciones REST). No se modifican stock ni precios y no se crean ofertas automáticamente.

**Verificado:** compilación final de 238 fuentes con `release 25`; 79 pruebas seleccionadas, 0 fallos, 0 errores, 0 omitidas dentro de la selección (24 nuevas y 55 existentes). **Pendiente:** arranque completo, DDL, persistencia y flujos HTTP con PostgreSQL autenticado. El puerto 5432 responde, pero esta sesión no dispone de `DB_PASSWORD`. `BodegoPlatformApplicationTests` fue excluida explícitamente; no se afirma que la suite completa haya pasado.

## 2. Arquitectura implementada

Monolito modular DDD y CQRS ligero siguiendo Catalog, Operations y Monitoring. Los agregados heredan `AuditableAbstractAggregateRoot<T>`; referencias por IDs, sin asociaciones JPA entre agregados/contextos. Commands/Queries son records y los servicios reciben sus tipos mediante `handle`. Las lecturas usan transacciones `readOnly`; las escrituras usan transacciones Spring y llaman la ACL de Monitoring dentro de ellas.

Paquetes: `domain/model/{aggregates,valueobjects,commands,queries}`, `domain/services`, `application/internal/{commandservices,queryservices,outboundservices/acl}`, `infrastructure/persistence/jpa/repositories`, `interfaces/rest/{resources,transform}`. No se añadieron clases base, buses, schedulers ni autenticación ficticia. Se usan las excepciones `IllegalArgumentException` y el handler existente: no hizo falta crear excepciones específicas.

## 3. Archivos creados

39 archivos Java de producción bajo `src/main/java/com/bodego/platform/strategicoffers/`:

| Ruta relativa | Responsabilidad |
|---|---|
| `application/internal/commandservices/OfferCommandServiceImpl.java` | Orquestación transaccional, reglas, persistencia y auditoría. |
| `application/internal/commandservices/RecommendationCommandServiceImpl.java` | Orquestación transaccional, reglas, persistencia y auditoría. |
| `application/internal/outboundservices/acl/ExternalCatalogService.java` | Adaptador de snapshots y validación de producto vía Catalog. |
| `application/internal/outboundservices/acl/ExternalMonitoringService.java` | Adaptador de auditoría vía Monitoring; userId null. |
| `application/internal/queryservices/OfferQueryServiceImpl.java` | Consultas de solo lectura. |
| `application/internal/queryservices/RecommendationQueryServiceImpl.java` | Consultas de solo lectura. |
| `domain/model/aggregates/Offer.java` | Agregado JPA e invariantes de negocio. |
| `domain/model/aggregates/Recommendation.java` | Agregado JPA e invariantes de negocio. |
| `domain/model/commands/ActivateOfferCommand.java` | Contrato de escritura; valida identificadores. |
| `domain/model/commands/CreateOfferCommand.java` | Contrato de escritura; valida identificadores. |
| `domain/model/commands/DeactivateOfferCommand.java` | Contrato de escritura; valida identificadores. |
| `domain/model/commands/DismissRecommendationCommand.java` | Contrato de escritura; valida identificadores. |
| `domain/model/commands/GenerateRecommendationsCommand.java` | Contrato de escritura; valida identificadores. |
| `domain/model/commands/UpdateOfferCommand.java` | Contrato de escritura; valida identificadores. |
| `domain/model/queries/GetOfferByIdQuery.java` | Contrato de lectura y filtros opcionales. |
| `domain/model/queries/GetOffersQuery.java` | Contrato de lectura y filtros opcionales. |
| `domain/model/queries/GetRecommendationByIdQuery.java` | Contrato de lectura y filtros opcionales. |
| `domain/model/queries/GetRecommendationsQuery.java` | Contrato de lectura y filtros opcionales. |
| `domain/model/valueobjects/RecommendationType.java` | Tipos y validaciones de identificadores del dominio. |
| `domain/model/valueobjects/StrategicOffersValidation.java` | Tipos y validaciones de identificadores del dominio. |
| `domain/services/OfferCommandService.java` | Interfaz CQRS del servicio de dominio. |
| `domain/services/OfferQueryService.java` | Interfaz CQRS del servicio de dominio. |
| `domain/services/RecommendationCommandService.java` | Interfaz CQRS del servicio de dominio. |
| `domain/services/RecommendationQueryService.java` | Interfaz CQRS del servicio de dominio. |
| `infrastructure/persistence/jpa/repositories/OfferRepository.java` | Repositorio JPA, filtros AND y bloqueo de escrituras. |
| `infrastructure/persistence/jpa/repositories/RecommendationGenerationLock.java` | Bloqueo advisory transaccional PostgreSQL por negocio. |
| `infrastructure/persistence/jpa/repositories/RecommendationRepository.java` | Repositorio JPA, filtros AND y bloqueo de escrituras. |
| `interfaces/rest/OffersController.java` | Endpoints REST y documentación OpenAPI. |
| `interfaces/rest/RecommendationsController.java` | Endpoints REST y documentación OpenAPI. |
| `interfaces/rest/resources/CreateOfferResource.java` | Contrato JSON; validación Jakarta en entradas. |
| `interfaces/rest/resources/GenerateRecommendationsResource.java` | Contrato JSON; validación Jakarta en entradas. |
| `interfaces/rest/resources/OfferResource.java` | Contrato JSON; validación Jakarta en entradas. |
| `interfaces/rest/resources/RecommendationResource.java` | Contrato JSON; validación Jakarta en entradas. |
| `interfaces/rest/resources/UpdateOfferResource.java` | Contrato JSON; validación Jakarta en entradas. |
| `interfaces/rest/transform/CreateOfferCommandFromResourceAssembler.java` | Transformación Resource/Command o agregado/Resource. |
| `interfaces/rest/transform/GenerateRecommendationsCommandFromResourceAssembler.java` | Transformación Resource/Command o agregado/Resource. |
| `interfaces/rest/transform/OfferResourceFromEntityAssembler.java` | Transformación Resource/Command o agregado/Resource. |
| `interfaces/rest/transform/RecommendationResourceFromEntityAssembler.java` | Transformación Resource/Command o agregado/Resource. |
| `interfaces/rest/transform/UpdateOfferCommandFromResourceAssembler.java` | Transformación Resource/Command o agregado/Resource. |

Cuatro archivos de pruebas bajo `src/test/java/com/bodego/platform/strategicoffers/`:

| Ruta relativa | Cobertura |
|---|---|
| `application/internal/commandservices/RecommendationCommandServiceTests.java` | 6 pruebas: umbrales estrictos, long, fechas, deduplicación, descarte/auditoría idempotentes y pertenencia. |
| `application/internal/commandservices/OfferCommandServiceTests.java` | 8 pruebas: creación, descuento, fechas, referencias, vigencia, cambios de estado, actualización idempotente y auditoría. |
| `application/internal/StrategicOffersAclAndQueryTests.java` | 4 pruebas: fachada real con servicios Catalog simulados, snapshots compatibles, producto activo/pertenencia y delegación de filtros. |
| `interfaces/rest/StrategicOffersRestTests.java` | 6 pruebas con MockMvc standalone: rutas, filtros, JSON, Jakarta Validation y respuestas 200/201/400/404/405. |

Documento creado: `docs/STRATEGIC_OFFERS_IMPLEMENTATION_REPORT.md` (este reporte). Total: 44 archivos nuevos.

## 4. Archivos modificados

Solo dos archivos existentes, ambos contratos de lectura ACL de Catalog:

- `src/main/java/com/bodego/platform/catalog/interfaces/acl/resources/ProductInventorySnapshot.java`: agrega `name: String` y `active: Boolean`; conserva constructor `(id,businessId,minimumStock)` para consumidores existentes. Ese constructor deja los campos nuevos en null; la fachada real siempre los obtiene del producto. Monitoring sigue leyendo sus tres campos anteriores sin cambiar reglas.
- `src/main/java/com/bodego/platform/catalog/interfaces/acl/CatalogContextFacade.java`: completa los campos nuevos al listar y agrega `getProductById(businessId,productId)` usando `ProductQueryService`, validando existencia y pertenencia. Permite validar una oferta mediante consulta puntual sin cargar todos los productos del negocio.

Sin cambios en entidades o repositorios Catalog, Operations, Monitoring, Shared, IAM, `pom.xml`, propiedades o credenciales. El snapshot de lote existente ya aporta todos los datos necesarios; no se amplió. No hubo commits, merges, pushes ni cambios de rama. El árbol Git estaba limpio al inicio.

## 5. Modelo de dominio

### Recommendation

`id`, `createdAt`, `updatedAt` heredados; `businessId`, `productId` positivos obligatorios; `lotId` positivo opcional; `type` obligatorio (`RESTOCK`, `OVERSTOCK`, `EXPIRING`); `message` no vacío de hasta 255 caracteres; `generatedAt: Instant` obligatorio; `dismissed: boolean` inicialmente false. Campos de origen inmutables en JPA (`updatable=false`). Solo EXPIRING exige lote; RESTOCK/OVERSTOCK no admiten lote. `dismiss()` devuelve si hubo transición y conserva historial.

Generación manual por negocio:

- Solo productos activos. Se valida pertenencia de snapshots de producto y lote.
- Stock total: suma con `long` de cantidades de todos los lotes, incluidos los vencidos o sin vencimiento.
- RESTOCK: `stock < minimumStock`.
- OVERSTOCK: `minimumStock > 0 && stock > 3L * minimumStock`; tanto suma como multiplicación evitan overflow de int.
- EXPIRING: lote con cantidad positiva y vencimiento entre hoy UTC y hoy + 30 días, inclusive. Se omiten fechas null, vencidos y cantidades cero.
- Una única captura de `clock.instant()` por ejecución, posterior al bloqueo; de ella derivan fecha UTC y `generatedAt` de todas las recomendaciones nuevas. Constructor adicional de servicio con Clock para pruebas deterministas.
- Deduplicación por negocio/producto/lote/tipo con `dismissed=false`. Una recomendación descartada permite generar otra si persiste la condición. No se descartan automáticamente cuando desaparece la condición.

### Offer

`id`, timestamps heredados; `businessId`, `productId` positivos obligatorios e inmutables; `title` no vacío, máximo 255, normalizado con trim; `discountPercentage: BigDecimal`, estrictamente entre 0 y 100; `startDate`, `endDate` obligatorios con `endDate >= startDate`; `active: boolean` inicialmente true.

Crear exige producto existente, activo y del negocio; una transición a activo vuelve a validar esas condiciones mediante Catalog. Una activación ya activa es una operación sin cambios. PUT modifica título, descuento y fechas, conserva negocio, producto y estado; no exige que el producto continúe activo para editar una oferta existente. No se admite reasignar producto/negocio mediante PUT.

`isEffectiveOn(LocalDate)` combina flag activo y ambas fechas inclusivas. No hay vencimiento automático ni eliminación. Las ofertas vencidas pueden conservar `active=true`; GET `active` filtra el flag persistido. Se permite solapamiento y no existe relación Recommendation–Offer. No se modifican `Product.price` ni cantidades.

Descarte, activación, desactivación y PUT sin cambios no vuelven a guardar ni registrar auditoría. PUT compara descuento numéricamente (`10` y `10.00` son equivalentes), título normalizado y fechas. Ninguna clase expone setters indiscriminados.

## 6. Commands y Queries

Commands: `GenerateRecommendationsCommand(businessId)`, `DismissRecommendationCommand(recommendationId)`, `CreateOfferCommand(businessId,productId,title,discountPercentage,startDate,endDate)`, `UpdateOfferCommand(offerId,title,discountPercentage,startDate,endDate)`, `ActivateOfferCommand(offerId)`, `DeactivateOfferCommand(offerId)`.

Queries: `GetRecommendationByIdQuery(recommendationId)`, `GetRecommendationsQuery(businessId,productId,type,dismissed)`, `GetOfferByIdQuery(offerId)`, `GetOffersQuery(businessId,productId,active)`. IDs presentes deben ser positivos; los filtros opcionales aceptan null. Un registro ausente devuelve Optional vacío para su traducción a 404.

## 7. Contratos REST

Base `/api/v1`; entradas/salidas JSON; sin autenticación mientras IAM esté pendiente.

| Método | Ruta | Entrada | Respuesta exitosa |
|---|---|---|---|
| POST | `/recommendations/generate` | `{ "businessId": 1 }` | 200, lista de recomendaciones nuevas o `[]` |
| GET | `/recommendations` | Query params opcionales `businessId`, `productId`, `type`, `dismissed` | 200, lista |
| GET | `/recommendations/{id}` | ID positivo | 200, RecommendationResource |
| PATCH | `/recommendations/{id}/dismiss` | Sin body | 200, recurso descartado |
| POST | `/offers` | CreateOfferResource | 201, OfferResource |
| GET | `/offers` | Query params opcionales `businessId`, `productId`, `active` | 200, lista |
| GET | `/offers/{id}` | ID positivo | 200, OfferResource |
| PUT | `/offers/{id}` | UpdateOfferResource | 200, recurso actualizado |
| PATCH | `/offers/{id}/activate` | Sin body | 200, recurso activo |
| PATCH | `/offers/{id}/deactivate` | Sin body | 200, recurso inactivo |

Todos los filtros se combinan mediante AND y se omiten si son null. Orden de ambos listados: ID descendente. No hay paginación.

CreateOfferResource:

```json
{
  "businessId": 1,
  "productId": 2,
  "title": "Promoción de temporada",
  "discountPercentage": 12.50,
  "startDate": "2026-11-10",
  "endDate": "2026-12-10"
}
```

UpdateOfferResource tiene los mismos cuatro campos editables (`title`, `discountPercentage`, `startDate`, `endDate`), sin businessId/productId. `active` se controla mediante PATCH.

RecommendationResource: `id,businessId,productId,lotId,type,message,generatedAt,dismissed,createdAt,updatedAt`. OfferResource: `id,businessId,productId,title,discountPercentage,startDate,endDate,active,createdAt,updatedAt`. Instant en ISO 8601; LocalDate en formato YYYY-MM-DD. No se expone un flag efectivo dependiente del instante de serialización.

404 para IDs inexistentes en lectura, edición o transiciones; 400 para IDs no positivos, recursos inválidos y violaciones de reglas (incluido producto inexistente/inactivo o ajeno al negocio al crear/activar). IllegalArgumentException y validaciones de body usan el `GlobalExceptionHandler` existente con `message`; enums/query params mal formados usan el manejo estándar de Spring. Métodos no disponibles, por ejemplo DELETE Offer o POST Recommendation arbitrario, devuelven 405. Los errores de BD no se ocultan como éxitos.

## 8. Integración ACL

`strategicoffers/.../ExternalCatalogService` delega exclusivamente en `CatalogContextFacade`; no importa repositorios ni entidades de Catalog. Reutiliza `getProductsByBusinessId` y `getLotsByProductId`. La nueva consulta puntual devuelve el mismo snapshot validando negocio; el adaptador verifica active. Los mensajes de recomendaciones usan IDs, por lo que no requieren número de lote y nunca superan 255 caracteres por nombres extensos.

`ExternalMonitoringService` delega en `MonitoringContextFacade.recordAction`, con userId=null. EntityType: `Recommendation` u `Offer`; entityId corresponde al agregado guardado. Acciones:

| Operación | Acción |
|---|---|
| Nueva recomendación | `RECOMMENDATION_GENERATED` |
| Primer descarte | `RECOMMENDATION_DISMISSED` |
| Nueva oferta | `OFFER_CREATED` |
| Edición con cambios | `OFFER_UPDATED` |
| Transición inactiva → activa | `OFFER_ACTIVATED` |
| Transición activa → inactiva | `OFFER_DEACTIVATED` |

Descripciones con IDs/tipos, sin copiar payloads. Fachada Monitoring y su handler mantienen `MANDATORY`; se propagan errores. No se utiliza `REQUIRES_NEW` ni escritura pública de AuditLog. La auditoría JPA de timestamps y la auditoría de negocio son mecanismos distintos. Los adaptadores tienen nombres de bean explícitos `strategicOffersExternalCatalogService` y `strategicOffersExternalMonitoringService` para evitar colisiones con Operations/Monitoring.

## 9. Persistencia

Mapeo implementado; **DDL y consultas reales aún no verificados**. Se conserva naming snake_case/plural existente y `ddl-auto=update` de desarrollo, sin ejecutarlo durante esta tarea.

| Tabla | Columnas previstas |
|---|---|
| `recommendations` | `id` bigint identity PK, `business_id` bigint NOT NULL, `product_id` bigint NOT NULL, `lot_id` bigint nullable, `type` varchar(20) NOT NULL, `message` varchar(255) NOT NULL, `generated_at` timestamp Instant NOT NULL, `dismissed` boolean NOT NULL, `created_at`/`updated_at` Instant NOT NULL |
| `offers` | `id` bigint identity PK, `business_id`/`product_id` bigint NOT NULL, `title` varchar(255) NOT NULL, `discount_percentage` numeric NOT NULL, `start_date`/`end_date` date NOT NULL, `active` boolean NOT NULL, `created_at`/`updated_at` Instant NOT NULL |

BigDecimal usa `columnDefinition="numeric"` de PostgreSQL, sin escala fija que redondee porcentajes pequeños o próximos a 100. No hay FK JPA entre agregados, índices únicos nuevos, CHECK SQL explícitos ni migraciones; las invariantes se preservan en métodos de dominio y anotaciones Jakarta. Los tipos exactos de timestamps y restricciones adicionales generadas por Hibernate deben inspeccionarse en PostgreSQL.

Deduplicación siguiendo Monitoring: advisory lock transaccional por negocio antes de leer Catalog y buscar duplicados, aislamiento READ_COMMITTED, namespace independiente `1397704262`, business key `Long.hashCode(businessId)`. Colisiones del hash solo serializan negocios adicionales. Se libera al terminar la transacción. Para lotId null, el método derivado Spring Data expresa la búsqueda de recomendaciones a nivel producto; su SQL real es una verificación pendiente. No hay garantía frente a escritores externos que omitan este protocolo.

Edición/transiciones usan `PESSIMISTIC_WRITE` mediante `findWithLockById`. El registro de auditoría comparte transacción con el cambio; el ID identity ya existe tras save. Atomicidad/rollback SQL y comportamiento concurrente son expectativas del diseño, no resultados verificados por mocks.

## 10. Verificaciones realizadas

### Verificado

Se inspeccionaron estructura del repositorio, `BODEGO_BACKEND_CONTEXT.md`, pom, propiedades, base de auditoría, implementaciones Catalog/Operations/Monitoring y fachadas/snapshots. No se identificó un frontend BodeGo con Recommendation/Offer en los proyectos cercanos revisados (APPWEB, fronent, opensource, appweb1 y fronentaplicacionesweb).

POM real: Spring Boot **4.0.9**, Java **25**, springdoc **3.0.2**. Entorno ejecutado: OpenJDK **26.0.2.1**, encontrado en `C:/Users/User/.jdks/openjdk-26.0.2.1`; JAVA_HOME se ajustó solo en procesos de comandos. Java 25 exacto no fue ejecutado.

| Incremento / comando | Resultado real |
|---|---|
| Etapa 1: `.\mvnw.cmd clean compile` | BUILD SUCCESS, 219 fuentes, release 25 |
| Etapa 2: `.\mvnw.cmd --no-transfer-progress clean compile` | BUILD SUCCESS, 229 fuentes |
| Etapa 2: `.\mvnw.cmd --no-transfer-progress test '-Dtest=RecommendationCommandServiceTests'` | 6 pruebas, sin fallos |
| Etapa 3: `.\mvnw.cmd --no-transfer-progress clean compile` | BUILD SUCCESS, 237 fuentes |
| Etapa 3: `.\mvnw.cmd --no-transfer-progress test '-Dtest=RecommendationCommandServiceTests,OfferCommandServiceTests'` | 13 pruebas, sin fallos |
| Etapa 4: clean compile con auditoría | BUILD SUCCESS, 238 fuentes |
| Regresión tras idempotencia PUT, selección sin contextLoads | 69 pruebas, sin fallos |
| Regresión tras MockMvc | 75 pruebas, sin fallos |
| Compilación final: `.\mvnw.cmd --no-transfer-progress clean compile` | BUILD SUCCESS, 238 fuentes, release 25, 2026-11-10 16:02:03 -05:00 |
| Pruebas finales: `.\mvnw.cmd --no-transfer-progress test '-Dtest=!BodegoPlatformApplicationTests'` | BUILD SUCCESS, 79 pruebas, 0 fallos/errores/omitidas en selección, 2026-11-10 16:02:40 -05:00 |
| `git diff --check` | Sin errores de whitespace en cambios rastreados |
| Conexión TCP localhost:5432 | Puerto accesible; no acredita login, base o esquema |

Las pruebas nuevas usan repositorios/servicios simulados y MockMvc standalone; no arrancan servidor HTTP ni PostgreSQL. La regresión existente incluye escaneo de componentes de todo `com.bodego.platform` sin sobrescritura de beans. Reportes Surefire disponibles en `target/surefire-reports` hasta el próximo clean.

### Implementado pero no verificado contra infraestructura real

Creación/actualización de tablas, JPQL con filtros combinados, deduplicación con lotId null, bloqueos PostgreSQL, generación/descarte persistidos, ciclo completo Offer y registros AuditLog en la misma transacción. Las pruebas verifican reglas y llamadas ACL, no commits ni rollback.

### Pendiente por entorno

No se ejecutó `BodegoPlatformApplicationTests`, ni `mvnw test` sin selección, ni arranque completo: necesitan la configuración real de conexión a PostgreSQL. `DB_PASSWORD` no está definida en el proceso; no se leyó ni cambió ninguna contraseña. No se sustituyó configuración para hacer pasar contextLoads. Tampoco se ejecutaron Swagger, solicitudes HTTP de red, SQL ni DDL sobre `bodego_db`. Nunca se ejecutó create-drop.

## 11. Problemas encontrados

- Java no estaba en PATH y JAVA_HOME no estaba disponible para Maven. Se encontró un JDK local y se indicó por proceso; no se cambió el sistema ni el pom.
- Snapshot de producto incompleto para filtrar activos/validar ofertas. Se amplió el contrato de lectura conservando constructor anterior y regresión de Monitoring.
- Posibles colisiones de beans ExternalCatalogService/ExternalMonitoringService. Se asignaron nombres propios al nuevo contexto, siguiendo Monitoring.
- Falta de credenciales de BD en el entorno. Se limitaron las verificaciones a pruebas sin BD; se registra el bloqueo explícitamente.
- Maven emitió avisos de Lombok/sun.misc.Unsafe y Mockito/agent dinámico en JDK 26. No impidieron compilación ni pruebas; no se alteraron dependencias para silenciarlos. No hubo fallos de compilación ni pruebas durante los incrementos.

## 12. Decisiones técnicas y diferencias concretas

- Oferta nace activa, siguiendo la convención Product y exponiéndolo en OpenAPI; el requisito no fijaba valor inicial. Un descuento no tiene escala de dos decimales impuesta, pues la solicitud solo exige BigDecimal y rango.
- Referencias business/product inmutables al actualizar, igual que el patrón de Catalog; el body PUT contiene únicamente datos comerciales editables.
- Se usa UTC siguiendo Monitoring y una sola captura temporal también para generatedAt, sin scheduler.
- Se reutiliza la estrategia advisory de Monitoring con namespace propio; no se copian tipos AlertType ni reglas de alertas. No se cambia Monitoring para registrar nuevas acciones string.
- Se agrega solo una consulta puntual ACL de Catalog, además de ampliar ProductInventorySnapshot. No hubo otros cambios fuera del contexto salvo sus pruebas y este reporte.
- Orden de listados por ID descendente, estable y sencillo. Vigencia efectiva se evalúa mediante método de dominio con fecha explícita; no se agrega filtro efectivo ni endpoint adicional.
- Se extiende la idempotencia a PUT sin cambios para evitar auditorías repetidas. No se necesita un directorio exceptions con clases vacías.
- Desviación de verificación: se compila con JDK 26/release 25 y se excluye contextLoads por credenciales ausentes. Persistencia y HTTP reales quedan pendientes, no se consideran completados.

## 13. Pendientes

IAM: identidad, sesión confiable, autorización y aislamiento multinegocio. **businessId enviado por cliente es solo dato/filtro, nunca autorización.** GET por ID y mutaciones por ID aún requieren futuro control de acceso; hoy operan con los IDs recibidos siguiendo los contextos existentes. No hay usuarios/roles ficticios ni cambios a IAM.

Migraciones versionadas (p. ej. Flyway) y revisión de constraints/índices, paginación, concurrencia real, rollback conjunto de agregado/auditoría, rendimiento y optimización de consultas por producto, integración Angular y validación con JDK 25 exacto. No se implementan restricciones de solapamiento todavía.

## 14. Estado de continuidad

Código listo para revisión y para verificar con la base local autenticada. No hay procesos de servidor dejados en ejecución. Solo se modificaron los dos contratos ACL mencionados; los demás cambios son archivos nuevos. El documento maestro de contexto permanece intacto; este reporte describe el nuevo estado para integrarlo posteriormente.

Próxima sesión, con DB_PASSWORD proporcionada por el entorno de ejecución y sin publicarla:

```powershell
# JAVA_HOME debe apuntar a un JDK compatible; idealmente JDK 25.
.\mvnw.cmd clean compile
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Usar la configuración existente (puerto 8091, base bodego_db, ddl-auto=update de desarrollo), sin create-drop. Comprobar Swagger en `http://localhost:8091/swagger-ui/index.html` y OpenAPI en `/v3/api-docs`. Inspeccionar `recommendations`/`offers` y columnas sin borrar datos.

En un negocio/productos de prueba autorizados: crear ofertas y comprobar 201, consultar y editar, repetir PUT, desactivar y activar repetidamente; verificar que solo hay auditorías de transiciones/cambios. Preparar stock con los flujos existentes de Operations, generar recomendaciones y repetir generación (segunda respuesta vacía si nada cambia), descartar y repetir descarte, generar otra vez si persiste la condición. Consultar historial y AuditLog vía endpoints de lectura. Revisar mínimo exacto, triple exacto, mínimo cero, lote vencido/hoy/+30/+31, cantidad cero y producto inactivo. Comprobar respuestas 400 por producto de otro negocio o inactivo al crear/reactivar. Usar datos de prueba sin modificar ni eliminar registros ajenos.

Registrar resultados PostgreSQL/HTTP reales antes de declarar completada la verificación funcional persistida; no interpretar los 79 tests seleccionados como evidencia de infraestructura ni autorización multinegocio.


### Actualizaci?n de continuidad ? estilo de c?digo (2026-11-10)

Por solicitud del usuario se revisaron los 45 archivos Java de esta implementaci?n (39 de Strategic Offers, 4 de pruebas y 2 contratos ACL de Catalog). Se ajustaron imports, indentaci?n, espacios, anotaciones, firmas con par?metros en varias l?neas y condiciones con llaves al estilo de los contextos existentes. Los m?todos p?blicos se agrupan antes de auxiliares privados; Offer y Recommendation ubican sus validaciones en un m?todo privado al final, siguiendo Product y Lot. Se conserva la l?gica funcional, los contratos REST, la persistencia y la idempotencia.

Verificaci?n posterior al ajuste: compilaci?n IntelliJ exitosa sin problemas; `.\mvnw.cmd --no-transfer-progress clean compile` con BUILD SUCCESS (238 fuentes, release 25, 16:15:57 -05:00); `.\mvnw.cmd --no-transfer-progress test '-Dtest=!BodegoPlatformApplicationTests'` con BUILD SUCCESS (79 pruebas, 0 fallos, 0 errores, 16:16:17 -05:00). Se mantiene excluida la prueba que requiere arranque completo con PostgreSQL. Los pendientes de infraestructura y seguridad descritos en este reporte siguen vigentes.
