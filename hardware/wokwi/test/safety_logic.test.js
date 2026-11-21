// Pruebas SIMULADAS de la logica de seguridad de hardware/wokwi/sketch.ino.
// NO son pruebas fisicas: no hay HC-SR04 ni motor vibrador conectados.
//
// La funcion `paso` reproduce, linea a linea, el bloque de safetyTask()
// (sketch.ino lineas 278-314) y las funciones releaseObstacle() (360-365) y
// isValidReading() (352-354). Si cambia el .ino, hay que actualizar esta copia.
// El test "constantes" comprueba que los umbrales del .ino siguen siendo los
// que se simulan aqui.
//
// Ejecutar: node --test hardware/wokwi/test/safety_logic.test.js

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const DANGER_CM = 100;
const HYSTERESIS_CM = 15;
const MAX_VALID_CM = 400;
const INVALID_READINGS_FOR_CLEAR = 3;
const COOLDOWN_MS = 5000;
const TICK_SENSOR_MS = 150;

function newState() {
  return {
    obstacleActive: false,
    reportPending: false,
    hadEvent: false,
    lastValidDistance: -1,
    lastEvent_ms: 0,
    consecutiveInvalid: 0,
    vibrator: false, // true = HIGH en Pin::VIBRATOR
  };
}

// Espejo de isValidReading(): 0 < d <= 400. measureDistanceCm() devuelve -1 en timeout.
function isValidReading(d) {
  return d > 0 && d <= MAX_VALID_CM;
}

function release(e) {
  e.obstacleActive = false;
  e.reportPending = false;
  e.vibrator = false;
}

// Un tick de safetyTask() con la lectura `d` en el instante `ahora`.
// Devuelve la lista de eventos PASSIVE encolados en este tick.
function step(e, d, now) {
  const events = [];
  if (isValidReading(d)) {
    e.consecutiveInvalid = 0;
    e.lastValidDistance = d;
    if (d <= DANGER_CM) {
      if (!e.obstacleActive) {
        e.obstacleActive = true;
        e.reportPending = true;
        e.vibrator = true;
      }
    } else if (e.obstacleActive && d > DANGER_CM + HYSTERESIS_CM) {
      release(e);
    }
  } else {
    if (++e.consecutiveInvalid >= INVALID_READINGS_FOR_CLEAR && e.obstacleActive) {
      release(e);
    }
  }

  if (e.obstacleActive && e.reportPending &&
      (!e.hadEvent || now - e.lastEvent_ms >= COOLDOWN_MS)) {
    e.lastEvent_ms = now;
    e.hadEvent = true;
    e.reportPending = false;
    events.push({ mode: 'PASSIVE', distanceCm: e.lastValidDistance, t: now });
  }
  return events;
}

// Ejecuta una secuencia de lecturas, una por tick de 150 ms, empezando en t=0.
function run(e, readings, tStart = 0) {
  const events = [];
  let t = tStart;
  for (const d of readings) {
    events.push(...step(e, d, t));
    t += TICK_SENSOR_MS;
  }
  return { events, t };
}

test('constantes del .ino coinciden con las simuladas', () => {
  const ino = fs.readFileSync(path.join(__dirname, '..', 'sketch.ino'), 'utf8');
  assert.match(ino, /DANGER_CM\s*=\s*100\.0f/);
  assert.match(ino, /HYSTERESIS_CM\s*=\s*15\.0f/);
  assert.match(ino, /MAX_VALID_CM\s*=\s*400\.0f/);
  assert.match(ino, /INVALID_READINGS_FOR_CLEAR\s*=\s*3/);
  assert.match(ino, /COOLDOWN_MS\s*=\s*5000/);
  assert.match(ino, /TICK_SENSOR_MS\s*=\s*150/);
});

test('entra en PELIGRO exactamente a 100 cm y activa el vibrador al instante', () => {
  const e = newState();
  const r1 = step(e, 101, 0);
  assert.equal(e.vibrator, false, '101 cm no es peligro');
  assert.equal(r1.length, 0);
  step(e, 100, 150);
  assert.equal(e.obstacleActive, true);
  assert.equal(e.vibrator, true);
});

