package com.bodego.platform.iam.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.iam.domain.model.aggregates.Business;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Business> findWithReadLockById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Business> findWithLockById(Long id);
}
