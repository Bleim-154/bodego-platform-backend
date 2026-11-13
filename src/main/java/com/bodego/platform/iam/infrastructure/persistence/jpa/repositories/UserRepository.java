package com.bodego.platform.iam.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.iam.domain.exceptions.EmailAlreadyExistsException;
import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.valueobjects.EmailAddress;
import com.bodego.platform.iam.domain.model.valueobjects.UserRole;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    Optional<User> findWithReadLockByIdAndBusinessId(Long userId,Long businessId);
    // Global lookup is restricted to the internal login flow; never exposed through ACL.
    Optional<User> findByEmail(EmailAddress email);
    boolean existsByEmail(EmailAddress email);
    boolean existsByBusinessIdAndRole(Long businessId, UserRole role);
    Optional<User> findByIdAndBusinessId(Long userId, Long businessId);
    Optional<User> findByBusinessIdAndEmail(Long businessId, EmailAddress email);
    org.springframework.data.domain.Slice<User> findAllByBusinessId(Long businessId, org.springframework.data.domain.Pageable pageable);

    /** Flush before returning so the global unique constraint also handles concurrent inserts. */
    default User persist(User user) {
        try {
            return saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uk_users_email".equals(violation.getConstraintName())) {
                    throw new EmailAlreadyExistsException();
                }
            }
            throw exception;
        }
    }
}
