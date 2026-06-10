# Documentación FRONTEND - Sistema de Mensajería P2P

## Descripción General

El FRONTEND es una aplicación Angular 17 que actúa como panel de monitoreo y gestión para un sistema de mensajería P2P. Permite visualizar y administrar:

- Clientes conectados a los servidores
- Mensajes intercambiados en la red
- Archivos almacenados
- Logs de actividad del servidor
- Información de servidores disponibles
- Clasificación de géneros musicales (integración con modelo ML)

**Stack tecnológico:**

- Angular 17 (con componentes standalone)
- Angular Material Design
- SCSS para estilos
- RxJS para reactividad
- TypeScript 5.4

---

## Estructura de Carpetas

```
FRONTEND/
├── angular.json              # Configuración del proyecto Angular
├── package.json              # Dependencias npm
├── tsconfig.json            # Configuración de TypeScript
├── tsconfig.app.json        # Configuración específica para la app
├── nginx.conf               # Configuración de servidor Nginx (para producción)
├── Dockerfile               # Imagen Docker multi-stage
├── src/
│   ├── index.html           # HTML principal
│   ├── main.ts              # Punto de entrada de la aplicación
│   ├── styles.scss          # Estilos globales y tema Material
│   ├── environments/        # Configuraciones por entorno
│   └── app/                 # Código de la aplicación
│       ├── app.component.ts         # Componente raíz
│       ├── app.config.ts            # Configuración global (providers)
│       ├── app.routes.ts            # Rutas principales
│       ├── core/                    # Lógica compartida
│       │   ├── interceptors/        # Interceptores HTTP
│       │   ├── models/              # Interfaces de tipos
│       │   └── services/            # Servicios inyectables
│       ├── features/                # Módulos funcionales
│       │   ├── panel/
│       │   ├── clientes/
│       │   ├── mensajes/
│       │   ├── archivos/
│       │   ├── logs/
│       │   ├── servidores/
│       │   └── ml/
│       ├── layout/                  # Componentes de layout
│       │   ├── shell/
│       │   └── sidebar/
│       └── shared/                  # Componentes reutilizables
│           ├── status-dot/
│           ├── page-header/
│           └── empty-state/
```

---

## Archivos de Configuración

### `angular.json`

- Define la estructura del proyecto Angular
- Configura los comandos de build, serve y watch
- Establece presupuestos de tamaño de bundle (500KB warning, 1MB error)
- Configura outputs, assets y estilos globales
- Define configuraciones de desarrollo y producción

### `tsconfig.json`

- TypeScript versión ES2022
- Strict mode habilitado (verifica tipos rigurosamente)
- Decoradores experimentales activados (necesario para Angular)
- Module resolution: bundler

### `nginx.conf`

- Servidor web para servir la aplicación en producción
- Configuración de routing del cliente (SPA)
- Compresión y cachés

### `Dockerfile`

- Build multi-stage: primero compila con Node, luego sirve con Nginx
- Genera archivos optimizados en `dist/frontend/browser`

---

## Configuración de Entornos

### `src/environments/environment.ts` (Desarrollo)

```typescript
export const environment = {
  production: false,
  gatewayUrl: "", // URL del Gateway (se define al desplegar)
};
```

### `src/environments/environment.prod.ts` (Producción)

```typescript
export const environment = {
  production: true,
  gatewayUrl: "", // URL del Gateway en producción
};
```

Uso: El código se reemplaza automáticamente durante la compilación.

---

## Punto de Entrada

### `src/main.ts`

- Arranca la aplicación usando `bootstrapApplication()`
- Inicializa el componente raíz `AppComponent`
- Aplica la configuración global desde `app.config.ts`

### `src/index.html`

- HTML principal que serve la app
- Importa fuentes de Google (Roboto, Material Icons)
- Define meta tags para responsive y viewport

### `src/styles.scss`

- Tema oscuro basado en Material Design
- Define variables CSS personalizadas:
  - Colores de fondo (`--bg-primary`, `--bg-surface`)
  - Colores de estado (`--status-on`, `--status-off`)
  - Variables de espaciado y bordes
- Estilos globales reset (margin/padding 0)

---

## Configuración Global de la Aplicación

### `app/app.config.ts`

Proveedores globales inyectados en todos los componentes:

