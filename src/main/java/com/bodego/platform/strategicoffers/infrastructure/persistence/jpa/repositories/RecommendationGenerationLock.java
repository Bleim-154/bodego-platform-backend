package com.bodego.platform.strategicoffers.infrastructure.persistence.jpa.repositories;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RecommendationGenerationLock {

    private final EntityManager entityManager;

    public RecommendationGenerationLock(
            EntityManager entityManager
    ) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(
            Long businessId
    ) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be positive");
        }

        // Separate advisory-lock namespace for Strategic Offers. Hash collisions only serialize extra businesses.
        // PostgreSQL releases the lock on transaction commit or rollback, across application instances.
        entityManager.createNativeQuery(
                        "SELECT count(*) FROM pg_advisory_xact_lock(:namespace, :businessKey)")
                .setParameter("namespace", 1397704262)
                .setParameter("businessKey", Long.hashCode(businessId))
                .getSingleResult();
    }
}
