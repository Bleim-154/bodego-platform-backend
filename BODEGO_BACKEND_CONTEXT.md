# BodeGo Backend — Contexto maestro de continuidad

**Versión documental:** 1.2.1  
**Actualización:** 2026-11-10  
**Fuentes:** reportes de Configuración y Shared, Catálogo e Inventario, Operations y seis reportes de Monitoring (Etapas 1–5 y consolidado, corte 2026-11-10).  
**Alcance de evidencia:** consolidación documental de los reportes aportados. Las verificaciones de compilación, pruebas y HTTP de Monitoring fueron reportadas por su chat de implementación; **no se inspeccionó ni ejecutó aquí el repositorio backend**. El reporte original de Monitoring no tuvo conexión PostgreSQL; posteriormente el usuario confirmó verificaciones manuales funcionales y existencia de tablas. No se recibieron logs SQL ni pruebas automatizadas de transacciones.

## 1. Estado ejecutivo

BodeGo es una plataforma de inventarios para bodegas y minimarkets. El backend es un monolito modular Spring Boot con DDD por Bounded Context y CQRS ligero. `shared` está configurado; `catalog` implementa Category, Product y Lot; `operations` implementa InventoryMovement, PhysicalCount, StockAdjustment, Waste y CorrectionRequest; y **`monitoring` implementa Alert y AuditLog, generación de alertas y auditoría integrada con Operations**. Catalog y Operations tienen pruebas manuales reportadas. Monitoring compiló y pasó 56 pruebas, además de verificación HTTP/Swagger sin operaciones de datos. **El usuario confirmó posteriormente persistencia y operaciones funcionales con PostgreSQL; las pruebas avanzadas de concurrencia, rollback y atomicidad siguen pendientes**. Próximos contextos: Strategic Offers e IAM.

| Área | Estado según reportes |
|---|---|
| Infraestructura Spring Boot/PostgreSQL | Configurada; arranque confirmado |
| Shared (auditoría, OpenAPI, naming, errores) | Implementado; pruebas parciales |
| Catalog: Category | CRUD y reglas principales probados en Swagger |
| Catalog: Product | Creación, lectura, edición y activación/desactivación probados |
| Catalog: Lot | Creación, consulta y edición probadas; comandos internos de cantidad sin REST |
| Operations: InventoryMovement, PhysicalCount, StockAdjustment, Waste, CorrectionRequest | Implementados; flujos REST probados manualmente |
| Monitoring: Alert y AuditLog | Implementados; 56 pruebas exitosas y comprobaciones funcionales manuales con PostgreSQL confirmadas por el usuario; concurrencia/rollback pendientes |
| IAM y seguridad | Pendiente |
| Ofertas Estratégicas | Pendiente |
| Integración Operations → Monitoring | Implementada con fachada transaccional y 12 pruebas específicas; rollback de filas no verificado |
| Integración real con Angular | No acreditada por estos reportes |
| Pruebas automatizadas | Operations: una prueba Maven previa; Monitoring: suite posterior de 56 pruebas exitosas (1 contextLoads, 43 Monitoring, 12 Operations/Monitoring); sin pruebas PostgreSQL reales |

## 2. Stack y configuración vigente

- **Java:** `release 25` configurado; el chat de Monitoring compiló con JDK 27 local. Ejecución con JDK 25 exacto no verificada en ese entorno.
- **Spring Boot:** 4.0.9 según `pom.xml` inspeccionado en el chat de Monitoring; no revalidado aquí.
- **Maven:** wrapper `mvnw.cmd`; **Spring Data JPA / Hibernate**, **Spring Web MVC**, **Jakarta Validation**, **Lombok**, **PostgreSQL Driver** y **DevTools**.
- **springdoc-openapi-starter-webmvc-ui:** 3.0.2 (según Shared); **Pluralize:** 1.0.0.
- **IDE:** IntelliJ IDEA; **BD:** PostgreSQL con pgAdmin 4.
- **Nombre/artefacto:** `bodego-platform`; **groupId:** `com.bodego`; **paquete raíz:** `com.bodego.platform`.
- **Puerto HTTP configurado:** `8091`; **BD:** `bodego_db` en `localhost:5432`; **usuario:** `postgres`; contraseña por variable `DB_PASSWORD` (no registrar su valor).
- **Swagger:** `http://localhost:8091/swagger-ui/index.html`; especificación: `/v3/api-docs`.
- **Desarrollo local:** `spring.jpa.hibernate.ddl-auto=update`, `spring.jpa.open-in-view=false`, `spring.jpa.show-sql=true`.
- **Naming JPA:** `com.bodego.platform.shared.infrastructure.persistence.jpa.configuration.strategy.SnakeCaseWithPluralizedTablePhysicalNamingStrategy`.

Configuración base registrada en el reporte de Shared:

