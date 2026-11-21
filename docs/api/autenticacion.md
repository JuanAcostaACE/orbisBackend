# Autenticación

El backend acepta dos mecanismos:

1. **Usuarios web**: sesión de servidor. Tras `POST /auth/login` el servidor envía la cookie
   `ORBIS_SID` (HttpOnly, SameSite=Strict, Secure en producción, 8 h).
   El navegador debe enviar las peticiones con `credentials: 'include'`.
2. **Dispositivo ESP32**: cabecera `X-Device-Token` con el valor de `APP_DEVICE_TOKEN`.
   Obtiene `ROLE_DEVICE` y solo puede usar `POST /api/v1/events`. Los eventos quedan a
   nombre de `APP_DEVICE_OWNER_EMAIL`.

## CSRF

Las mutaciones con sesión requieren la cabecera `X-XSRF-TOKEN` con el valor de la cookie
`XSRF-TOKEN`. Están excluidas `/auth/**`, `POST /api/v1/events` y `/health`.

## CORS

Orígenes permitidos: `localhost:3000`, `localhost:8080`, `localhost:5173` y los definidos en
`APP_CORS_ORIGIN` / `APP_CORS_ORIGIN2`. En producción el frontend usa el proxy de Vercel, por lo
que el navegador ve un solo origen.