| Proveedor                                                 | Propósito                          |
| --------------------------------------------------------- | ---------------------------------- |
| `provideRouter(routes)`                                   | Sistema de enrutamiento            |
| `provideHttpClient(withInterceptors([errorInterceptor]))` | Cliente HTTP con interceptores     |
| `provideAnimations()`                                     | Sistema de animaciones de Material |

### `app/app.routes.ts`

Define la estructura de navegación:

- Ruta raíz (""): renderiza `ShellComponent` con subrutas
- Subrutas: panel, clientes, mensajes, archivos, logs, servidores, ml
- Lazy loading: cada ruta carga su componente solo cuando se accede
- Ruta por defecto: redirige a "panel"
- Catch-all (\*\*): redirige a inicio si ruta no existe

### `app/app.component.ts`

- Componente raíz minimal
- Solo renderiza `<router-outlet />` (donde aparecen las subrutas)

---

## Core - Servicios e Interceptores

### `core/interceptors/error.interceptor.ts`

Intercepta todas las peticiones HTTP:

- Captura errores de red/HTTP
- Extrae mensaje de error del response o del error
- Propaga el error de forma legible

### `core/services/api.service.ts`

Servicio central para peticiones HTTP:

```typescript
get<T>(servidorId: string, path: string, params?: Record<string, string>): Observable<T>
```

- Construye URLs: `${gatewayUrl}/gateway/${servidorId}/api/${path}`
- Acepta parámetros query
- Retorna observables RxJS

**Uso:** Todos los componentes features lo inyectan para consultar datos.

### `core/services/server.service.ts`

Gestiona el servidor seleccionado y lista de servidores:

```typescript
readonly servidores$: Signal<Servidor[]>
readonly seleccionado$: Signal<Servidor | null>

cargarServidores(): Observable<Servidor[]>
seleccionar(servidor: Servidor): void
```

- Usa Angular Signals para reactividad
- Carga la lista de servidores desde `GET /gateway/servidores`
- Permite cambiar el servidor activo
- Todos los features observan `seleccionado$()` para filtrar datos

---

## Models - Interfaces de Tipos

### `core/models/index.ts`

Exporta todas las interfaces de datos.

### `core/models/servidor.model.ts`

```typescript
interface Servidor {
  servidorId: string; // ID único
  host: string; // Dirección IP/hostname
  puerto: number; // Puerto REST
  estado: string; // "CONECTADO" o "DESCONECTADO"
  intentosReconexion: number;
  ultimaConexion: string | null;
}
```

### `core/models/cliente.model.ts`

```typescript
interface Cliente {
  username: string;
  ip: string;
  puerto: number;
  protocolo: string;
  creadoEn: string; // ISO date
  ultimoAcceso: string; // ISO date
}
```

### `core/models/mensaje.model.ts`

```typescript
interface Mensaje {
  id: string;
  autor: string; // Usuario que envió
  ipRemitente: string;
  contenido: string; // Texto del mensaje
  hashSha256: string; // Hash del contenido
  fechaEnvio: string; // ISO date
  servidorOrigen: string;
  destinatario: string;
}
```

### `core/models/archivo.model.ts`

```typescript
interface Archivo {
  id: string;
  remitente: string;
  ipRemitente: string;
  nombreArchivo: string;
  extension: string;
  rutaArchivo: string;
  hashSha256: string;
  tamano: number; // Bytes
  fechaRecepcion: string; // ISO date
  servidorOrigen: string;
  destinatario: string;
}
```

### `core/models/log-servidor.model.ts`

```typescript
interface LogServidor {
  id: number;
  nivel: string; // INFO, WARN, ERROR, DEBUG
  mensaje: string;
  origen: string; // Componente que generó el log
  ipRemitente: string;
  fechaEvento: string; // ISO date
}
```

### `core/models/pagina.model.ts`

Envoltorio para respuestas paginadas:

```typescript
interface Pagina<T> {
  datos: T[];
  total: number; // Total de registros
  pagina: number; // Índice de página (0-based)
  tamanoPagina: number; // Elementos por página
}
```

### `core/models/ml.model.ts`

```typescript
interface MlHealth {
  status: string;
  modelLoaded: boolean;
}

interface GenerosMl {
  genres: string[];
}
```

---

## Layout - Estructura Visual

### `layout/shell/shell.component.ts`

Contenedor principal que divide la pantalla:

```
┌─────────────────┬──────────────────┐
│                 │                  │
│   SIDEBAR       │   MAIN CONTENT   │
│                 │                  │
└─────────────────┴──────────────────┘
```

