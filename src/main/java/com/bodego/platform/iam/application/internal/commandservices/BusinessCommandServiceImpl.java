package com.bodego.platform.iam.application.internal.commandservices;

import com.bodego.platform.iam.domain.model.aggregates.Business;
import com.bodego.platform.iam.domain.model.commands.CreateBusinessCommand;
import com.bodego.platform.iam.domain.model.commands.UpdateBusinessCommand;
import com.bodego.platform.iam.domain.model.valueobjects.IamValidation;
import com.bodego.platform.iam.domain.services.BusinessCommandService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.BusinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class BusinessCommandServiceImpl implements BusinessCommandService {
    private final BusinessRepository businesses;

    public BusinessCommandServiceImpl(BusinessRepository businesses) { this.businesses = businesses; }

    @Override
    public Long handle(CreateBusinessCommand command) {
        Objects.requireNonNull(command, "Business command is required");
        return businesses.save(new Business(command.name(), command.address(), command.phone())).getId();
    }

    @Override
    public Optional<Business> handle(UpdateBusinessCommand command) {
        Objects.requireNonNull(command, "Business command is required");
        Long id = IamValidation.positiveId(command.businessId());
        return businesses.findWithLockById(id).map(business -> {
            business.updateInformation(command.name(), command.address(), command.phone());
            return businesses.save(business);
        });
    }
}
