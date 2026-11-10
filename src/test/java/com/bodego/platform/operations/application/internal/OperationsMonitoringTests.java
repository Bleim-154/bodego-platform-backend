package com.bodego.platform.operations.application.internal;

import com.bodego.platform.inventory.domain.model.aggregates.Lot;
import com.bodego.platform.inventory.domain.model.commands.DecreaseLotQuantityCommand;
import com.bodego.platform.inventory.domain.model.commands.IncreaseLotQuantityCommand;
import com.bodego.platform.inventory.domain.model.queries.GetLotByIdQuery;
import com.bodego.platform.inventory.domain.services.LotCommandService;
import com.bodego.platform.inventory.domain.services.LotQueryService;
import com.bodego.platform.catalog.domain.services.ProductQueryService;
import com.bodego.platform.catalog.interfaces.acl.CatalogContextFacade;
import com.bodego.platform.inventory.interfaces.acl.InventoryContextFacade;
import com.bodego.platform.monitoring.domain.model.commands.RecordAuditLogCommand;
import com.bodego.platform.monitoring.domain.services.AuditLogCommandService;
import com.bodego.platform.monitoring.interfaces.acl.MonitoringContextFacade;
import com.bodego.platform.operations.application.internal.commandservices.*;
import com.bodego.platform.operations.application.internal.outboundservices.acl.ExternalInventoryService;
import com.bodego.platform.operations.application.internal.outboundservices.acl.ExternalMonitoringService;
import com.bodego.platform.operations.domain.model.aggregates.*;
import com.bodego.platform.operations.domain.model.commands.*;
import com.bodego.platform.operations.domain.model.valueobjects.*;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationsMonitoringTests {

    private final InventoryMovementRepository movements = proxy(InventoryMovementRepository.class);
    private final PhysicalCountRepository counts = proxy(PhysicalCountRepository.class);
    private final StockAdjustmentRepository adjustments = proxy(StockAdjustmentRepository.class);
    private final WasteRepository wastes = proxy(WasteRepository.class);
    private final CorrectionRequestRepository requests = proxy(CorrectionRequestRepository.class);
    private final LotQueryService lots = proxy(LotQueryService.class);
    private final LotCommandService lotCommands = proxy(LotCommandService.class);
    private final AuditLogCommandService auditCommands = proxy(AuditLogCommandService.class);
    private final ExternalMonitoringService monitoring =
            new ExternalMonitoringService(new MonitoringContextFacade(auditCommands));
    private final CatalogContextFacade catalogFacade = new CatalogContextFacade(proxy(ProductQueryService.class));
    private final InventoryContextFacade inventoryFacade = new InventoryContextFacade(lotCommands, lots, catalogFacade);
    private final ExternalInventoryService catalog = new ExternalInventoryService(inventoryFacade);
    private final InventoryMovementCommandServiceImpl movementService =
            new InventoryMovementCommandServiceImpl(movements, catalog, monitoring);
    private final PhysicalCountCommandServiceImpl countService =
            new PhysicalCountCommandServiceImpl(counts, catalog, monitoring);
    private final StockAdjustmentCommandServiceImpl adjustmentService =
            new StockAdjustmentCommandServiceImpl(adjustments, counts, movementService, catalog, monitoring);
    private final WasteCommandServiceImpl wasteService =
            new WasteCommandServiceImpl(wastes, movementService, monitoring);
    private final CorrectionRequestCommandServiceImpl requestService =
            new CorrectionRequestCommandServiceImpl(requests, counts, adjustments, adjustmentService, monitoring);
    private final List<RecordAuditLogCommand> recorded = new ArrayList<>();
    private final Lot lot = new Lot(1L, 2L, "Batch", 10, null);

    private static <T> T proxy(Class<T> type) {
        return mock(type, withSettings().mockMaker(MockMakers.PROXY));
    }

    private Object identified(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    @BeforeEach
    void setUp() {
        identified(lot, 3L);
        when(lots.handle(new GetLotByIdQuery(3L))).thenReturn(Optional.of(lot));
        when(lotCommands.handle(any(IncreaseLotQuantityCommand.class))).thenReturn(Optional.of(lot));
        when(lotCommands.handle(any(DecreaseLotQuantityCommand.class))).thenReturn(Optional.of(lot));
        when(movements.save(any())).thenAnswer(call -> identified(call.getArgument(0), 11L));
        when(counts.save(any())).thenAnswer(call -> identified(call.getArgument(0), 12L));
        when(adjustments.save(any())).thenAnswer(call -> identified(call.getArgument(0), 13L));
        when(wastes.save(any())).thenAnswer(call -> identified(call.getArgument(0), 14L));
        when(requests.save(any())).thenAnswer(call -> identified(call.getArgument(0), 15L));
        when(auditCommands.handle(any())).thenAnswer(call -> {
            recorded.add(call.getArgument(0));
            return (long) recorded.size();
        });
    }

    private PhysicalCount count(int counted) {
        var count = new PhysicalCount(1L, 3L, 10, counted, "Private observation");
        identified(count, 12L);
        when(counts.findById(12L)).thenReturn(Optional.of(count));
        return count;
    }

    private CorrectionRequest pending() {
        var request = new CorrectionRequest(1L, 12L, "Private reason");
        identified(request, 15L);
        when(requests.findWithLockById(15L)).thenReturn(Optional.of(request));
        return request;
    }

    private void assertActions(String... actions) {
        assertEquals(List.of(actions), recorded.stream().map(RecordAuditLogCommand::action).toList());
        assertTrue(recorded.stream().allMatch(log -> log.userId() == null
                && log.businessId().equals(1L) && log.entityId() != null
                && !log.description().contains("Private")));
    }

    @Test
    void inventoryMovementRecordsExactlyOneActionAfterPersistence() {
        assertEquals(11L, movementService.handle(
                new CreateInventoryMovementCommand(1L, 3L, MovementType.ENTRY, 2, "Private reason")));
        assertActions("INVENTORY_MOVEMENT_REGISTERED");
        assertEquals("InventoryMovement", recorded.getFirst().entityType());
        assertEquals(11L, recorded.getFirst().entityId());
        var order = inOrder(movements, auditCommands);
        order.verify(movements).save(any());
        order.verify(auditCommands).handle(any());
    }

    @Test
    void physicalCountRecordsItsPersistedIdentity() {
        assertEquals(12L, countService.handle(new CreatePhysicalCountCommand(1L, 3L, 8, "Private observation")));
        assertActions("PHYSICAL_COUNT_CREATED");
        assertEquals("PhysicalCount", recorded.getFirst().entityType());
        assertEquals(12L, recorded.getFirst().entityId());
    }

    @Test
    void wasteRecordsMovementAndWasteOnceEach() {
        assertEquals(14L, wasteService.handle(
                new RegisterWasteCommand(1L, 3L, 2, WasteReason.DAMAGED, "Private observation")));
        assertActions("INVENTORY_MOVEMENT_REGISTERED", "WASTE_REGISTERED");
        assertEquals("Waste", recorded.getLast().entityType());
        assertEquals(14L, recorded.getLast().entityId());
    }

    @Test
    void adjustmentRecordsMovementThenAdjustmentAfterFlush() {
        count(8);
        assertEquals(13L, adjustmentService.handle(new ApplyStockAdjustmentCommand(1L, 12L, "Private reason")));
        assertActions("INVENTORY_MOVEMENT_REGISTERED", "STOCK_ADJUSTMENT_APPLIED");
        assertEquals(13L, recorded.getLast().entityId());
        var order = inOrder(adjustments, auditCommands);
        order.verify(auditCommands).handle(any());
        order.verify(adjustments).save(any());
        order.verify(adjustments).flush();
        order.verify(auditCommands).handle(any());
    }

    @Test
    void correctionCreationRecordsOnlyRequest() {
        count(8);
        assertEquals(15L, requestService.handle(new CreateCorrectionRequestCommand(1L, 12L, "Private reason")));
        assertActions("CORRECTION_REQUEST_CREATED");
        assertEquals("CorrectionRequest", recorded.getFirst().entityType());
        assertEquals(15L, recorded.getFirst().entityId());
    }

    @Test
    void approvalRecordsMovementAdjustmentAndRequestOnceEach() {
        count(8);
        pending();
        assertEquals(15L, requestService.handle(new ReviewCorrectionRequestCommand(
                1L, 15L, CorrectionRequestStatus.APPROVED, "Private comment")));
        assertActions("INVENTORY_MOVEMENT_REGISTERED", "STOCK_ADJUSTMENT_APPLIED",
                "CORRECTION_REQUEST_APPROVED");
        assertEquals("CorrectionRequest", recorded.getLast().entityType());
    }

    @Test
    void rejectionRecordsOnlyRequestAndCannotBeRepeated() {
        pending();
        requestService.handle(new ReviewCorrectionRequestCommand(
                1L, 15L, CorrectionRequestStatus.REJECTED, "Private comment"));
        assertActions("CORRECTION_REQUEST_REJECTED");
        assertThrows(IllegalStateException.class, () -> requestService.handle(
                new ReviewCorrectionRequestCommand(1L, 15L, CorrectionRequestStatus.REJECTED, null)));
        assertActions("CORRECTION_REQUEST_REJECTED");
        verifyNoInteractions(adjustments, movements);
    }

    @Test
    void movementPersistenceFailureDoesNotRecordSuccess() {
        doThrow(new IllegalStateException("Persistence failed")).when(movements).save(any());
        assertThrows(IllegalStateException.class, () -> movementService.handle(
                new CreateInventoryMovementCommand(1L, 3L, MovementType.ENTRY, 2, "Reason")));
        verifyNoInteractions(auditCommands);
    }

    @Test
    void insufficientStockDoesNotRecordWasteOrMovement() {
        when(lotCommands.handle(any(DecreaseLotQuantityCommand.class)))
                .thenThrow(new IllegalArgumentException("Insufficient quantity in lot"));
        assertThrows(IllegalArgumentException.class, () -> wasteService.handle(
                new RegisterWasteCommand(1L, 3L, 20, WasteReason.DAMAGED, null)));
        verifyNoInteractions(auditCommands, wastes, movements);
    }

    @Test
    void invalidCountAndDuplicateAdjustmentDoNotRecordSuccess() {
        assertThrows(IllegalArgumentException.class, () -> countService.handle(
                new CreatePhysicalCountCommand(1L, 3L, -1, null)));
        count(8);
        when(adjustments.existsByPhysicalCountId(12L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> adjustmentService.handle(
                new ApplyStockAdjustmentCommand(1L, 12L, "Reason")));
        verifyNoInteractions(auditCommands, movements);
    }

    @Test
    void failedApprovalAndCrossBusinessRequestDoNotRecordSuccess() {
        count(8);
        pending();
        when(adjustments.existsByPhysicalCountId(12L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> requestService.handle(
                new ReviewCorrectionRequestCommand(1L, 15L, CorrectionRequestStatus.APPROVED, null)));
        assertThrows(IllegalArgumentException.class, () -> requestService.handle(
                new CreateCorrectionRequestCommand(9L, 12L, "Reason")));
        verifyNoInteractions(auditCommands);
        verify(requests, never()).save(any());
    }

    @Test
    void auditFailurePropagatesToCaller() {
        when(auditCommands.handle(any())).thenThrow(new IllegalStateException("Audit failed"));
        assertThrows(IllegalStateException.class, () -> wasteService.handle(
                new RegisterWasteCommand(1L, 3L, 2, WasteReason.DAMAGED, null)));
        verifyNoInteractions(wastes);
        verify(auditCommands, times(1)).handle(any());
    }
}
