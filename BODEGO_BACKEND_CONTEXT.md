# BodeGo Backend — Contexto maestro de continuidad

**Versión documental:** 1.0  
**Actualización:** 2026-11-08  
**Fuentes:** reportes de Configuración y Shared y de Catálogo e Inventario, aportados por los chats de desarrollo.  
**Alcance de evidencia:** avances comunicados y pruebas manuales confirmadas por el usuario; **no se inspeccionó el repositorio backend ni se ejecutó su código** para elaborar esta versión.

## 1. Estado ejecutivo

BodeGo es una plataforma de inventarios para bodegas y minimarkets. El backend es un monolito modular Spring Boot con DDD por Bounded Context y CQRS ligero. La infraestructura y el paquete `shared` están configurados. En `catalog` se han implementado y probado manualmente **Category, Product y Lot** con endpoints REST, servicios, JPA y PostgreSQL. El siguiente trabajo prioritario es **Operaciones Diarias y Mermas**, empezando por el flujo de movimientos de inventario que debe actualizar cantidades de lotes con trazabilidad.

| Área | Estado según reportes |
|---|---|
| Infraestructura Spring Boot/PostgreSQL | Configurada; arranque confirmado |
| Shared (auditoría, OpenAPI, naming, errores) | Implementado; pruebas parciales |
| Catalog: Category | CRUD y reglas principales probados en Swagger |
| Catalog: Product | Creación, lectura, edición y activación/desactivación probados |
| Catalog: Lot | Creación, consulta y edición probadas; comandos internos de cantidad sin REST |
| Operaciones Diarias y Mermas | Pendiente |
| IAM y seguridad | Pendiente |
| Ofertas Estratégicas | Pendiente |
| Auditoría y Seguimiento | Pendiente |
| Integración real con Angular | No acreditada por estos reportes |
| Pruebas automatizadas | No acreditadas |

## 2. Stack y configuración vigente

- **Java:** JDK 25, confirmado en el chat de Shared.
- **Spring Boot:** familia 4.0.x; versión exacta pendiente de comprobar en `pom.xml`.
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
- Los comandos de aumentar/disminuir cantidad existen internamente **pero no tienen endpoints REST**.

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

**No comprobado directamente al consolidar:** código del repositorio, versión exacta de Spring Boot, índices SQL únicos normalizados, pruebas automatizadas, concurrencia real, integridad de FK, seguridad multinegocio, integración Angular-backend y ejecución de movimientos de inventario.

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
7. No publicar endpoints de cambio directo de cantidad de lote antes de integrar movimientos y auditoría.
8. Mantener los filtros combinados pospuestos hasta que se decida cambiar el contrato.
9. No confiar en `businessId` enviado por el cliente como autorización: IAM y aislamiento de negocio están pendientes.
10. Usar `ddl-auto=update` solo para desarrollo local.

## 10. Próxima etapa: Operaciones Diarias y Mermas

**Antes de implementar:** revisar el frontend Angular existente y los contratos reales para `InventoryMovement`, `PhysicalCount`, `StockAdjustment`, `Waste` y `CorrectionRequest`. Son **nombres conceptuales**, todavía no clases Java confirmadas.

**Prioridad técnica:** definir un flujo transaccional que registre `InventoryMovement` y actualice `Lot.quantity` de forma atómica, con trazabilidad, validación de pertenencia a negocio y protección ante concurrencia. Revisar la frontera entre `catalog` y operaciones para no generar dependencias circulares. El reporte de Catalog no acredita que ese flujo ya exista.

**Después:** conteos físicos, ajustes, mermas y solicitudes de corrección; IAM, ofertas y monitoreo en etapas posteriores según prioridades.

### Pendientes técnicos

- Diseñar el contrato entre operaciones y `LotCommandService` sin dependencias circulares.
- Definir ingreso inicial mediante movimiento (Lot nace en cero).
- Probar transacciones, bloqueo pesimista y concurrencia.
- Revisar autorización multinegocio en consultas y mutaciones.
- Comprobar índices únicos normalizados y añadir manejo de `DataIntegrityViolationException` cuando corresponda.
- Revisar integridad de Category/Product/Lot, política de lotes duplicados y fechas de vencimiento.
- Añadir pruebas automatizadas y pruebas de integración Angular.
- Considerar migraciones con Flyway antes de entornos estables.

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
| 1.0 | 2026-11-08 | Consolidación inicial de los reportes de Shared y Catalog; estado actualizado a Category, Product y Lot; riesgos y contratos REST registrados. |