- Flex layout: 100vh de alto
- Sidebar a la izquierda (ancho variable)
- Main content ocupa el resto y scrollea

### `layout/sidebar/sidebar.component.ts`

Panel de navegación lateral:

**Funcionalidades:**

- Dropdown selector de servidores
- Menú de navegación con 7 opciones (panel, clientes, mensajes, etc.)
- Cada opción es un RouterLink que activa la ruta
- Ícono y etiqueta por opción
- Carga la lista de servidores al inicializar

**Estructura:**

```html
<mat-select
  [value]="serverService.seleccionado$()"
  (selectionChange)="seleccionar($event)"
>
  Dropdown de servidores
</mat-select>

<mat-nav-list>
  Panel -> /panel Clientes -> /clientes Mensajes -> /mensajes ...
</mat-nav-list>
```

---

## Componentes Compartidos

### `shared/status-dot/status-dot.component.ts`

Indicador visual de estado:

- Input: `@Input() estado: string`
- Verde brillante si estado == "CONECTADO"
- Rojo si estado != "CONECTADO"
- Dibuja un círculo con glow CSS

### `shared/page-header/page-header.component.ts`

Encabezado reutilizable para cada página:

- `@Input() titulo: string` (requerido)
- `@Input() subtitulo: string` (opcional)
- Proyecta contenido en `<ng-content />` para acciones adicionales

**Uso:**

```html
<app-page-header titulo="Clientes" subtitulo="Conectados ahora">
  <button mat-icon-button>...</button>
</app-page-header>
```

### `shared/empty-state/empty-state.component.ts`

Mensaje cuando no hay datos:

- `@Input() mensaje: string` (default: "Sin datos")
- `@Input() icono: string` (default: "inbox")
- Muestra ícono de Material + mensaje centrado

---

## Features - Módulos Funcionales

Cada feature es un componente standalone que:

1. Se carga lazy (solo cuando se accede a su ruta)
2. Inyecta `ApiService` para obtener datos
3. Inyecta `ServerService` para saber cuál servidor está activo
4. Usa Angular Signals (`signal()`, `.set()`, `()`) para estado local
5. Observa cambios de servidor con `serverService.seleccionado$()`

### `features/panel/panel.component.ts`

**Propósito:** Dashboard con estado general y accesos rápidos

**Estado:**

- Observable: `serverService.seleccionado$()` (servidor actual)

**Contenido:**

- Tarjeta con info del servidor (ID, host, puerto, última conexión)
- Indicador de estado (verde/rojo) usando `StatusDotComponent`
- 3 tarjetas de acceso rápido a: Clientes, Archivos, Logs

**Plantilla:**

```html
<app-page-header titulo="Panel" subtitulo="...">
  <div class="info-card">Estado del servidor seleccionado</div>
  <div class="quick-cards">3 tarjetas que son RouterLinks</div></app-page-header
>
```

---

### `features/clientes/clientes.component.ts`

**Propósito:** Tabla de clientes conectados

**Signals:**

- `loading`: muestra barra de progreso mientras carga
- `error`: muestra banner rojo si hay error
- `datos`: array de `Cliente[]` para la tabla

**Columnas:**

- username, ip, puerto, protocolo, creadoEn, ultimoAcceso

**Funcionalidad:**

- Botón "Actualizar" para recargar datos
- Habilitado solo si hay servidor seleccionado
- Llamada: `GET /gateway/{servidorId}/api/clientes`
- Si sin datos: muestra `<app-empty-state />`

---

### `features/mensajes/mensajes.component.ts`

**Propósito:** Tabla de mensajes intercambiados

**Signals:**

- `loading`, `error`, `datos: Mensaje[]`

**Columnas:**

- autor, destinatario, contenido, servidorOrigen, fechaEnvio, hashSha256

**Features:**

- Input de búsqueda: filtra por username (autor)
- Llamada: `GET /gateway/{servidorId}/api/mensajes?username=xxx` (si hay filtro)
- Botón refrescar para recargar

---

### `features/archivos/archivos.component.ts`

**Propósito:** Tabla de archivos almacenados

**Signals:**

- `loading`, `error`, `datos: Archivo[]`

**Columnas:**

- nombreArchivo, extension, remitente, destinatario, tamano, fechaRecepcion, servidorOrigen, hashSha256

