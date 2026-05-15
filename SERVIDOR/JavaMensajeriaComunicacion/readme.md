# 📡 Manual del Protocolo de Comunicación (Contrato Cliente-Servidor)

## 🧠 1. Propósito del módulo `mensajeria`

Este módulo define el **contrato de comunicación** entre cliente y servidor.

### ✔ Responsabilidad

* Definir la **estructura de los mensajes**
* Definir las **acciones disponibles**
* Definir los **datos intercambiados (payloads)**

### ❌ No incluye

* Lógica de negocio
* Manejo de sockets (TCP/UDP)
* Serialización (JSON, etc.)

👉 Este módulo debe ser **compartido e importado** tanto por cliente como por servidor.

---

## 🧩 2. Estructura general del mensaje

Clase principal: `Mensaje<T>`

Representa cualquier comunicación entre cliente y servidor.

```java
Mensaje<T>
```

### Componentes:

| Campo    | Descripción                       |
|----------| --------------------------------- |
| tipo     | Indica si es REQUEST o RESPONSE   |
| accion   | Qué operación se desea ejecutar   |
| metadata | Información adicional del mensaje |
| payload  | Datos específicos de la acción    |

---

## 🔹 2.1 TipoMensaje

```java
REQUEST / RESPONSE
```

### Uso:

* `REQUEST`: enviado por el cliente
* `RESPONSE`: enviado por el servidor

---

## 🔹 2.2 Accion

Enum que define todas las operaciones del sistema:

```java
CONECTAR
LISTAR_CLIENTES
LISTAR_DOCUMENTOS
LISTAR_LOGS
ENVIAR_DOCUMENTO
OBTENER_DOCUMENTO
```

### Uso:

Permite al servidor saber **qué lógica ejecutar**.

---

## 🔹 2.3 Metadata

Contiene información contextual del mensaje:

```java
Metadata
```

### Campos:

| Campo     | Descripción               |
| --------- | ------------------------- |
| idMensaje | Identificador único       |
| timestamp | Fecha/hora del envío      |
| clientId  | Identificador del cliente |
| protocolo | TCP o UDP                 |

### Uso:

* Logging
* Trazabilidad
* Debugging

---

## 🔹 2.4 Protocolo

```java
TCP / UDP
```

### Uso:

Indica el protocolo de transporte usado.

---

## 📦 3. Payload (datos del mensaje)

El `payload` contiene los datos específicos de cada acción.

👉 Cada acción tiene su propio tipo de payload.

---

## 🔹 3.1 PayloadConectar

```java
username
```

### Uso:

Cliente solicita conexión al servidor.

---

## 🔹 3.2 PayloadEnviarDocumento

```java
nombre
contenido (base64)
extension
tamano
clientIdDestino (opcional)
```

### Uso:

Enviar mensajes o archivos al servidor.

---

## 🔹 3.3 PayloadObtenerDocumento

```java
documentId
opciones
```

---

## 🔹 3.4 OpcionesDocumento

```java
incluirHash
encriptado
```

### Uso:

Define cómo quiere el cliente recibir el documento:

| Opción      | Descripción                   |
| ----------- | ----------------------------- |
| incluirHash | Retornar hash del documento   |
| encriptado  | Retornar documento encriptado |

---

## 📦 4. Respuestas del servidor

Clase: `Respuesta<T>`

Encapsula la respuesta del servidor.

---

## 🔹 4.1 Estado

```java
EXITO / ERROR
```

---

## 🔹 4.2 ErrorDetalle

```java
codigo
mensaje
```

### Uso:

Describe errores ocurridos en la operación.

---

## 🧠 5. Flujo completo de comunicación

### 📤 Cliente envía:

```text
Mensaje<T> → JSON → Socket
```

---

### 📥 Servidor recibe:

```text
JSON → Mensaje<?> → determinar acción → convertir payload → procesar
```

---

### 📤 Servidor responde:

```text
Respuesta<T> → JSON → Socket
```

---

### 📥 Cliente recibe:

```text
JSON → Respuesta<?> → procesar resultado
```

---

## 🔄 6. Manejo del payload (punto crítico)

Debido a limitaciones de Java con genéricos:

```java
Mensaje<?> mensaje = ...
```

El `payload` se recibe como `Map`.

### Solución:

Convertir según la acción:

```java
objectMapper.convertValue(...)
```

---
