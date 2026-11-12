# Cliente real detras del proxy de Railway

Revision de la configuracion de proxy del backend y de lo que falta para verificarla en Railway.
Relacionado con `docs/operacion-seguridad.md` (seccion 3, rate limit) y con la seccion "Cliente real detras
del proxy" del `README.md`.

**Aviso principal:** el comportamiento documentado de Railway (IPs de su proxy, cabeceras que anade o
sobrescribe en el borde) **no se ha verificado desde este entorno**. No se han inventado rangos de IP de
Railway. Todo lo que dependa de Railway queda marcado como no verificado.

---

## 1. Configuracion revisada

Fichero: `src/main/resources/application.yaml` (solo lectura), lineas 36-44:

- `server.tomcat.remoteip.remote-ip-header: X-Real-IP`
- `server.tomcat.remoteip.internal-proxies`: expresion regular, por defecto la lista de rangos de confianza de
  abajo, sobreescribible con `APP_TRUSTED_PROXIES`.

Comentarios relevantes del propio fichero: X-Forwarded-For **no** se usa, y si la IP del proxy de Railway no
entra en el patron, X-Real-IP se ignora.

`RateLimitFilter` (`src/main/java/com/orbis/api/config/RateLimitFilter.java`) usa `request.getRemoteAddr()` y
no lee cabeceras. Lo que el filtro ve como IP del cliente es lo que Tomcat ha resuelto con `RemoteIpValve`.
Su comentario de clase lo describe igual.

---

## 2. Que hace RemoteIpValve con esta configuracion

Tomcat compara la direccion de la conexion directa (`remoteAddr`, la del que abre el socket) con
`internal-proxies`:

1. **Si la conexion directa coincide con el patron** (peer de confianza): toma el cliente de la cabecera
   `X-Real-IP` y la usa como `remoteAddr` de la peticion. Tambien toma el protocolo de la cabecera de protocolo
   (`protocol-header`), que no esta configurada en el yaml: se aplica el valor por defecto de Spring Boot
   (`X-Forwarded-Proto`, segun la documentacion de Spring Boot; no comprobado contra esta version).
2. **Si no coincide** (peer no confiable): ignora `X-Real-IP`. `remoteAddr` queda como la IP de la conexion
   directa, que detras de un proxy es la del proxy.

Detalles:

- Tomcat evalua el patron contra la direccion completa (coincidencia total, no parcial). Esto impide que una
  direccion como `100.64.1.2` entre por el prefijo `10`. **(Comportamiento de Tomcat segun su documentacion; no
  probado aqui.)**
- `X-Forwarded-For` no se configura como cabecera de IP, asi que el valve no la usa. Un cliente puede enviarla
  libremente sin efecto sobre `remoteAddr`.
- La cabecera `X-Real-IP` que envia un cliente **solo** tiene efecto si la conexion directa viene de un peer de
  confianza. Si el peer es confiable y el borde de Railway no la sobrescribe, un cliente podria fijarla.

---

## 3. Rangos de confianza por defecto

El patron por defecto de `application.yaml` confia en:

| Rango | Expresion en el patron | Notas |
|---|---|---|
| 10.0.0.0/8 | `10[.][0-9]+[.][0-9]+[.][0-9]+` | Red privada RFC 1918. Incluye la red privada de Railway si usa 10.x. |
| 172.16.0.0/12 | `172[.](1[6-9]|2[0-9]|3[01])[.]...` | RFC 1918. |
| 192.168.0.0/16 | `192[.]168[.]...` | RFC 1918. |
| 127.0.0.0/8 | `127[.]...` | Loopback. |
| ::1 y 0:0:0:0:0:0:0:1 | literales | Loopback IPv6. |
| fc00::/7 | `f[cd][0-9a-f][0-9a-f]:[0-9a-f:]*` | ULA IPv6. |

**No incluye:**

- **169.254.0.0/16** (link-local). El patron por defecto de Spring Boot si lo incluye; este proyecto lo sustituye
  por su propia expresion y no lo tiene. Solo importa si algun proxy usa esa red.
- **100.64.0.0/10** (espacio CGNAT, RFC 6598). Hay reportes de proxies en la nube que usan este rango. Si el
  proxy de Railway conecta desde ahi, X-Real-IP se ignora. Ver seccion 5.
- Cualquier IP publica.

---

## 4. Riesgos en las dos direcciones

1. **Peer no confiable (el valor por defecto no cubre al proxy):** X-Real-IP se ignora. Todas las peticiones
   llegan con la misma IP (la del proxy). El limite por IP de login (10 por minuto) se convierte en un limite
   **global**: diez intentos de cualquiera bloquean el login de todos. Es un fallo de disponibilidad, no de
   seguridad.
2. **Peer demasiado amplio (un servicio no previsto entra en el patron):** cualquier host dentro de la red
   confiable puede fijar X-Real-IP y saltarse el limite por IP cambiando la cabecera en cada peticion. Por
   ejemplo, otro servicio en la red privada de Railway (`*.railway.internal`), que no pasa por el borde, si su
   direccion cae en 10.x. Confiar en el patron por defecto solo es seguro si este backend es el unico servicio
   del entorno con acceso a esa red (el README lo indica igual).

