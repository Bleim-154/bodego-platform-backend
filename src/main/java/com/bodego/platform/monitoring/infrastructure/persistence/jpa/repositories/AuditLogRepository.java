package com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.monitoring.domain.model.aggregates.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:businessId IS NULL OR a.businessId = :businessId)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:entityId IS NULL OR a.entityId = :entityId)
            ORDER BY a.occurredAt DESC, a.id DESC
            """)
    List<AuditLog> findByFilters(
            @Param("businessId") Long businessId,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId
    );
}
