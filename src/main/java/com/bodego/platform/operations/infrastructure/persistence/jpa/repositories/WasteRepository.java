package com.bodego.platform.operations.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.operations.domain.model.aggregates.Waste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WasteRepository extends JpaRepository<Waste, Long> {

    List<Waste> findByBusinessId(Long businessId);

    List<Waste> findByLotId(Long lotId);

    List<Waste> findByBusinessIdAndLotId(
            Long businessId,
            Long lotId
    );
}