test('no sale de PELIGRO a 115 cm (frontera de histeresis) y si sale a 116 cm', () => {
  const e = newState();
  step(e, 90, 0);
  step(e, 115, 150);
  assert.equal(e.vibrator, true, '115 cm no debe liberar');
  step(e, 116, 300);
  assert.equal(e.vibrator, false, '116 cm debe liberar');
  assert.equal(e.obstacleActive, false);
});

test('zona de histeresis (100 < d <= 115) no re-activa ni libera', () => {
  const e = newState();
  step(e, 100, 0);
  step(e, 107, 150);
  assert.equal(e.vibrator, true);
  step(e, 115, 300);
  assert.equal(e.vibrator, true);
});

test('lecturas invalidas aisladas (0, >400, timeout -1) no activan el vibrador', () => {
  for (const d of [0, -1, 401, 500]) {
    const e = newState();
    const r = step(e, d, 0);
    assert.equal(e.vibrator, false, `lectura ${d} no debe activar`);
    assert.equal(r.length, 0, `lectura ${d} no debe emitir evento`);
  }
});

test('muchas lecturas invalidas seguidas en reposo no activan el vibrador', () => {
  const e = newState();
  const { events } = run(e, [0, -1, 401, 0, -1, 0, 450, -1]);
  assert.equal(e.vibrator, false);
  assert.equal(events.length, 0);
});

test('una lectura invalida aislada durante PELIGRO no libera; tres seguidas si', () => {
  const e = newState();
  step(e, 80, 0);
  step(e, -1, 150);
  step(e, 80, 300);
  assert.equal(e.vibrator, true, 'una invalida aislada se ignora');
  step(e, -1, 450);
  step(e, 0, 600);
  assert.equal(e.vibrator, true, 'dos invalidas no bastan');
  step(e, 401, 750);
  assert.equal(e.vibrator, false, 'tres invalidas seguidas = camino libre');
});

test('un evento PASSIVE por episodio y cooldown de 5 s entre eventos', () => {
  const e = newState();
  // Episodio 1 en t=0: evento inmediato.
  const a = step(e, 90, 0);
  assert.equal(a.length, 1);
  assert.equal(a[0].t, 0);
  // Mantener peligro no genera mas eventos.
  const b = run(e, [90, 90, 90], 150).events;
  assert.equal(b.length, 0, 'mismo episodio: sin reenvio');
  // Salir (t=600) y reentrar (t=750) dentro del cooldown: pendiente, no inmediato.
  step(e, 150, 600);
  const c = step(e, 90, 750);
  assert.equal(c.length, 0, 'dentro de 5 s no se reporta');
  // Justo antes de 5 s desde el evento (t=4999) no se envia...
  assert.equal(step(e, 90, 4999).length, 0);
  // ...y en t=5000 se envia el pendiente (el obstaculo sigue ahi).
  const d = step(e, 90, 5000);
  assert.equal(d.length, 1);
  assert.equal(d[0].t, 5000);
});

test('si el obstaculo se va antes de cumplirse el cooldown, el reporte pendiente se cancela', () => {
  const e = newState();
  step(e, 90, 0); // evento en t=0
  step(e, 150, 300);
  step(e, 90, 450); // pendiente
  step(e, 150, 600);
  step(e, 150, 750);
  step(e, 150, 900);
  step(e, 150, 1050);
  const r = run(e, [150, 150, 150, 150, 150, 150, 150, 150, 150, 150, 150, 150], 1200).events;
  assert.equal(r.length, 0, 'sin obstaculo no hay reporte');
  assert.equal(e.reportPending, false);
});

test('tras el cooldown, un nuevo episodio se reporta en cuanto entra en peligro', () => {
  const e = newState();
  step(e, 90, 0);
  step(e, 150, 150);
  const r = step(e, 90, 6000);
  assert.equal(r.length, 1, 'han pasado mas de 5 s desde el ultimo evento');
});

test('timeout del sensor (-1) mientras hay peligro cuenta como lectura invalida, no como distancia', () => {
  const e = newState();
  step(e, 90, 0);
  step(e, -1, 150);
  step(e, -1, 300);
  assert.equal(e.lastValidDistance, 90, 'la distancia reportada sigue siendo la ultima valida');
});