```properties
spring.application.name=bodego-platform
server.port=8091
spring.datasource.url=jdbc:postgresql://localhost:5432/bodego_db
spring.datasource.username=postgres
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database=postgresql
spring.jpa.show-sql=true
spring.jpa.open-in-view=false
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.hibernate.naming.physical-strategy=com.bodego.platform.shared.infrastructure.persistence.jpa.configuration.strategy.SnakeCaseWithPluralizedTablePhysicalNamingStrategy
```

Para ejecutar en PowerShell, desde la raíz del backend:

```powershell
$env:DB_PASSWORD = "CONTRASEÑA_LOCAL"
.\mvnw.cmd clean compile
.\mvnw.cmd spring-boot:run
```

No usar `ddl-auto=update` como estrategia de migraciones de producción; evaluar Flyway posteriormente.

## 3. Arquitectura y convenciones

**Monolito modular; DDD por Bounded Context; CQRS ligero** (Commands/Queries y servicios separados; sin event sourcing, bus de mensajes ni bases de lectura/escritura separadas). Referencia académica: Learning Center Platform Mini.

```text
src/main/java/com/bodego/platform/
├── BodegoPlatformApplication.java
├── shared/
│   ├── domain/model/aggregates/AuditableAbstractAggregateRoot.java
│   ├── domain/model/entities/AuditableModel.java
│   ├── infrastructure/documentation/openapi/configuration/OpenApiConfiguration.java
│   ├── infrastructure/persistence/jpa/configuration/strategy/
│   │   └── SnakeCaseWithPluralizedTablePhysicalNamingStrategy.java
│   └── interfaces/rest/
│       ├── GlobalExceptionHandler.java  # incorporado durante Catalog
│       └── resources/MessageResource.java
└── catalog/
    ├── domain/
    │   ├── exceptions/
    │   ├── model/{aggregates,commands,queries}/
    │   └── services/
    ├── application/internal/{commandservices,queryservices}/
    ├── infrastructure/persistence/jpa/repositories/
    └── interfaces/rest/{resources,transform}/
        # CategoriesController, ProductsController, LotsController en interfaces/rest/
```

Cada agregado tiene Commands, Queries, interfaces de servicios, implementaciones, repositorio JPA, Resources, Assemblers y Controller. Los controladores transforman JSON en Commands y devuelven Resources; los servicios de aplicación ejecutan reglas y persistencia. Las referencias entre agregados/contextos se mantienen por **ID**, sin relaciones JPA directas. Evitar acoplamientos circulares entre contextos.

`AuditableAbstractAggregateRoot` aporta `id: Long`, `createdAt: Instant`, `updatedAt: Instant` y hereda de `AbstractAggregateRoot<T>`. `AuditableModel` aporta timestamps sin ID. La auditoría JPA se habilita mediante `@EnableJpaAuditing` en la clase principal. `MessageResource` es `record MessageResource(String message)`. La estrategia de nombres usa snake_case y pluralización. La configuración OpenAPI presenta **BodeGo API**, versión `v1.0.0`.

**Precisión temporal:** el reporte de Shared fue redactado antes de Catalog; sus frases «no existen entidades de negocio» y «manejo de errores pendiente» quedaron superadas por el reporte posterior. No tratarlas como estado vigente.

## 4. Modelo implementado en Catalog

### Category (`categories`)

- `id: Long`, timestamps heredados; `businessId: Long` obligatorio; `name: String` obligatorio, máximo 100; `description: String` opcional, máximo 255.
- Restricción JPA `UNIQUE (business_id, name)`; validación de nombre único por negocio ignorando mayúsculas/minúsculas.
- `updateInformation(name, description)`; eliminación bloqueada si existen productos de la categoría (`409`).
- La referencia a Business es lógica; IAM aún no existe en el backend.

### Product (`products`)

- `id`, timestamps; `businessId: Long`, `categoryId: Long`, `name: String` (100), `description: String` (255), `price: BigDecimal(10,2)`, `minimumStock: Integer`, `active: Boolean`.
- Nace activo; `price >= 0` y máximo dos decimales; `minimumStock >= 0` **no es el stock real**, sino umbral para alertas.
- Categoría existente y perteneciente al mismo negocio; nombre único por negocio sin distinguir mayúsculas/minúsculas.
- Se **activa/desactiva**, no se elimina físicamente; sin `@ManyToOne` con Category.

### Lot (`lots`)

- `id`, timestamps; `businessId: Long`, `productId: Long`, `batchNumber: String` (100), `quantity: Integer >= 0`, `expirationDate: LocalDate` opcional.
- Restricción JPA `UNIQUE (business_id, batch_number)`; producto existente, activo y del mismo negocio al crear.
- **Creación obligatoria con `quantity = 0`** para impedir stock inicial sin movimiento trazable.
- `updateInformation(batchNumber, expirationDate)`, `increaseQuantity(amount)`, `decreaseQuantity(amount)`; cambios requieren cantidad positiva y nunca stock negativo.
- `LotRepository.findWithLockById` usa `PESSIMISTIC_WRITE` para modificaciones de cantidad dentro de transacciones.
- Los comandos de aumentar/disminuir cantidad existen internamente **pero no tienen endpoints REST**; Operations los utiliza mediante la fachada ACL de Catalog.