---

## 5. Lo que falta para verificar detras de Railway

| Pregunta | Estado | Como responderla |
|---|---|---|
| Desde que IP conecta el proxy de Railway con el contenedor | **No verificado.** Railway no se ha consultado. No hay rangos documentados aqui. | Prueba controlada del paso 6.1 y, si hace falta, documentacion de Railway revisada por el propietario. |
| Si el borde de Railway anade o sobrescribe `X-Real-IP` | **No verificado.** No se debe asumir ni en un sentido ni en otro. | Prueba controlada del paso 6.1: la IP que aparece en el log muestra el comportamiento real. |
| Si el protocolo llega en `X-Forwarded-Proto` | **No verificado.** | Revisar una peticion desde logs o con una ruta de prueba. Afecta a la cookie `Secure` y al esquema generado, no al limite. |
| Si la peticion pasa antes por Vercel | **Pregunta abierta.** El backend se usa detras de un proxy Vercel-Railway (ver comentarios de `application.yaml` sobre SameSite). Si el navegador llega a Railway a traves de Vercel, el peer que ve Railway es la infraestructura de Vercel, no el usuario. | Confirmar la configuracion de rewrites de Vercel y si reenvia `X-Real-IP`. Sin eso, el limite por IP puede ser global. |
| Rango CGNAT 100.64.0.0/10 | **Caveat abierto.** No esta en el patron. | Solo importa si el paso 6.1 muestra la IP del proxy dentro de 100.64/10. Entonces, anadirlo con `APP_TRUSTED_PROXIES` tras confirmarlo. |

---

## 6. Verificacion segura tras desplegar

Ninguno de estos pasos requiere cambiar codigo. Todos se hacen contra produccion con una cuenta inventada.
Son pruebas de bajo riesgo, pero generan trafico real y logs con IPs de clientes.

### 6.1. Prueba controlada con log (recomendada)

Objetivo: ver que IP registra el backend en el aviso "Limite de peticiones alcanzado".

1. Desde tu red (IP publica conocida), enviar 11 `POST` a `/auth/login` en menos de 60 s, con correo inventado
   (por ejemplo `prueba-ip@example.invalid`), contrasena incorrecta y la cabecera
   `X-Real-IP: 203.0.113.99`. Ese rango (TEST-NET-3) es solo de documentacion y no es una IP real de nadie.
2. Las peticiones 1 a 10 devuelven 401 o 429 de cuenta (el limite por cuenta corta antes). La 11 devuelve 429 del
   limite por IP.
3. Revisar en los logs de Railway la linea `Límite de peticiones alcanzado (login) desde IP: ...`.

Interpretacion de la IP que aparece:

| IP en el log | Significado |
|---|---|
| `203.0.113.99` | El peer es de confianza y el borde **no** sobrescribe X-Real-IP: un cliente puede fijar la IP. Hay que cerrar esto (revisar el borde y el patron) antes de dar el limite por IP por valido. |
| Tu IP publica | El peer es de confianza y el borde **si** pone la IP real del cliente (sobrescribiendo la cabecera enviada). Es el caso deseado. |
| La IP del proxy de Railway | El peer no es de confianza, o el borde no pone X-Real-IP. El limite es global. Si la IP cae en 100.64/10, anadirla con `APP_TRUSTED_PROXIES` tras confirmarlo. |

Anotar cuenta de peticiones, fecha, IP observada y resultado. No pegar en issues publicos los logs con IPs.

### 6.2. Prueba desde otra red

Repetir 6.1 desde otra red (por ejemplo, datos moviles). Si la IP del log cambia y el contador es independiente,
el limite por IP distingue clientes. Si ambas peticiones acaban en el mismo contador, la IP vista por el backend
es la del proxy.

### 6.3. Log de depuracion temporal

Un log que escriba `request.getRemoteAddr()` y el valor de `X-Real-IP` en cada peticion daria la informacion
directamente. **Requiere cambio de codigo y decision del propietario.** Si se hace, debe quitarse despues y no
debe registrar ningun valor de cabeceras de autenticacion.

### 6.4. Endpoint de depuracion temporal

Un endpoint que devuelva la IP resuelta. **Requiere decision del propietario.** No recomendado: expone
informacion de red. Si se usa, debe ir detras de un perfil que no exista en produccion y eliminarse el mismo
dia.

---

## 7. Resumen

- El backend confia en X-Real-IP solo si el peer esta en el patron de confianza. El patron por defecto cubre
  loopback, RFC 1918 y ULA, pero no 100.64/10.
- **No se ha verificado** que Railway conecte desde un rango cubierto, ni que su borde establezca X-Real-IP.
- Antes de dar por bueno el limite por IP en produccion, hacer la prueba 6.1 y leer la IP del log.
- Pregunta abierta: si el trafico pasa por Vercel antes de Railway, el peer es de Vercel y la IP del cliente
  depende de lo que reenvie Vercel.
