package com.smartcane.api.strategy;

import com.smartcane.api.model.RegistroEvento;

public interface ModoProcesamientoStrategy {
    RegistroEvento procesar(RegistroEvento eventoRequest);
}