package com.bodego.platform.operations.application.internal.commandservices;

import com.bodego.platform.shared.application.security.*;
import com.bodego.platform.operations.domain.exceptions.*;

import com.bodego.platform.operations.application.internal.outboundservices.acl.ExternalMonitoringService;
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
    private final BusinessAccess access;

    private final CorrectionRequestRepository requestRepository;
    private final PhysicalCountRepository countRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final StockAdjustmentCommandService adjustmentCommandService;
    private final ExternalMonitoringService externalMonitoringService;

    public CorrectionRequestCommandServiceImpl(
            CorrectionRequestRepository requestRepository,
            PhysicalCountRepository countRepository,
            StockAdjustmentRepository adjustmentRepository,
            StockAdjustmentCommandService adjustmentCommandService,
            ExternalMonitoringService externalMonitoringService, BusinessAccess access) {
        this.access = access;
        this.requestRepository = requestRepository;
        this.countRepository = countRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.adjustmentCommandService = adjustmentCommandService;
        this.externalMonitoringService = externalMonitoringService;
    }

    @Override
    @Transactional
    public Long handle(CreateCorrectionRequestCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.write(BusinessPermission.CORRECTION_CREATE, command.businessId());


        if (command == null) {
            throw new IllegalArgumentException(
                    "Correction request command is required");
        }

        var request = new CorrectionRequest(
                actor.businessId(),
                command.physicalCountId(),
                command.reason()
        );

        var count = countRepository.findWithLockByIdAndBusinessId(request.getPhysicalCountId(), actor.businessId()).orElseThrow(BusinessResourceNotFoundException::new);

        if (!count.getBusinessId().equals(request.getBusinessId())) {
            throw new IllegalArgumentException(
                    "Physical count does not belong to the business");
        }

        if (count.getDifference() == 0) {
            throw new PhysicalCountWithoutDifferenceException();
        }

        if (adjustmentRepository.existsByPhysicalCountId(
                request.getPhysicalCountId())) {
            throw new PhysicalCountAlreadyAdjustedException();
        }

        if (requestRepository.existsByPhysicalCountIdAndStatus(
                request.getPhysicalCountId(),
                CorrectionRequestStatus.PENDING)) {
            throw new PendingCorrectionRequestAlreadyExistsException();
        }

        var savedRequest = requestRepository.save(request);

        externalMonitoringService.recordAction(
                savedRequest.getBusinessId(), "CORRECTION_REQUEST_CREATED",
                "CorrectionRequest", savedRequest.getId(),
                "Correction requested for physical count " + savedRequest.getPhysicalCountId());

        return savedRequest.getId();
    }

    @Override
    @Transactional
    public Long handle(ReviewCorrectionRequestCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.write(BusinessPermission.CORRECTION_REVIEW, command.businessId());


        if (command == null) {
            throw new IllegalArgumentException(
                    "Review command is required");
        }

        if (actor.businessId() == null || actor.businessId() <= 0
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

        var request = requestRepository.findWithLockByIdAndBusinessId(command.correctionRequestId(), actor.businessId()).orElseThrow(BusinessResourceNotFoundException::new);

        if (!request.getBusinessId().equals(actor.businessId())) {
            throw new IllegalArgumentException(
                    "Correction request does not belong to the business");
        }

        if (request.getStatus() != CorrectionRequestStatus.PENDING) {
            throw new CorrectionRequestAlreadyReviewedException();
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

        var savedRequest = requestRepository.save(request);

        externalMonitoringService.recordAction(
                savedRequest.getBusinessId(),
                savedRequest.getStatus() == CorrectionRequestStatus.APPROVED
                        ? "CORRECTION_REQUEST_APPROVED" : "CORRECTION_REQUEST_REJECTED",
                "CorrectionRequest", savedRequest.getId(),
                "Correction request " + savedRequest.getStatus()
                        + " for physical count " + savedRequest.getPhysicalCountId());

        return savedRequest.getId();
    }
}
