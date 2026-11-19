// Pruebas SIMULADAS de la logica de seguridad de hardware/wokwi/sketch.ino.
// NO son pruebas fisicas: no hay HC-SR04 ni motor vibrador conectados.
//
// La funcion `paso` reproduce, linea a linea, el bloque de tareaSeguridad()
// (sketch.ino lineas 278-314) y las funciones liberarObstaculo() (360-365) y
// lecturaValida() (352-354). Si cambia el .ino, hay que actualizar esta copia.
// El test "constantes" comprueba que los umbrales del .ino siguen siendo los
// que se simulan aqui.
//
// Ejecutar: node --test hardware/wokwi/test/logica_seguridad.test.js

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const PELIGRO_CM = 100;
const HISTERESIS_CM = 15;
const MAX_VALIDA_CM = 400;
const LECTURAS_INVALIDAS_PARA_LIBRE = 3;
const COOLDOWN_MS = 5000;
const TICK_SENSOR_MS = 150;

function nuevoEstado() {
  return {
    obstaculoActivo: false,
    reportePendiente: false,
    huboEvento: false,
    ultimaDistanciaValida: -1,
    ultimoEvento_ms: 0,
    invalidasSeguidas: 0,
    vibrador: false, // true = HIGH en Pin::VIBRADOR
  };
}

// Espejo de lecturaValida(): 0 < d <= 400. medirDistanciaCm() devuelve -1 en timeout.
function lecturaValida(d) {
  return d > 0 && d <= MAX_VALIDA_CM;
}

function liberar(e) {
  e.obstaculoActivo = false;
  e.reportePendiente = false;
  e.vibrador = false;
}

// Un tick de tareaSeguridad() con la lectura `d` en el instante `ahora`.
// Devuelve la lista de eventos PASIVO encolados en este tick.
function paso(e, d, ahora) {
  const eventos = [];
  if (lecturaValida(d)) {
    e.invalidasSeguidas = 0;
    e.ultimaDistanciaValida = d;
    if (d <= PELIGRO_CM) {
      if (!e.obstaculoActivo) {
        e.obstaculoActivo = true;
        e.reportePendiente = true;
        e.vibrador = true;
      }
    } else if (e.obstaculoActivo && d > PELIGRO_CM + HISTERESIS_CM) {
      liberar(e);
    }
  } else {
    if (++e.invalidasSeguidas >= LECTURAS_INVALIDAS_PARA_LIBRE && e.obstaculoActivo) {
      liberar(e);
    }
  }

  if (e.obstaculoActivo && e.reportePendiente &&
      (!e.huboEvento || ahora - e.ultimoEvento_ms >= COOLDOWN_MS)) {
    e.ultimoEvento_ms = ahora;
    e.huboEvento = true;
    e.reportePendiente = false;
    eventos.push({ modo: 'PASIVO', distanciaCm: e.ultimaDistanciaValida, t: ahora });
  }
  return eventos;
}

// Ejecuta una secuencia de lecturas, una por tick de 150 ms, empezando en t=0.
function correr(e, lecturas, tInicio = 0) {
  const eventos = [];
  let t = tInicio;
  for (const d of lecturas) {
    eventos.push(...paso(e, d, t));
    t += TICK_SENSOR_MS;
  }
  return { eventos, t };
}

test('constantes del .ino coinciden con las simuladas', () => {
  const ino = fs.readFileSync(path.join(__dirname, '..', 'sketch.ino'), 'utf8');
  assert.match(ino, /PELIGRO_CM\s*=\s*100\.0f/);
  assert.match(ino, /HISTERESIS_CM\s*=\s*15\.0f/);
  assert.match(ino, /MAX_VALIDA_CM\s*=\s*400\.0f/);
  assert.match(ino, /LECTURAS_INVALIDAS_PARA_LIBRE\s*=\s*3/);
  assert.match(ino, /COOLDOWN_MS\s*=\s*5000/);
  assert.match(ino, /TICK_SENSOR_MS\s*=\s*150/);
});

test('entra en PELIGRO exactamente a 100 cm y activa el vibrador al instante', () => {
  const e = nuevoEstado();
  const r1 = paso(e, 101, 0);
  assert.equal(e.vibrador, false, '101 cm no es peligro');
  assert.equal(r1.length, 0);
  paso(e, 100, 150);
  assert.equal(e.obstaculoActivo, true);
  assert.equal(e.vibrador, true);
});