**Cardinalidades conceptuales:** Business 1:N Category, Product y Lot; Category 1:N Product; Product 1:N Lot. Son referencias por ID y validaciones de aplicación, **no claves foráneas JPA declaradas entre agregados**. El stock total de producto se concibe como suma de cantidades de sus lotes; todavía no hay endpoint de stock agregado.

## 5. Contratos REST implementados

Base: `/api/v1`.

| Método | Ruta | Resultado principal |
|---|---|---|
| POST | `/categories` | Crear; `201` |
| GET | `/categories` | Listar; filtro opcional `businessId`; `200` |
| GET | `/categories/{id}` | Consultar; `200` / `404` |
| PUT | `/categories/{id}` | Actualizar; `200` / `404` |
| DELETE | `/categories/{id}` | Eliminar si no tiene productos; `204` / `404` / `409` |
| POST | `/products` | Crear; `201` |
| GET | `/products` | Listar; **solo uno** de `businessId` o `categoryId`; `200` |
| GET | `/products/{id}` | Consultar; `200` / `404` |
| PUT | `/products/{id}` | Actualizar; `200` / `404` |
| PATCH | `/products/{id}/activate` | Activar; `200` / `404` |
| PATCH | `/products/{id}/deactivate` | Desactivar; `200` / `404` |
| POST | `/lots` | Crear con cantidad cero; `201` |
| GET | `/lots` | Listar; **solo uno** de `businessId` o `productId`; `200` |
| GET | `/lots/{id}` | Consultar; `200` / `404` |
| PUT | `/lots/{id}` | Editar código y vencimiento; `200` / `404` |

**Filtros combinados:** `GET /products?businessId=2&categoryId=5` y la combinación análoga de lotes devuelven **400 sin cuerpo** por diseño actual. Se decidió posponer filtros combinados; no interpretarlo como error de PostgreSQL.

**Resources:**

- Category: crear `{businessId,name,description}`, actualizar `{name,description}`, salida `{id,businessId,name,description}`.
- Product: crear `{businessId,categoryId,name,description,price,minimumStock}`, actualizar sin `businessId`, salida con `id` y `active` además de los campos de negocio.
- Lot: crear `{businessId,productId,batchNumber,quantity,expirationDate}`, actualizar `{batchNumber,expirationDate}`, salida `{id,businessId,productId,batchNumber,quantity,expirationDate}`.

POST y PUT usan `@Valid` y validaciones Jakarta. No hay `DELETE /products` ni endpoints REST para modificar `Lot.quantity`.

## 6. Clases principales de Catalog

- **Agregados:** `Category`, `Product`, `Lot`.
- **Commands:** `CreateCategoryCommand`, `UpdateCategoryCommand`, `DeleteCategoryCommand`; `CreateProductCommand`, `UpdateProductCommand`, `ActivateProductCommand`, `DeactivateProductCommand`; `CreateLotCommand`, `UpdateLotCommand`, `IncreaseLotQuantityCommand`, `DecreaseLotQuantityCommand`.
- **Queries:** `GetAllCategoriesQuery`, `GetCategoryByIdQuery`, `GetCategoriesByBusinessIdQuery`; `GetAllProductsQuery`, `GetProductByIdQuery`, `GetProductsByBusinessIdQuery`, `GetProductsByCategoryIdQuery`; `GetAllLotsQuery`, `GetLotByIdQuery`, `GetLotsByProductIdQuery`, `GetLotsByBusinessIdQuery`.
- **Interfaces:** `CategoryCommandService`, `CategoryQueryService`, `ProductCommandService`, `ProductQueryService`, `LotCommandService`, `LotQueryService`.
- **Implementaciones:** `CategoryCommandServiceImpl`, `CategoryQueryServiceImpl`, `ProductCommandServiceImpl`, `ProductQueryServiceImpl`, `LotCommandServiceImpl`, `LotQueryServiceImpl`.
- **Repositorios:** `CategoryRepository`, `ProductRepository`, `LotRepository`.
- **Controladores:** `CategoriesController`, `ProductsController`, `LotsController`.
- **Excepciones específicas:** `CategoryAlreadyExistsException`, `CategoryHasProductsException`, `ProductAlreadyExistsException`.
- **Transformación REST:** recursos de creación/actualización/salida y tres assemblers por agregado (create→command, update→command, entity→resource).

**No implementado deliberadamente:** `GetProductsByBusinessIdAndCategoryIdQuery` y su flujo de filtrado combinado.

## 7. Errores HTTP y validaciones

`shared.interfaces.rest.GlobalExceptionHandler` (`@RestControllerAdvice`) incorporado en Catalog:

