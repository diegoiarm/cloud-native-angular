# cloud-native-angular — DSY1107

Proyecto conjunto **Cloud Native** de la asignatura DSY1107. Unifica en un solo
repositorio los 3 entregables del grupo:

| Componente | Stack | Puerto | Descripción |
|---|---|---|---|
| `pedidos-api` | Spring Boot (Java 21) | **8080** | Microservicio de pedidos: estados, reglas de negocio y stock vía catálogo. Protegido con JWT de Entra ID. |
| `catalogo-api` | Spring Boot (Java 21) | **8081** | Microservicio de catálogo: productos, precios, stock. Protegido con JWT de Entra ID. |
| `cloud-native-angular/` | Angular 21 + MSAL | **4200** | SPA (frontend) con login Microsoft, rutas protegidas y rol desde el claim `roles`. |
| `infra/` | OpenAPI | — | Plantilla del API Gateway (AWS) lista para importar. |

La autenticación/ autorización es **end-to-end**: el frontend obtiene un Access Token
de Microsoft Entra ID, las APIs lo **validan** (issuer + audience) y autorizan por
**scope `Pedidos.Read`** y por **App Roles** (`Admin`, `Operador`, `Cliente`).

```
Navegador (SPA Angular :4200)
   │  Access Token (OAuth2 / Authorization Code + PKCE)
   ▼
pedidos-api (:8080) ──forward token──►  catalogo-api (:8081)
   │  valida JWT (aud + iss)                 valida JWT (aud + iss)
   ▼
PostgreSQL (pedidosdb)               PostgreSQL (catalogodb)
```

## 1. Requisitos

- **Java 21** (los `pom.xml` usan `java.version = 21`)
- **Maven** (con `./mvnw` incluido no hace falta instalarlo)
- **Node.js 22+ / npm 11** para el frontend
- **PostgreSQL** en `localhost:5432` (o una instancia RDS en la nube)
  - `pedidos-api` → base **`pedidosdb`**
  - `catalogo-api` → base **`catalogodb`**

> El proyecto está pensado para despliegue cloud (EC2/RDS). Las credenciales de base
> de datos se pasan por **variables de entorno**, nunca van en el código.

## 2. Instalación después de clonar

```bash
# Catalogo API
cd catalogo-api
./mvnw -q clean compile       # Windows: mvnw.cmd -q clean compile

# Pedidos API
cd ../pedidos-api
./mvnw -q clean compile

# Frontend Angular
cd ../cloud-native-angular
npm install
```

Las dependencias de Maven y npm se descargan solas en la primera ejecución.

## 3. Configuración previa (Microsoft Entra ID)

Definir en el tenant (tenantId `413875c7-b2d6-4bc6-93ee-071e4f43a25e`):

1. **App Registration SPA** (`198c0fac-457a-43c6-a8a8-f3b90ba14dc1`)
   - Redirect URI: `http://localhost:4200`
   - API permissions → agregar el scope **`Pedidos.Read`** de la API `api://fe49fbff-...`
2. **App Registration API** (`fe49fbff-96c3-4146-b54e-a81b1d2a2379`)
   - **Exponer una API** → App ID URI `api://fe49fbff-...` y scope `Pedidos.Read`
   - **App roles**: crear exactamente `Admin`, `Operador`, `Cliente` (valor con mayúscula inicial)
   - Asignar el rol correspondiente a cada usuario del grupo
3. **Otorgar consentimiento de administrador** en la API permissions de la SPA
   (sin esto las cuentas invitadas no obtienen el token).

Estos valores ya están configurados en `cloud-native-angular/src/environments/environment.ts`.

## 4. Levantar el proyecto local

### 4.1) Bases de datos (PostgreSQL)

```sql
CREATE DATABASE pedidosdb;
CREATE DATABASE catalogodb;
```

### 4.2) CATALOGO API (puerto 8081)

```bash
cd catalogo-api
export DB_HOST=localhost DB_NAME=catalogodb DB_USER=postgres DB_PASSWORD=<clave>
./mvnw spring-boot:run
```

### 4.3) PEDIDOS API (puerto 8080)

