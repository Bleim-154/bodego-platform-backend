package com.bodego.platform.iam.domain.model.aggregates;

import com.bodego.platform.iam.domain.exceptions.InvalidIamDataException;
import com.bodego.platform.iam.domain.model.valueobjects.EmailAddress;
import com.bodego.platform.iam.domain.model.valueobjects.IamValidation;
import com.bodego.platform.iam.domain.model.valueobjects.PasswordHash;
import com.bodego.platform.iam.domain.model.valueobjects.UserRole;
import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class User extends AuditableAbstractAggregateRoot<User> {
    @Getter
    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;
    @Getter
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "email", nullable = false, length = 254))
    private EmailAddress email;
    // Field access only: intentionally no public credential getter.
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "password_hash", nullable = false, length = 512))
    private PasswordHash passwordHash;
    @Getter
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 16)
    private UserRole role;
    @Getter
    @Column(nullable = false)
    private Boolean active;

    protected User() {}

    public User(Long businessId, String fullName, EmailAddress email, PasswordHash passwordHash, UserRole role) {
        this.businessId = IamValidation.positiveId(businessId);
        this.fullName = IamValidation.requiredText(fullName, 150, "user full name");
        if (email == null || passwordHash == null || role == null) {
            throw new InvalidIamDataException("Email, password hash and role are required");
        }
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
    }

    public String getEmail() { return email.value(); }
    /** Verify inside IAM without publishing stored credentials or depending on infrastructure. */
    public boolean matchesPassword(CharSequence candidate,
            java.util.function.BiPredicate<CharSequence, PasswordHash> verifier) {
        return verifier.test(candidate, passwordHash);
    }
    public boolean isEnabled() { return Boolean.TRUE.equals(active); }
    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }
    public void changeEmployeeStatus(boolean active) {
        if(role!=UserRole.EMPLOYEE) throw new com.bodego.platform.iam.domain.exceptions.EmployeeOnlyOperationException();
        if(active) activate(); else deactivate();
    }
}
