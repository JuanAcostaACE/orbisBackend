package com.orbis.api.strategy;

import com.orbis.api.model.RegistroEvento;

public interface ModoProcesamientoStrategy {
    RegistroEvento procesar(RegistroEvento eventoRequest);
}