```bash
cd pedidos-api
export DB_HOST=localhost DB_NAME=pedidosdb DB_USER=postgres DB_PASSWORD=<clave>
export CATALOGO_URL=http://localhost:8081
./mvnw spring-boot:run
```

> `pedidos-api` llama a `catalogo-api` **reenviando el token del usuario**, por eso
> ambos microservicios validan el mismo issuer y audience.

### 4.4) FRONTEND (puerto 4200)

```bash
cd cloud-native-angular
npm start        # o: npx ng serve
```

Abrir `http://localhost:4200` e iniciar sesión con la cuenta de Microsoft.

### 4.5) Verificación rápida

```bash
curl http://localhost:8080/api/publico     # → {"mensaje":"Endpoint público operativo" ...}
curl http://localhost:8081/api/catalog/products   # sin token → 401
```

## 5. Matriz de permisos (Admin / Operador / Cliente)

Los roles se toman del claim `roles` del Access Token (App Roles en Entra ID) y se
aplican en los controladores con `@PreAuthorize`. Todo endpoint exige además el scope
`Pedidos.Read` salvo `/api/publico`.

| Operación | Endpoint | Admin | Operador | Cliente |
|---|:--|:--:|:--:|:--:|
| Listar pedidos | `GET /api/orders` | ✅ **todos** | ✅ **todos** | ✅ **solo propios** |
| Ver pedido | `GET /api/orders/{id}` | ✅ | ✅ | ✅ solo propio |
| Crear pedido | `POST /api/orders` | ❌ | ✅ | ✅ |
| Cambiar estado | `PUT /api/orders/{id}/status` | ✅ | ✅ | ❌ |
| Listar productos | `GET /api/catalog/products[/{id}]` | ✅ | ✅ | ✅ |
| Crear/editar producto | `POST`/`PUT /api/catalog/products` | ✅ | ❌ | ❌ |
| Descontar/reponer stock | `POST /api/catalog/stock/...` | ✅ | ✅ | ❌ |
| Ver claims del token | `GET /api/me` | ✅ | ✅ | ✅ |
| Endpoint público | `GET /api/publico` | público | público | público |

> **Nota de diseño**: en `pedidos-api`, `POST /api/orders` está restringido a
> `Cliente`/`Operador` (`@PreAuthorize("hasAnyRole('Cliente','Operador')")`). El rol
> `Admin` gestiona estados y catálogo pero **no crea pedidos**. Si se quiere lo
> contrario, basta agregar `'Admin'` a esa anotación en `PedidoController`.

La **diferencia Cliente vs Operador/Admin** se evidencia además en el filtro por actor:
el Cliente solo recibe sus propios pedidos a nivel de servicio (`PedidoService`, usando
`preferred_username`). Los rechazos devuelven **401** (sin token) o **403** (rol/scope insuficiente).

## 6. Reglas de negocio (pedidos)

- Estados: `CREADO → ACEPTADO → EN_PREPARACION → DESPACHADO → ENTREGADO`.
- Cancelación: `CREADO / ACEPTADO / EN_PREPARACION → CANCELADO`.
- No existe `CREADO → DESPACHADO`: un pedido **no puede despacharse sin haber sido aceptado**.
- Al **aceptar** un pedido, `pedidos-api` descuenta stock en `catalogo-api`; al **cancelar**
  en estados con stock descontado (`ACEPTADO`, `EN_PREPARACION`), se repone.
- Errores de dominio (stock insuficiente, transición inválida, producto inexistente) →
  `409 Conflict`/`404 Not Found` con mensaje legible.

## 7. Pruebas

```bash
cd catalogo-api && ./mvnw test
cd ../pedidos-api && ./mvnw test
cd ../cloud-native-angular && npm test       # Vitest
```

## 8. Despliegue (cloud)

- `infra/api-gateway-openapi.yaml`: plantilla para **API Gateway** (AWS) — sustituir
  los placeholders (`TU-API-ID`, `TU-EC2-DNS`, etc.) e importarla.
- Los microservicios se despliegan en **EC2** contra **RDS PostgreSQL** (env vars `DB_*`).
- El frontend se publica en **Vercel** apuntando al Gateway en `environment.prod.ts`.
