package com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.monitoring.domain.model.aggregates.Alert;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    boolean existsByBusinessIdAndProductIdAndLotIdAndTypeAndResolvedFalse(
            Long businessId, Long productId, Long lotId, AlertType type
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Alert> findWithLockById(Long id);

    @Query("""
            SELECT a FROM Alert a
            WHERE (:businessId IS NULL OR a.businessId = :businessId)
              AND (:productId IS NULL OR a.productId = :productId)
              AND (:lotId IS NULL OR a.lotId = :lotId)
              AND (:resolved IS NULL OR a.resolved = :resolved)
            ORDER BY a.generatedAt DESC, a.id DESC
            """)
    List<Alert> findByFilters(
            @Param("businessId") Long businessId,
            @Param("productId") Long productId,
            @Param("lotId") Long lotId,
            @Param("resolved") Boolean resolved
    );

    java.util.Optional<Alert> findByIdAndBusinessId(Long id, Long businessId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Alert> findWithLockByIdAndBusinessId(Long id, Long businessId);
}
