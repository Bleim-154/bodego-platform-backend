package com.bodego.platform.operations.domain.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.operations.domain.model.aggregates.PhysicalCount;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

@Repository
public interface PhysicalCountRepository extends JpaRepository<PhysicalCount, Long>{

    List<PhysicalCount> findByBusinessId(Long businessId);
    List<PhysicalCount> findByLotId(Long lotId);
}
