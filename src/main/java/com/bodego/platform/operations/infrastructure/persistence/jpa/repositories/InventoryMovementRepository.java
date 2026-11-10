package com.bodego.platform.operations.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.operations.domain.model.aggregates.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryMovementRepository
        extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findByBusinessId(Long businessId);

    List<InventoryMovement> findByLotId(Long lotId);
}