**Método helper:**

```typescript
formatBytes(bytes: number): string
// 1024 B -> "1.0 KB"
// 1048576 B -> "1.0 MB"
```

**Features:**

- Filtro por username (remitente)
- Tamaño legible en KB/MB

---

### `features/logs/logs.component.ts`

**Propósito:** Tabla paginada de eventos de log del servidor

**Signals:**

- `loading`, `error`, `datos: LogServidor[]`, `total: number`

**Propiedades:**

- `pagina = 0`, `tamanoPagina = 50`

**Columnas:**

- nivel, mensaje, origen, ipRemitente, fechaEvento

**Features:**

- Paginación con `MatPaginatorModule`
- Evento `onPage()`: recarga datos cuando cambia la página
- Llamada: `GET /gateway/{servidorId}/api/logs?pagina=0&tamanoPagina=50`
- Método `nivelClass()` retorna clase CSS para colorear por nivel

---

### `features/servidores/servidores.component.ts`

**Propósito:** Tabla de todos los servidores registrados

**Signals:**

- `loading`, `error`, `datos: Servidor[]`

**Columnas:**

- estado (con StatusDot verde/rojo), servidorId, host, puerto, intentosReconexion, ultimaConexion

**Features:**

- Muestra estado visual con `StatusDotComponent`
- Botón refrescar
- Llamada: `GET /gateway/servidores` (sin parámetro servidorId)

---

### `features/ml/ml.component.ts`

**Propósito:** Integración con servicio de clasificación de géneros musicales

**Signals:**

- `loading`, `error`
- `health: MlHealth | null` (si modelo está cargado)
- `generos: string[]` (lista de géneros disponibles)

**Features:**

- 2 llamadas paralelas al inicio:
  - `GET /gateway/{servidorId}/api/ml/health` -> status del modelo
  - `GET /gateway/{servidorId}/api/ml/generos` -> géneros disponibles
- Si health == true, muestra list de géneros
- Interfaz para subir audio y predecir (en componente HTML)

---

## Estilos y Temas

### Material Design Dark Theme

- Colores primarios: azul (600)
- Acento: azul claro (A200)
- Tipografía: Roboto

### Colores Personalizados (CSS Variables)

```scss
--bg-primary: #0f0f1a // Fondo principal
  --bg-surface: #1a1a2e // Tarjetas
  --bg-card: #16213e // Cartas secundarias
  --accent-blue: #4a9eff // Azul destacado
  --text-primary: #e8eaf6 // Texto principal
  --text-muted: #90a4ae // Texto secundario
  --status-on: #4caf50 // Verde conectado
  --status-off: #f44336 // Rojo desconectado
  --border-color: #2a2a4a; // Bordes
```

### Layout Base

```scss
html,
body {
  height: 100%; // Full viewport
}

* {
  box-sizing: border-box; // Incluye padding en width
  margin: 0;
  padding: 0;
}
```

---

## Flujo de Datos

```
Usuario cambia servidor en Sidebar
  ↓
ServerService.seleccionar(servidor)
  ↓
Todos los features observan serverService.seleccionado$()
  ↓
Componente detecta cambio, llama cargar()
  ↓
ApiService.get<T>(servidorId, path, params)
  ↓
HTTP GET /gateway/{servidorId}/api/{path}
  ↓
ErrorInterceptor captura respuesta/error
  ↓
Actualiza signals: loading=false, datos=[], error=null/mensaje
  ↓
Template detecta cambio en signal y re-renderiza
```

---

## Patrones Clave

### Signal-based Reactivity

```typescript
readonly datos = signal<Cliente[]>([]);

// Actualizar
this.datos.set(newArray);

// Leer en template
@if (datos().length > 0) { ... }
```

### Lazy Loading de Rutas

```typescript
{
  path: 'clientes',
  loadComponent: () => import('./clientes.component')
    .then(m => m.ClientesComponent)
}
```

Solo importa y carga el JS cuando accedes a `/clientes`.

### Inyección de Dependencias

```typescript
export class MiComponente {
  private readonly api = inject(ApiService);
  readonly serverService = inject(ServerService);
}
```

### Subscribe a Observables HTTP (next/error)

Patrón para manejar respuestas HTTP con Signals:

