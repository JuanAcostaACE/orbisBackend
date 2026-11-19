package com.orbis.api;

import com.orbis.api.model.CapturaFotografia;
import com.orbis.api.model.CapturaImagen;
import com.orbis.api.model.EstadoCaptura;
import com.orbis.api.repository.CapturaImagenRepository;
import com.orbis.api.service.CapturaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Retención de bytes: las imágenes de más de 24 h se borran y la captura pendiente queda FALLIDA. Sin worker. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:retencion-imagenes;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.analisis.activo=false"
})
class RetencionImagenesTest {

    static final String DUENO = "retencion-dueno@test.com";

    @Autowired CapturaService capturas;
    @Autowired CapturaImagenRepository imagenes;

    @Test
    @DisplayName("Imagen de más de 24 h: se borra; captura RECIBIDA pasa a FALLIDA (IMAGEN_CADUCADA)")
    void caduca_imagen_antigua() {
        String id = "e4051627-3849-4eaf-8fb1-3c4d5e6f7081";
        byte[] cuerpo = CapturaValidacionTest.jpeg(20 * 1024, (byte) 0x31);
        CapturaFotografia captura = capturas.recibir(DUENO, id, cuerpo).captura();

        CapturaImagen img = imagenes.findByCaptura_Id(captura.getId()).orElseThrow();
        img.setCreadaEn(LocalDateTime.now().minusHours(25));
        imagenes.saveAndFlush(img);

        assertEquals(1, capturas.caducarImagenesAntiguas());
        assertEquals(0, imagenes.countByCaptura_Id(captura.getId()));
        CapturaFotografia tras = capturas.obtener(DUENO, id).orElseThrow();
        assertEquals(EstadoCaptura.FALLIDA, tras.getEstado());
        assertEquals("IMAGEN_CADUCADA", tras.getMotivoFallo());
    }

    @Test
    @DisplayName("Imagen reciente: no se borra")
    void conserva_imagen_reciente() {
        String id = "f5162738-495a-4fb0-9fc2-4d5e6f708192";
        CapturaFotografia captura = capturas.recibir(DUENO, id,
                CapturaValidacionTest.jpeg(20 * 1024, (byte) 0x32)).captura();
        capturas.caducarImagenesAntiguas();
        assertEquals(1, imagenes.countByCaptura_Id(captura.getId()));
        assertTrue(capturas.contenidoImagenVigente(captura.getId()).isPresent());
    }
}
