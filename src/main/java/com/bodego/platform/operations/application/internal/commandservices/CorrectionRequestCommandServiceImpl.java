package com.bodego.platform.operations.application.internal.commandservices;

import com.bodego.platform.operations.domain.model.aggregates.CorrectionRequest;
import com.bodego.platform.operations.domain.model.commands.ApplyStockAdjustmentCommand;
import com.bodego.platform.operations.domain.model.commands.CreateCorrectionRequestCommand;
import com.bodego.platform.operations.domain.model.commands.ReviewCorrectionRequestCommand;
import com.bodego.platform.operations.domain.model.valueobjects.CorrectionRequestStatus;
import com.bodego.platform.operations.domain.services.CorrectionRequestCommandService;
import com.bodego.platform.operations.domain.services.StockAdjustmentCommandService;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.CorrectionRequestRepository;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.PhysicalCountRepository;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.StockAdjustmentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorrectionRequestCommandServiceImpl
        implements CorrectionRequestCommandService {

    private final CorrectionRequestRepository requestRepository;
    private final PhysicalCountRepository countRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final StockAdjustmentCommandService adjustmentCommandService;

    public CorrectionRequestCommandServiceImpl(
            CorrectionRequestRepository requestRepository,
            PhysicalCountRepository countRepository,
            StockAdjustmentRepository adjustmentRepository,
            StockAdjustmentCommandService adjustmentCommandService
    ) {
        this.requestRepository = requestRepository;
        this.countRepository = countRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.adjustmentCommandService = adjustmentCommandService;
    }

    @Override
    @Transactional
    public Long handle(CreateCorrectionRequestCommand command) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "Correction request command is required");
        }

        var request = new CorrectionRequest(
                command.businessId(),
                command.physicalCountId(),
                command.reason()
        );

        var count = countRepository.findById(
                request.getPhysicalCountId()
        ).orElseThrow(() -> new IllegalArgumentException(
                "Physical count not found"
        ));

        if (!count.getBusinessId().equals(request.getBusinessId())) {
            throw new IllegalArgumentException(
                    "Physical count does not belong to the business");
        }

        if (count.getDifference() == 0) {
            throw new IllegalStateException(
                    "Physical count has no stock difference");
        }

        if (adjustmentRepository.existsByPhysicalCountId(
                request.getPhysicalCountId())) {
            throw new IllegalStateException(
                    "Physical count has already been adjusted");
        }

        if (requestRepository.existsByPhysicalCountIdAndStatus(
                request.getPhysicalCountId(),
                CorrectionRequestStatus.PENDING)) {
            throw new IllegalStateException(
                    "A pending correction request already exists");
        }

        return requestRepository.save(request).getId();
    }

    @Override
    @Transactional
    public Long handle(ReviewCorrectionRequestCommand command) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "Review command is required");
        }

        if (command.businessId() == null || command.businessId() <= 0
                || command.correctionRequestId() == null
                || command.correctionRequestId() <= 0) {
            throw new IllegalArgumentException(
                    "Business ID and correction request ID must be positive");
        }

        if (command.decision() != CorrectionRequestStatus.APPROVED
                && command.decision() != CorrectionRequestStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "Decision must be APPROVED or REJECTED");
        }

        var request = requestRepository.findWithLockById(
                command.correctionRequestId()
        ).orElseThrow(() -> new IllegalArgumentException(
                "Correction request not found"
        ));

        if (!request.getBusinessId().equals(command.businessId())) {
            throw new IllegalArgumentException(
                    "Correction request does not belong to the business");
        }

        if (request.getStatus() != CorrectionRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "Correction request has already been reviewed");
        }

        if (command.decision() == CorrectionRequestStatus.APPROVED) {

            adjustmentCommandService.handle(
                    new ApplyStockAdjustmentCommand(
                            request.getBusinessId(),
                            request.getPhysicalCountId(),
                            request.getReason()
                    )
            );

            request.approve(command.reviewComment());

        } else {
            request.reject(command.reviewComment());
        }

        return requestRepository.save(request).getId();
    }
}