```typescript
// Ejemplo: Cargar archivos del servidor
this.api.get<Archivo[]>(srv.servidorId, "archivos", params).subscribe({
  next: (data) => {
    this.datos.set(data); // Guardar respuesta en Signal
    this.loading.set(false); // Ocultar barra de carga
  },
  error: (err) => {
    this.error.set(err.message); // Guardar mensaje de error
    this.loading.set(false); // Ocultar barra de carga
  },
});
```

**Desglose:**

| Parte                         | Qué hace                                               |
| ----------------------------- | ------------------------------------------------------ |
| `this.api.get<T>(...)`        | Realiza HTTP GET a `/gateway/{servidorId}/api/{path}`  |
| `.subscribe({...})`           | Se suscribe al Observable (empieza la petición)        |
| `next: data => {...}`         | Se ejecuta cuando la respuesta es exitosa (status 200) |
| `this.datos.set(data)`        | Actualiza el Signal con los datos recibidos            |
| `this.loading.set(false)`     | Oculta indicador de carga (barra progress)             |
| `error: err => {...}`         | Se ejecuta si hay error (timeout, error HTTP, etc)     |
| `this.error.set(err.message)` | Guarda el mensaje de error para mostrar en template    |

**Flujo visual:**

```
Usuario hace clic en botón "Actualizar"
  ↓
this.loading.set(true)  [muestra barra de progreso]
  ↓
HTTP GET → /gateway/{servidorId}/api/archivos?params
  ↓ espera respuesta 1-3 segundos
  ↓
  ├─ ✅ Respuesta OK (200)
  │   └─ next: { this.datos.set(respuesta), this.loading.set(false) }
  │       [tabla muestra archivos, barra desaparece]
  │
  └─ ❌ Error (timeout, 500, etc)
      └─ error: { this.error.set(mensaje), this.loading.set(false) }
          [banner rojo con error, barra desaparece]
```

**En el template HTML:**

```html
@if (loading()) { <mat-progress-bar mode="indeterminate" />
<!-- Mostrar mientras carga -->
} @if (error()) {
<div class="error-banner">{{ error() }}</div>
<!-- Mostrar si hay error -->
} @if (datos().length > 0) {
<table>
  ...
</table>
<!-- Mostrar tabla si hay datos -->
} @else {
<app-empty-state />
<!-- Mostrar "sin datos" si vacío -->
}
```

**Casos de uso en el proyecto:**

- `ClientesComponent`: GET `/api/clientes`
- `MensajesComponent`: GET `/api/mensajes?username=xxx`
- `ArchivosComponent`: GET `/api/archivos?username=xxx`
- `LogsComponent`: GET `/api/logs?pagina=0&tamanoPagina=50`
- `MlComponent`: GET `/api/ml/health` y `/api/ml/generos`

### Template Control Flow

```html
@if (loading()) { <mat-progress-bar /> } @if (error()) {
<div>{{ error() }}</div>
} @if (datos().length > 0) {
<table>
  ...
</table>
} @else {
<app-empty-state />
} @for (item of datos(); track item.id) {
<tr>
  {{ item.campo }}
</tr>
}
```

---

## Build y Deployment

### Desarrollo

```bash
npm install
npm start   # ng serve en http://localhost:4200
```

### Producción

```bash
npm run build  # Genera dist/frontend/browser optimizado
```

### Docker

```dockerfile
# Stage 1: Build
FROM node:20-alpine
COPY . .
RUN npm run build

# Stage 2: Serve
FROM nginx:alpine
COPY dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

---

## Resumen Visual

```
┌─── FRONTEND ────────────────────────────────┐
│                                             │
│ app.component → app.routes                  │
│    ↓                                        │
│ ShellComponent (layout)                     │
│    ├─ Sidebar (navegación + selector)       │
│    └─ RouterOutlet (contenido dinámico)     │
│         ├─ PanelComponent                   │
│         ├─ ClientesComponent                │
│         ├─ MensajesComponent                │
│         ├─ ArchivosComponent                │
│         ├─ LogsComponent                    │
│         ├─ ServidoresComponent              │
│         └─ MlComponent                      │
│                                             │
│ Services (inyectables):                     │
│    ├─ ApiService (HTTP calls)               │
│    └─ ServerService (estado global)         │
│                                             │
│ Shared Components:                          │
│    ├─ StatusDot (indicador verde/rojo)      │
│    ├─ PageHeader (encabezado)               │
│    └─ EmptyState (sin datos)                │
│                                             │
└─────────────────────────────────────────────┘
```