test('no sale de PELIGRO a 115 cm (frontera de histeresis) y si sale a 116 cm', () => {
  const e = nuevoEstado();
  paso(e, 90, 0);
  paso(e, 115, 150);
  assert.equal(e.vibrador, true, '115 cm no debe liberar');
  paso(e, 116, 300);
  assert.equal(e.vibrador, false, '116 cm debe liberar');
  assert.equal(e.obstaculoActivo, false);
});

test('zona de histeresis (100 < d <= 115) no re-activa ni libera', () => {
  const e = nuevoEstado();
  paso(e, 100, 0);
  paso(e, 107, 150);
  assert.equal(e.vibrador, true);
  paso(e, 115, 300);
  assert.equal(e.vibrador, true);
});

test('lecturas invalidas aisladas (0, >400, timeout -1) no activan el vibrador', () => {
  for (const d of [0, -1, 401, 500]) {
    const e = nuevoEstado();
    const r = paso(e, d, 0);
    assert.equal(e.vibrador, false, `lectura ${d} no debe activar`);
    assert.equal(r.length, 0, `lectura ${d} no debe emitir evento`);
  }
});

test('muchas lecturas invalidas seguidas en reposo no activan el vibrador', () => {
  const e = nuevoEstado();
  const { eventos } = correr(e, [0, -1, 401, 0, -1, 0, 450, -1]);
  assert.equal(e.vibrador, false);
  assert.equal(eventos.length, 0);
});

test('una lectura invalida aislada durante PELIGRO no libera; tres seguidas si', () => {
  const e = nuevoEstado();
  paso(e, 80, 0);
  paso(e, -1, 150);
  paso(e, 80, 300);
  assert.equal(e.vibrador, true, 'una invalida aislada se ignora');
  paso(e, -1, 450);
  paso(e, 0, 600);
  assert.equal(e.vibrador, true, 'dos invalidas no bastan');
  paso(e, 401, 750);
  assert.equal(e.vibrador, false, 'tres invalidas seguidas = camino libre');
});

test('un evento PASIVO por episodio y cooldown de 5 s entre eventos', () => {
  const e = nuevoEstado();
  // Episodio 1 en t=0: evento inmediato.
  const a = paso(e, 90, 0);
  assert.equal(a.length, 1);
  assert.equal(a[0].t, 0);
  // Mantener peligro no genera mas eventos.
  const b = correr(e, [90, 90, 90], 150).eventos;
  assert.equal(b.length, 0, 'mismo episodio: sin reenvio');
  // Salir (t=600) y reentrar (t=750) dentro del cooldown: pendiente, no inmediato.
  paso(e, 150, 600);
  const c = paso(e, 90, 750);
  assert.equal(c.length, 0, 'dentro de 5 s no se reporta');
  // Justo antes de 5 s desde el evento (t=4999) no se envia...
  assert.equal(paso(e, 90, 4999).length, 0);
  // ...y en t=5000 se envia el pendiente (el obstaculo sigue ahi).
  const d = paso(e, 90, 5000);
  assert.equal(d.length, 1);
  assert.equal(d[0].t, 5000);
});

test('si el obstaculo se va antes de cumplirse el cooldown, el reporte pendiente se cancela', () => {
  const e = nuevoEstado();
  paso(e, 90, 0); // evento en t=0
  paso(e, 150, 300);
  paso(e, 90, 450); // pendiente
  paso(e, 150, 600);
  paso(e, 150, 750);
  paso(e, 150, 900);
  paso(e, 150, 1050);
  const r = correr(e, [150, 150, 150, 150, 150, 150, 150, 150, 150, 150, 150, 150], 1200).eventos;
  assert.equal(r.length, 0, 'sin obstaculo no hay reporte');
  assert.equal(e.reportePendiente, false);
});

test('tras el cooldown, un nuevo episodio se reporta en cuanto entra en peligro', () => {
  const e = nuevoEstado();
  paso(e, 90, 0);
  paso(e, 150, 150);
  const r = paso(e, 90, 6000);
  assert.equal(r.length, 1, 'han pasado mas de 5 s desde el ultimo evento');
});

test('timeout del sensor (-1) mientras hay peligro cuenta como lectura invalida, no como distancia', () => {
  const e = nuevoEstado();
  paso(e, 90, 0);
  paso(e, -1, 150);
  paso(e, -1, 300);
  assert.equal(e.ultimaDistanciaValida, 90, 'la distancia reportada sigue siendo la ultima valida');
});
