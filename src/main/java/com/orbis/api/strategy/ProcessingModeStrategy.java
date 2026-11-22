package com.orbis.api.strategy;

import com.orbis.api.model.EventRecord;

public interface ProcessingModeStrategy {
    EventRecord process(EventRecord eventRequest);
}