- `IllegalArgumentException` → `400` con `{"message":"..."}`.
- `MethodArgumentNotValidException` → `400` con el primer error de campo.
- `CategoryAlreadyExistsException`, `CategoryHasProductsException`, `ProductAlreadyExistsException` → `409`.
- Lote duplicado → `IllegalArgumentException` / `400` (aún no excepción de conflicto específica).

Los errores de filtros combinados producen `400` sin cuerpo, fuera del formato habitual del handler.

## 8. Evidencia de pruebas y límites

**Confirmado por el usuario en los chats:** compilación Maven `BUILD SUCCESS`, arranque Spring Boot, conexión PostgreSQL, creación/consulta de tablas `categories`, `products`, `lots`, operaciones manuales de Category/Product/Lot desde Swagger, conflicto `409` por categoría con productos y por producto duplicado (incluidas variantes de mayúsculas), y registro del mismo nombre en negocios distintos.

**No comprobado directamente al consolidar:** código del repositorio, versión exacta de Spring Boot, índices SQL únicos normalizados, concurrencia real, integridad de FK, seguridad multinegocio e integración Angular-backend. **Actualización 2026-11-10:** Operations reportó pruebas manuales de movimientos de inventario y una prueba Maven exitosa, sin suite de integración de Operations.

Se propusieron estos índices SQL (su presencia efectiva **no está confirmada**):

```sql
CREATE UNIQUE INDEX IF NOT EXISTS ux_categories_business_normalized_name
ON categories (business_id, LOWER(TRIM(name)));
CREATE UNIQUE INDEX IF NOT EXISTS ux_products_business_normalized_name
ON products (business_id, LOWER(TRIM(name)));
```

La unicidad de `Lot.batchNumber` está definida por negocio en JPA; decidir si debe ser por negocio o por producto y si debe normalizarse el texto.

## 9. Decisiones vigentes y precauciones

1. Mantener `com.bodego.platform` y el estilo Learning Center.
2. Mantener un solo Bounded Context `catalog` para Category, Product y Lot.
3. Usar referencias por ID entre agregados y contextos; no introducir asociaciones JPA ni ciclos entre contextos sin revisión.
4. Conservar Commands/Queries y servicios separados.
5. Desactivar Product en lugar de eliminarlo.
6. Crear Lot con cantidad cero; registrar entradas/salidas mediante movimientos trazables.
7. No publicar endpoints de cambio directo de cantidad de lote: Operations registra movimientos mediante ACL transaccional; AuditLog de negocio está implementado en Monitoring e integrado en Operations, con registro funcional confirmado manualmente; rollback conjunto pendiente.
8. Mantener los filtros combinados pospuestos hasta que se decida cambiar el contrato.
9. No confiar en `businessId` enviado por el cliente como autorización: IAM y aislamiento de negocio están pendientes.
10. Usar `ddl-auto=update` solo para desarrollo local.

## 10. Bounded Context Operations — implementado (2026-11-10)

**Estado y evidencia:** cinco agregados implementados y probados manualmente desde Swagger según confirmación del usuario. No se inspeccionó el repositorio local ni se verificaron todas las respuestas negativas. Las pruebas de integración de Operations se pausaron expresamente. `\.\mvnw.cmd test` informó **1 prueba, 0 fallos, 0 errores y BUILD SUCCESS**; no acredita rollback, concurrencia o aislamiento entre negocios.

### 10.1 Arquitectura e integración con Catalog

- Paquete: `com.bodego.platform.operations`; capas `domain/model/{aggregates,valueobjects,commands,queries}`, `domain/services`, `application/internal/{commandservices,queryservices,outboundservices/acl}`, `infrastructure/persistence/jpa/repositories` e `interfaces/rest/{resources,transform}`.
- Agregados: `InventoryMovement`, `PhysicalCount`, `StockAdjustment`, `Waste`, `CorrectionRequest`; cada uno tiene servicios Command/Query, repositorio JPA y controlador REST.
- **ACL entre BC:** `operations/application/internal/outboundservices/acl/ExternalCatalogService.java` consume `catalog/interfaces/acl/CatalogContextFacade.java`. Operations no modifica `Lot` mediante su repositorio directamente.
- Contrato ACL: `registerMovement(businessId,lotId,entry,quantity)`; `getLotQuantity(businessId,lotId)`; `validateLotStock(businessId,lotId,expectedQuantity)`.
- Catalog incorporó `ValidateLotStockCommand` y su handler con `@Transactional(propagation = MANDATORY)`: valida stock esperado con `LotRepository.findWithLockById` (`PESSIMISTIC_WRITE`) en la misma transacción del ajuste. La lectura de `getLotQuantity` para conteos no bloquea.
- Los flujos que cambian stock usan `@Transactional`: movimiento + lote; merma + movimiento + lote; ajuste + movimiento + lote; aprobación de solicitud + ajuste + movimiento + lote. **Atomicidad esperada, sin pruebas automatizadas de rollback todavía**.
- Referencias entre agregados y BC por `Long` (`businessId`, `lotId`, `physicalCountId`), sin asociaciones JPA directas acreditadas.

