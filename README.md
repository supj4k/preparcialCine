# Parcial 2 (práctica v3): Cine Icesi sobre TCP y UDP

**Computación en Internet I**

| | |
|---|---|
| **Duración** | **1 hora 15 minutos** |
| **Modalidad** | Individual |
| **Puertos** | TCP **12345** (clientes) · UDP **5000** (kiosco) |

---

## 1. Contexto

Un cine tiene una sala de **5 filas × 6 columnas**. Las filas van de 0 a 4 y las columnas de 0 a 5.

- Los **clientes** se comunican por **TCP** con mensajes **JSON** para ver la sala, reservar sillas y cancelar sus reservas. Funciona igual que el Buscaminas del laboratorio: **una petición por conexión**. El cliente se conecta, envía una línea, lee una línea y el servidor cierra.
- Un **kiosco** en la entrada consulta por **UDP** cuántas sillas quedan libres y el estado de una silla en particular.

Un solo servidor atiende los dos canales a la vez, sobre **la misma sala**.

---

## 2. Estructura del proyecto

```text
├── server/        Servidor (ENTREGADO: incompleto y con defectos)
│   └── src/main/java/co/icesi/cine/
│       ├── Main.java
│       ├── controllers/   TCPController, UDPController
│       │   └── dtos/      Request, Response
│       ├── model/         Seat
│       └── services/      ServicesImpl, CinemaException
├── kiosco/        Cliente UDP (VACÍO: lo construye usted)
├── cliente/       Cliente TCP (VACÍO: lo construye usted)
└── verificador/   Pruebas automáticas (NO modificar)
```

---

## 3. Protocolo UDP (kiosco → servidor, puerto 5000)

Los mensajes son texto con campos separados por `;`, y los comandos van en mayúsculas exactas. Por cada datagrama, el servidor responde con un datagrama al emisor.

| Petición | Respuesta |
|---|---|
| `PING` | `PONG` |
| `FREE` | `FREE;<sillasLibres>` |
| `SEAT;<fila>;<col>` | `SEAT;<fila>;<col>;FREE` o `SEAT;<fila>;<col>;TAKEN` |
| `SEAT` con una silla fuera de la sala | `ERROR;INVALID_SEAT` |
| Cualquier otra cosa (comando desconocido, vacío, campos de más o de menos, fila/columna no numérica) | `ERROR;INVALID_FORMAT` |

---

## 4. Protocolo TCP (cliente → servidor, puerto 12345)

Cada petición es una línea JSON terminada en `\n`. El servidor responde con **una** línea JSON y **cierra la conexión**.

**Petición:** `{"action":"RESERVE","data":{"row":"1","col":"3","user":"ana"}}` (todos los valores de `data` son strings)

**Respuesta OK:** `{"status":"OK","data":{"seats":[[...]], "free":29}}`. La sala completa viene como matriz 5×6 de sillas `{"reserved":true,"owner":"ana"}`; si `owner` es null, no aparece en el JSON.

**Respuesta con error:** `{"status":"ERROR","data":{"message":"<CODIGO>"}}`

| `action` | `data` | Códigos de error |
|---|---|---|
| `GET_SEATS` | — | — |
| `RESERVE` | `row`, `col`, `user` | `INVALID_DATA` (falta un campo, no es número o el user está vacío) · `INVALID_SEAT` (fuera de la sala) · `SEAT_TAKEN` |
| `CANCEL` | `row`, `col`, `user` | `INVALID_DATA` · `INVALID_SEAT` · `NOT_YOURS` (la silla está libre o es de otra persona) |
| otra acción | | `UNKNOWN_ACTION` |
| línea que no es JSON | | `INVALID_JSON` |

---

## 5. Requisitos del servidor

1. `Main` deja funcionando **los dos** servidores al mismo tiempo, sobre la misma instancia de `ServicesImpl`.
2. Los dos servidores aceptan peticiones en **todas las interfaces** de la máquina.
3. **Toda** petición TCP recibe una respuesta, incluso cuando los datos vienen mal, y la conexión **siempre** se cierra después.
4. Aunque varios clientes reserven al mismo tiempo, **una silla nunca puede quedar reservada por dos personas**.

---

## 6. Tareas

### Parte A: Servidor (≈ 30 min)
El código de `server/` compila, pero **está incompleto y tiene defectos**. Encuéntrelos y corríjalos hasta que cumpla las secciones 3, 4 y 5. No cambie los nombres de clases, paquetes ni las firmas públicas existentes.

### Parte B: Cliente kiosco, UDP (≈ 10 min)
En el módulo `kiosco/`, paquete `kiosco`:
- `KioskClient(String host, int port, int timeoutMs)`.
- `String query(String message) throws IOException`: envía el datagrama y devuelve la respuesta. Si no llega en `timeoutMs`, lanza `SocketTimeoutException`.
- `KioskMain`: menú con sillas libres, estado de una silla, PING y salir. Timeout de 2000 ms, con un aviso claro si se agota.

### Parte C: Cliente TCP (≈ 20 min)
En el módulo `cliente/`, paquete `cine`:
- `Request` y `Response`, con la misma estructura de los DTO del servidor (campos públicos y constructor vacío). También una clase `Seat` para leer la sala.
- `CinemaClient(String host, int port)` con `Response sendRequest(Request r) throws IOException`, que abre **una conexión por petición**.
- `ClientMain`: pide el nombre del usuario y muestra el menú:
  1. Ver sala: dibuja la matriz con `[ ]` libre, `[X]` ocupada por otro y `[*]` tuya, más el total de libres.
  2. Reservar.
  3. Cancelar.
  4. Salir.

  Muestra los códigos de error y no se cae si el servidor está apagado.

### Parte D: Preguntas (≈ 5 min)
1. El cliente envía `user` en cada `RESERVE`/`CANCEL`, en vez de hacer un LOGIN al inicio. ¿Por qué, con este diseño de conexión, no tendría sentido un LOGIN?
2. Describa paso a paso cómo, sin sincronización, dos clientes podrían quedar los dos con la misma silla. ¿Qué métodos deben protegerse y por qué?

---

## 7. Cómo ejecutar y verificar

```bash
./gradlew :verificador:run --console=plain   # pruebas automáticas (apague antes su servidor)
./gradlew :server:run --console=plain        # terminal 1
./gradlew :cliente:run --console=plain       # terminales 2 y 3
./gradlew :kiosco:run --console=plain        # terminal 4
```
En Windows use `gradlew.bat`. Sin Gradle: `javac -cp gson.jar -d out $(find . -name "*.java")` y luego `java -cp out:gson.jar verificador.Verificador` (en Windows, `;` en lugar de `:`).

El verificador trae **52 pruebas**. Mientras haya partes sin hacer, algunas secciones se detienen antes y el total que muestra es menor.

---

## 8. Rúbrica (0.0 a 5.0)

| Componente | Peso | Evidencia |
|---|---|---|
| Lógica y servidor UDP | 1.0 | Secciones 1 y 3 |
| Servicios y concurrencia | 0.6 | Sección 2 |
| Servidor TCP | 1.0 | Sección 4 |
| Arranque conjunto | 0.4 | Sección 5 |
| Cliente kiosco | 0.6 | Sección 6 + demo |
| Cliente TCP | 1.0 | Sección 7 + demo con dos clientes |
| Preguntas | 0.4 | Parte D |
| **Total** | **5.0** | |
