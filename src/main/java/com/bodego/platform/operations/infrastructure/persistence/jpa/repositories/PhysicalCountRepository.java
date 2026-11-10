package com.bodego.platform.operations.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.operations.domain.model.aggregates.PhysicalCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PhysicalCountRepository
        extends JpaRepository<PhysicalCount, Long> {

    List<PhysicalCount> findByBusinessId(Long businessId);

    List<PhysicalCount> findByLotId(Long lotId);

    List<PhysicalCount> findByBusinessIdAndLotId(
            Long businessId,
            Long lotId
    );
}