### 10.2 Modelo y reglas de negocio

| Agregado / tabla | Campos y reglas principales |
|---|---|
| `InventoryMovement` / `inventory_movements` | `businessId`, `lotId`, `type: ENTRY/EXIT`, `quantity > 0`, `reason` (máx. 255); ENTRY aumenta y EXIT reduce `Lot.quantity`; valida negocio, lote y stock disponible. Historial sin edición REST. |
| `PhysicalCount` / `physical_counts` | `businessId`, `lotId`, `systemQuantity`, `countedQuantity`, `difference`, `observation` (500), `countedAt`; `difference = countedQuantity - systemQuantity`; captura snapshot del stock sin alterarlo. |
| `StockAdjustment` / `stock_adjustments` | `businessId`, `physicalCountId`, `lotId`, `previousQuantity`, `newQuantity`, `difference`, `reason` (255), `adjustedAt`; diferencia no nula ni cero; **un ajuste máximo por conteo** (`UNIQUE physicalCountId`); comprueba stock actual contra snapshot y crea movimiento ENTRY/EXIT compensatorio. |
| `Waste` / `wastes` | `businessId`, `lotId`, `quantity > 0`, `reason: EXPIRED/DAMAGED/DETERIORATED/OTHER`, `observation` (500), `registeredAt`; genera movimiento EXIT con motivo `WASTE: <REASON>`. |
| `CorrectionRequest` / `correction_requests` | `businessId`, `physicalCountId`, `reason` (1–500), `status: PENDING/APPROVED/REJECTED`, `requestedAt`, `reviewedAt`, `reviewComment`; solo un PENDING por conteo según validación de aplicación; aprobar aplica StockAdjustment, rechazar no cambia stock; revisión bloquea la solicitud con `PESSIMISTIC_WRITE`. |

**Relaciones conceptuales:** Lot 1:N InventoryMovement/PhysicalCount/Waste/StockAdjustment; PhysicalCount 1:N CorrectionRequest (histórico, a lo sumo una pendiente según regla); PhysicalCount 1:0..1 StockAdjustment. Son relaciones lógicas por ID, no FKs verificadas. `StockAdjustment` no documenta `correctionRequestId`.

### 10.3 REST de Operations

Base: `/api/v1`. Las rutas siguientes fueron descritas como implementadas; los GET listados permiten solo los filtros indicados, salvo la combinación especial de solicitudes.

| Método | Ruta | Función / filtros |
|---|---|---|
| POST | `/inventory-movements` | Registrar ENTRY/EXIT |
| GET | `/inventory-movements` | Todos, `businessId` **o** `lotId` |
| GET | `/inventory-movements/{id}` | Consultar por ID |
| POST | `/physical-counts` | Registrar conteo sin modificar stock |
| GET | `/physical-counts` | Todos, `businessId` **o** `lotId` |
| GET | `/physical-counts/{id}` | Consultar por ID |
| POST | `/stock-adjustments` | Aplicar ajuste desde conteo |
| GET | `/stock-adjustments` | Todos, `businessId` **o** `lotId` **o** `physicalCountId` |
| GET | `/stock-adjustments/{id}` | Consultar por ID |
| POST | `/wastes` | Registrar merma y movimiento EXIT |
| GET | `/wastes` | Todos, `businessId` **o** `lotId` |
| GET | `/wastes/{id}` | Consultar por ID |
| POST | `/correction-requests` | Crear solicitud |
| PATCH | `/correction-requests/{id}/review` | Aprobar o rechazar (`decision: APPROVED/REJECTED`) |
| GET | `/correction-requests` | Todos, `businessId`, `physicalCountId` o `businessId` + `status` |
| GET | `/correction-requests/{id}` | Consultar por ID |

**Ejemplos de payloads documentados:**

```json
{"businessId":1,"lotId":1,"type":"EXIT","quantity":2,"reason":"Salida de inventario"}
```

```json
{"businessId":1,"lotId":1,"countedQuantity":15,"observation":"Conteo de almacén"}
```

```json
{"businessId":1,"physicalCountId":1,"reason":"Diferencia validada"}
```

```json
{"businessId":1,"lotId":1,"quantity":4,"reason":"DAMAGED","observation":"Unidades dañadas"}
```

```json
{"businessId":1,"physicalCountId":1,"reason":"Diferencia en conteo"}
```

```json
{"businessId":1,"decision":"APPROVED","reviewComment":"Diferencia verificada"}
```

Los POST devuelven `201`, GET y PATCH exitosos `200` según el reporte. No existen PUT/DELETE de estos agregados ni endpoints REST para modificar directamente cantidades de Lot. Los filtros incompatibles se rechazan; errores HTTP de dominio requieren revisión de consistencia.

### 10.4 Verificaciones y pendientes específicos

