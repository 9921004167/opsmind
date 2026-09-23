package com.opsmind.core.common;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Generates human-readable, sequential incident numbers (e.g. INC-000042) backed by a
 * real Postgres sequence (see V2 migration) - not an in-memory counter, so it is safe
 * across multiple application instances.
 */
@Component
public class IncidentNumberGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public String next() {
        Number value = (Number) entityManager
                .createNativeQuery("SELECT nextval('incident_number_seq')")
                .getSingleResult();
        return String.format("INC-%06d", value.longValue());
    }
}
