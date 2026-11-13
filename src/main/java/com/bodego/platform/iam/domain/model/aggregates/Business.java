package com.bodego.platform.iam.domain.model.aggregates;

import com.bodego.platform.iam.domain.model.valueobjects.IamValidation;
import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "businesses")
public class Business extends AuditableAbstractAggregateRoot<Business> {
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 255)
    private String address;
    @Column(nullable = false, length = 32)
    private String phone;
    @Column(nullable = false)
    private Boolean active;

    protected Business() {}

    public Business(String name, String address, String phone) {
        updateInformation(name, address, phone);
        this.active = true;
    }

    public Business updateInformation(String name, String address, String phone) {
        // Validate every value before changing the aggregate.
        String validName = IamValidation.requiredText(name, 100, "business name");
        String validAddress = IamValidation.requiredText(address, 255, "business address");
        String validPhone = IamValidation.requiredText(phone, 32, "business phone");
        this.name = validName;
        this.address = validAddress;
        this.phone = validPhone;
        return this;
    }

    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }
}