**Confirmado por usuario:** compilación/arranque incremental; movimiento modifica lote; conteo no modifica stock; creación y consulta de ajustes, mermas y solicitudes; revisión de solicitudes. `mvnw.cmd test` ejecutó **una sola prueba exitosa** (2026-11-10 12:54:27 -05:00). **No confirmados:** suite de integración, concurrencia, rollback, permisos y aislamiento multinegocio.

**Riesgos prioritarios:**

1. **IAM no implementado:** `businessId` proviene del cliente; no hay autorización ADMIN para aplicar ajustes o revisar solicitudes ni aislamiento seguro en lecturas.
2. **Solicitudes PENDING concurrentes:** `existsByPhysicalCountIdAndStatus` no evita carreras. Evaluar índice único parcial `physical_count_id WHERE status='PENDING'` en PostgreSQL, con migración controlada.
3. **Rollback y concurrencia:** probar stock insuficiente, ajuste con snapshot desactualizado, aprobación fallida, doble ajuste y salidas simultáneas; comprobar que no quedan cambios parciales.
4. **Conteo sin bloqueo:** `systemQuantity` es una foto que puede quedar obsoleta; al ajustar se valida contra el stock actual bajo lock.
5. **Integridad y errores HTTP:** revisar FKs, manejo de `DataIntegrityViolationException`, `IllegalStateException` y consistencia de `400/404/409`.
6. **Contrato Angular:** frontend podría usar `IN/OUT` e IDs string; backend usa `ENTRY/EXIT` y `Long`. Validar adaptación antes de integrar.
7. **Trazabilidad de negocio:** auditoría JPA `createdAt/updatedAt` **no equivale** a AuditLog; Monitoring ya implementa AuditLog y Operations registra acciones, con persistencia funcional confirmada manualmente; falta probar rollback con PostgreSQL.
8. **Flujo de ajustes:** decidir si ADMIN puede aplicar ajustes directos mientras existe una solicitud pendiente; documentar la política.
9. **Base de pruebas:** `bodego_test_db` y `application-test.properties` se propusieron; no está acreditado que la única prueba Maven utilizara el perfil `test`. `create-drop` solo sobre base de pruebas, nunca sobre `bodego_db`.

## 10A. Bounded Context Monitoring — implementado (2026-11-10)

**Estado documentado:** completadas Etapas 1–5 y reporte consolidado. **IMPLEMENTADO:** Alert, generación idempotente por negocio, consultas/resolución, AuditLog inmutable, fachada interna y auditoría desde Operations. **PROBADO EN EL CHAT DE IMPLEMENTACIÓN:** `clean compile` (199 fuentes, `release 25`), suite completa de **56 pruebas sin fallos**, arranque Spring/HTTP sin conexión funcional a BD, Swagger/OpenAPI con 6 contratos y 11 comprobaciones HTTP de validaciones/métodos. **NO VERIFICADO EN AQUELLA EJECUCIÓN:** JDK 25 exacto, conexión/persistencia PostgreSQL, DDL, consultas JPA reales, operaciones HTTP válidas sobre datos, concurrencia/bloqueo y rollback de filas. **ACTUALIZACIÓN POSTERIOR:** el usuario confirmó manualmente tablas y flujos funcionales con PostgreSQL (véase 10A.6); concurrencia y rollback siguen sin verificarse. Se usó JDK 27 local y `ddl-auto=none` solo en los procesos de prueba; la conexión `localhost:5432` fue rechazada y faltaba `DB_PASSWORD` en ese entorno. No se modificó `pom.xml`, Shared ni configuración.

### 10A.1 Modelo y arquitectura

- Paquete `com.bodego.platform.monitoring`; estructura `domain/model/{aggregates,valueobjects,commands,queries}`, `domain/services`, `application/internal/{commandservices,queryservices,outboundservices/acl}`, `infrastructure/persistence/jpa/repositories`, `interfaces/{acl,rest}` y `interfaces/rest/{resources,transform}`.
- `Alert`: `businessId`, `productId`, `lotId` opcional, `type: LOW_STOCK/EXPIRING_SOON/EXPIRED`, `severity: LOW/MEDIUM/HIGH`, `message` (255), `resolved`, `generatedAt`, `resolvedAt` opcional, además de `id/createdAt/updatedAt`. Resolución idempotente: la segunda resolución no altera la primera fecha; se utiliza bloqueo `PESSIMISTIC_WRITE` al resolver.
- `AuditLog`: `businessId`, `userId` opcional, `action` (100), `entityType` (100), `entityId`, `description` (1000), `occurredAt` e identificador/timestamps heredados. Inmutable para actualizaciones de negocio gestionadas con Hibernate (`@Immutable`, `updatable=false`), sin escritura REST. No protege contra SQL externo o borrados internos arbitrarios.
- Las referencias entre contextos son IDs, no relaciones JPA directas. `businessId` y filtros no equivalen a autorización; `userId` permanece `null` hasta implementar IAM.
- Commands: `ResolveAlertCommand`, `GenerateAlertsCommand`, `RecordAuditLogCommand`; Queries: `GetAlertByIdQuery`, `GetAlertsQuery`, `GetAuditLogByIdQuery`, `GetAuditLogsQuery`.
- Repositorios: `AlertRepository`, `AuditLogRepository`; componente `AlertGenerationLock`; fachada `MonitoringContextFacade`.

