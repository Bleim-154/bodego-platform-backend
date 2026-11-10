package com.bodego.platform.strategicoffers.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.strategicoffers.domain.model.aggregates.*;
import com.bodego.platform.strategicoffers.domain.model.valueobjects.RecommendationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Offer> findWithLockById(
            Long id
    );

    @Query("""
            SELECT e FROM Offer e
            WHERE (:businessId IS NULL OR e.businessId = :businessId)
              AND (:productId IS NULL OR e.productId = :productId)
              AND (:active IS NULL OR e.active = :active)
            ORDER BY e.id DESC
            """)
    List<Offer> findByFilters(
            @Param("businessId") Long businessId,
            @Param("productId") Long productId,
            @Param("active") Boolean active
    );
}
