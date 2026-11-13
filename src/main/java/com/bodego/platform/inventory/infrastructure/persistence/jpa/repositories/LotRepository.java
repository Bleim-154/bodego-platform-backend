package com.bodego.platform.inventory.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.inventory.domain.model.aggregates.Lot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {

    List<Lot> findByBusinessId(Long businessId);

    List<Lot> findByProductId(Long productId);

    List<Lot> findByProductIdAndBusinessId(
            Long productId,
            Long businessId
    );

    boolean existsByBusinessIdAndBatchNumberIgnoreCase(
            Long businessId,
            String batchNumber
    );

    boolean existsByBusinessIdAndBatchNumberIgnoreCaseAndIdNot(
            Long businessId,
            String batchNumber,
            Long id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Lot> findWithLockById(Long id);

    java.util.Optional<Lot> findByIdAndBusinessId(Long id, Long businessId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Lot> findWithLockByIdAndBusinessId(Long id, Long businessId);
}