### 10A.2 Contratos REST

Base `/api/v1`:

| Método | Ruta | Contrato |
|---|---|---|
| POST | `/alerts/generate` | Body `{ "businessId": 1 }`; `200` con alertas **nuevas** o `[]` |
| GET | `/alerts` | Filtros opcionales `businessId`, `productId`, `lotId`, `resolved`, combinables con AND |
| GET | `/alerts/{id}` | Consultar; `200` / `404` |
| PATCH | `/alerts/{id}/resolve` | Sin body; resolución idempotente; `200` / `404` |
| GET | `/audit-logs` | Filtros opcionales `businessId`, `entityType`, `entityId`, combinables con AND |
| GET | `/audit-logs/{id}` | Consultar; `200` / `404` |

IDs no positivos y solicitudes inválidas dan `400`. No existen POST arbitrarios de Alert ni endpoints públicos de escritura de AuditLog; métodos no permitidos devuelven `405`. Alert ordena por `generatedAt DESC, id DESC`; AuditLog por `occurredAt DESC, id DESC`. La verificación HTTP real se limitó a Swagger/OpenAPI y 11 solicitudes inválidas/no permitidas; **no se ejecutaron solicitudes válidas que necesitaran datos**.

### 10A.3 Reglas de generación de Alert

- `LOW_STOCK`: suma de `Lot.quantity` de **todos** los lotes del producto, usando `long`, estrictamente menor que `minimumStock`; alerta a nivel producto (`lotId=null`), severidad `MEDIUM`.
- `EXPIRING_SOON`: vencimiento entre hoy y hoy + 7 días inclusive, severidad `MEDIUM`; `EXPIRED`: vencimiento anterior a hoy, severidad `HIGH`. Fecha de evaluación en UTC, capturada una vez por generación.
- Se incluyen productos inactivos y lotes de cantidad cero; los lotes sin fecha no generan alerta por vencimiento. No se resuelven alertas automáticamente al desaparecer la condición. Una alerta resuelta puede generarse nuevamente si persiste la condición; el historial se conserva.
- No duplicar una alerta activa con la misma combinación negocio/producto/lote/tipo. El flujo implementado adquiere **bloqueo advisory transaccional PostgreSQL por negocio** antes de consultar Catalog, con `READ_COMMITTED`, namespace `1297043017` y clave `Long.hashCode(businessId)`. Usa `SELECT count(*) FROM pg_advisory_xact_lock(:namespace, :businessKey)`. La eficacia real del bloqueo **no se probó con PostgreSQL**; tampoco existe índice único parcial, y otros creadores deben respetar el protocolo.
- Monitoring consulta Catalog mediante `ExternalCatalogService` y `CatalogContextFacade`, que fue ampliada con `getProductsByBusinessId` y `getLotsByProductId`. Catalog entrega `ProductInventorySnapshot(id,businessId,minimumStock)` y `LotInventorySnapshot(id,businessId,productId,quantity,expirationDate)`; no expone entidades JPA ni permite modificar stock desde Monitoring. El bean del adaptador se llama `monitoringExternalCatalogService` para evitar colisión con Operations.

### 10A.4 Auditoría Operations → Monitoring

- `operations/application/internal/outboundservices/acl/ExternalMonitoringService` delega en `MonitoringContextFacade.recordAction(businessId,userId,action,entityType,entityId,description)`; Operations pasa `userId=null` mientras IAM no existe.
- La fachada y el servicio de comando AuditLog requieren transacción activa mediante `Propagation.MANDATORY`; se propagan errores, sin `REQUIRES_NEW`, de modo que se **espera** atomicidad de la operación y su auditoría (rollback de filas todavía sin verificar).
- Se modificaron cinco handlers existentes de Operations, después de `save`/`flush`, sin cambiar endpoints ni Commands/Resources:

| Handler | Acción de auditoría |
|---|---|
| InventoryMovement | `INVENTORY_MOVEMENT_REGISTERED` |
| PhysicalCount | `PHYSICAL_COUNT_CREATED` |
| StockAdjustment | `STOCK_ADJUSTMENT_APPLIED` |
| Waste | `WASTE_REGISTERED` |
| CorrectionRequest (crear) | `CORRECTION_REQUEST_CREATED` |
| CorrectionRequest (aprobar) | `CORRECTION_REQUEST_APPROVED` |
| CorrectionRequest (rechazar) | `CORRECTION_REQUEST_REJECTED` |

