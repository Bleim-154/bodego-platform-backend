package com.bodego.platform.strategicoffers.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.strategicoffers.domain.model.aggregates.*;
import com.bodego.platform.strategicoffers.domain.model.valueobjects.RecommendationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Recommendation> findWithLockById(
            Long id
    );

    boolean existsByBusinessIdAndProductIdAndLotIdAndTypeAndDismissedFalse(
            Long businessId,
            Long productId,
            Long lotId,
            RecommendationType type
    );

    @Query("""
            SELECT e FROM Recommendation e
            WHERE (:businessId IS NULL OR e.businessId = :businessId)
              AND (:productId IS NULL OR e.productId = :productId)
              AND (:type IS NULL OR e.type = :type)
              AND (:dismissed IS NULL OR e.dismissed = :dismissed)
            ORDER BY e.id DESC
            """)
    List<Recommendation> findByFilters(
            @Param("businessId") Long businessId,
            @Param("productId") Long productId,
            @Param("type") RecommendationType type,
            @Param("dismissed") Boolean dismissed
    );

    java.util.Optional<Recommendation> findByIdAndBusinessId(Long id, Long businessId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Recommendation> findWithLockByIdAndBusinessId(Long id, Long businessId);
}
