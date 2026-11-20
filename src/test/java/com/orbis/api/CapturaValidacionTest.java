package com.orbis.api;

import com.orbis.api.model.EstadoCaptura;
import com.orbis.api.service.CapturaException;
import com.orbis.api.service.ValidadorJpeg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas unitarias sin contexto Spring: validación JPEG y transiciones de estado. */
class CapturaValidacionTest {

    /** JPEG mínimo válido a efectos de validación: SOI + relleno + EOI. */
    static byte[] jpeg(int tamano, byte relleno) {
        byte[] b = new byte[tamano];
        java.util.Arrays.fill(b, relleno);
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        b[3] = (byte) 0xE0;
        b[tamano - 2] = (byte) 0xFF;
        b[tamano - 1] = (byte) 0xD9;
        return b;
    }

    private static CapturaException capturar(Runnable r) {
        return assertThrows(CapturaException.class, r::run);
    }

    @Test
    @DisplayName("JPEG de tamaño razonable con SOI y EOI se acepta")
    void jpeg_valido_se_acepta() {
        assertDoesNotThrow(() -> ValidadorJpeg.validarCuerpo(jpeg(20 * 1024, (byte) 0)));
    }

    @Test
    @DisplayName("Imagen por encima de 512 KB → 413")
    void demasiado_grande_413() {
        CapturaException e = capturar(() -> ValidadorJpeg.validarCuerpo(
                jpeg(ValidadorJpeg.TAMANO_MAXIMO_BYTES + 1, (byte) 0)));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, e.getStatus());
        assertEquals("IMAGEN_DEMASIADO_GRANDE", e.getCodigo());
    }

    @Test
    @DisplayName("Imagen por debajo de 5 KB → 422")
    void demasiado_pequena_422() {
        CapturaException e = capturar(() -> ValidadorJpeg.validarCuerpo(jpeg(1024, (byte) 0)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals("IMAGEN_NO_VALIDA", e.getCodigo());
    }

    @Test
    @DisplayName("Sin marcador SOI (FF D8) → 422")
    void sin_soi_422() {
        byte[] b = jpeg(10 * 1024, (byte) 0);
        b[0] = 0x00;
        CapturaException e = capturar(() -> ValidadorJpeg.validarCuerpo(b));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @DisplayName("Sin marcador EOI (FF D9) al final, truncado → 422")
    void truncado_sin_eoi_422() {
        byte[] b = jpeg(10 * 1024, (byte) 0);
        b[b.length - 1] = 0x00;
        CapturaException e = capturar(() -> ValidadorJpeg.validarCuerpo(b));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @DisplayName("Cuerpo vacío → 413 si es nulo, 422 si está vacío")
    void vacio() {
        CapturaException e = capturar(() -> ValidadorJpeg.validarCuerpo(new byte[0]));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @DisplayName("Tipo distinto de image/jpeg → 415")
    void tipo_no_soportado_415() {
        CapturaException e = capturar(() -> ValidadorJpeg.validarTipo("image/png"));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getStatus());
        assertEquals("TIPO_NO_SOPORTADO", e.getCodigo());
        assertThrows(CapturaException.class, () -> ValidadorJpeg.validarTipo(null));
    }

    @Test
    @DisplayName("image/jpeg con parámetros (charset) se acepta")
    void tipo_jpeg_con_parametros() {
        assertDoesNotThrow(() -> ValidadorJpeg.validarTipo("image/jpeg; charset=binary"));
    }

    @Test
    @DisplayName("Transiciones: RECIBIDA → EN_PROCESO → DESCRITA → ENTREGADA")
    void transiciones_validas() {
        assertTrue(EstadoCaptura.RECIBIDA.puedePasarA(EstadoCaptura.EN_PROCESO));
        assertTrue(EstadoCaptura.EN_PROCESO.puedePasarA(EstadoCaptura.DESCRITA));
        assertTrue(EstadoCaptura.DESCRITA.puedePasarA(EstadoCaptura.ENTREGADA));
    }

    @Test
    @DisplayName("Transiciones inválidas y estados terminales no retroceden")
    void transiciones_invalidas() {
        assertFalse(EstadoCaptura.DESCRITA.puedePasarA(EstadoCaptura.RECIBIDA));
        assertFalse(EstadoCaptura.RECIBIDA.puedePasarA(EstadoCaptura.ENTREGADA));
        assertFalse(EstadoCaptura.FALLIDA.puedePasarA(EstadoCaptura.EN_PROCESO));
        assertFalse(EstadoCaptura.ENTREGADA.puedePasarA(EstadoCaptura.FALLIDA));
    }

    @Test
    @DisplayName("Repetir el mismo estado es idempotente")
    void mismo_estado_idempotente() {
        for (EstadoCaptura e : EstadoCaptura.values()) {
            assertTrue(e.puedePasarA(e));
        }
    }
}