Una merma o ajuste registra también el movimiento generado como acción separada; aprobar una solicitud registra solicitud, ajuste y movimiento en la misma transacción. No se duplican las acciones en los handlers superiores. Las descripciones usan IDs/estados/cantidades, sin copiar observaciones o payloads sensibles.

### 10A.5 Evidencia, cambios y pendientes

- `\.\mvnw.cmd clean compile`: `BUILD SUCCESS`, 199 fuentes, Java `release 25`, Spring Boot 4.0.9.
- `\.\mvnw.cmd test '-Dspring.jpa.hibernate.ddl-auto=none'`: `BUILD SUCCESS`, **56 pruebas** (1 contextLoads, 43 Monitoring, 12 OperationsMonitoringTests), sin fallos, errores ni omitidas. Las pruebas de transacción usan gestor sin BD y persistencia simulada; no prueban rollback SQL.
- Servidor temporal puerto `18091`, con `ddl-auto=none`: Swagger UI y `/v3/api-docs` respondieron `200`; 11 comprobaciones HTTP devolvieron los `400/405` esperados. Servidor detenido al concluir.
- `CatalogContextFacade.java` se amplió en Etapa 2; se añadieron dos records snapshot. En Etapa 4 se modificaron cinco command services de Operations y se añadieron adaptador y pruebas. Sin commits, merges, pushes ni cambios de rama al cierre del reporte; archivos nuevos aún sin seguimiento en Git.
- **Alta prioridad:** crear/usar PostgreSQL de pruebas de forma segura; automatizar DDL/JPA y consultas reales, casos límite `lotId=null`, generación concurrente, rollback conjunto stock/operación/log, resolución concurrente y aislamiento entre negocios. Las verificaciones funcionales manuales ya fueron confirmadas. No usar `create-drop` sobre `bodego_db`.
- **Pendientes generales:** IAM (identidad, permisos ADMIN y negocio confiable), migraciones, paginación, optimización de consultas de lotes, integración Angular y pruebas de Operations con BD. La ubicación reportada de `application-test.properties` es no convencional (`src/test/java/resources/`); verificar antes de modificar.

**Siguiente etapa funcional:** Strategic Offers o IAM, según coordinación del equipo. Monitoring ya no debe figurar como pendiente de implementación; solo quedan sus verificaciones de persistencia y endurecimiento.

### 10A.6 Actualización de verificaciones PostgreSQL — 2026-11-10

**Confirmado posteriormente por el usuario (verificación manual):**

1. Las tablas `alerts` y `audit_logs` aparecen en PostgreSQL. Se ejecutó una consulta de presencia de tablas; no se compartió salida completa de columnas ni constraints.
2. Los endpoints permiten guardar y consultar registros reales.
3. La generación y resolución de alertas funcionan.
4. Las operaciones de inventario generan registros de auditoría.

**Pendiente:** punto 5, pruebas avanzadas de concurrencia, rollback y atomicidad de transacciones (incluida reversión conjunta de stock, operación y auditoría). La confirmación manual no reemplaza pruebas automatizadas ni acredita cada caso límite o constraint de base de datos.

**Nota cronológica:** las limitaciones de PostgreSQL del reporte de Etapa 5 describen el entorno de ejecución de ese reporte, anterior a estas comprobaciones manuales. No deben interpretarse como estado funcional vigente.

## 11. Procedimiento de actualización del documento

Al cerrar cada chat especializado:

1. Solicitar un reporte técnico de continuidad con **implementado, probado, pendiente y decisiones**.
2. Integrarlo en este documento reemplazando estados superados, sin concatenar reportes completos.
3. Registrar fecha y versión; anotar discrepancias o puntos no verificados.
4. Guardar la copia oficial en la raíz del repositorio backend y actualizar la copia de referencia del proyecto ChatGPT.
5. Si es posible, anotar el commit Git que corresponde al estado documentado.

**Fuente de verdad de la implementación:** repositorio y base de datos. **Fuente de continuidad:** este Markdown. Ante discrepancias, inspeccionar el código antes de cambiar contratos.

## 12. Historial documental

| Versión | Fecha | Cambios |
|---|---|---|
| 1.0 | 2026-11-08 | Consolidación inicial de Shared y Catalog; Category, Product y Lot. |
| 1.1 | 2026-11-10 | Incorporado Operations (cinco agregados), ACL Catalog↔Operations, 16 endpoints REST, pruebas manuales, riesgos de concurrencia y siguiente BC Monitoring. |
| 1.2 | 2026-11-10 | Incorporado Monitoring (Alert/AuditLog), 6 endpoints, reglas de generación, ACL Catalog y auditoría Operations, 56 pruebas, verificación Swagger/HTTP y pendientes PostgreSQL/IAM. |
| 1.2.1 | 2026-11-10 | Usuario confirmó tablas y flujos funcionales de Monitoring con PostgreSQL; quedan pendientes concurrencia, rollback y atomicidad automatizados. |
