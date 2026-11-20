package com.orbis.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Bytes JPEG de una captura, SOLO mientras dura el análisis (decisión de retención del analizador YOLO).
 *
 * Política: la fila se borra al terminar el análisis (DESCRITA o FALLIDA) o, como máximo, 24 h
 * después de la recepción, lo que ocurra antes. El borrado por antigüedad lo ejecuta
 * RetencionImagenes cada pocos minutos; además, el worker nunca usa bytes con más de 24 h.
 * Los bytes no se registran en logs ni se exponen por la API.
 */
@Getter
@Setter
@Entity
@Table(name = "capturas_imagen")
public class CapturaImagen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "captura_id", nullable = false, unique = true)
    private CapturaFotografia captura;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "contenido", nullable = false, length = 524288)
    private byte[] contenido;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;
}
