package com.bodego.platform.operations.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.operations.domain.model.aggregates.StockAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockAdjustmentRepository
        extends JpaRepository<StockAdjustment, Long> {

    List<StockAdjustment> findByBusinessId(Long businessId);

    List<StockAdjustment> findByLotId(Long lotId);

    Optional<StockAdjustment> findByPhysicalCountId(Long physicalCountId);

    boolean existsByPhysicalCountId(Long physicalCountId);
}
