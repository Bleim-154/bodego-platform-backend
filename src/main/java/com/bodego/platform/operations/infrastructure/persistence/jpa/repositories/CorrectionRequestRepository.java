
package com.bodego.platform.operations.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.operations.domain.model.aggregates.CorrectionRequest;
import com.bodego.platform.operations.domain.model.valueobjects.CorrectionRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CorrectionRequestRepository
        extends JpaRepository<CorrectionRequest, Long> {

    List<CorrectionRequest> findByBusinessId(Long businessId);

    List<CorrectionRequest> findByPhysicalCountId(Long physicalCountId);

    List<CorrectionRequest> findByBusinessIdAndStatus(
            Long businessId,
            CorrectionRequestStatus status
    );

    boolean existsByPhysicalCountIdAndStatus(
            Long physicalCountId,
            CorrectionRequestStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request
            FROM CorrectionRequest request
            WHERE request.id = :id
            """)
    Optional<CorrectionRequest> findWithLockById(@Param("id") Long id);
}
