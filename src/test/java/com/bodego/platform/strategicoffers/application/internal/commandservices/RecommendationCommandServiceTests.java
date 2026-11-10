package com.bodego.platform.strategicoffers.application.internal.commandservices;

import com.bodego.platform.catalog.interfaces.acl.resources.ProductInventorySnapshot;
import com.bodego.platform.inventory.interfaces.acl.resources.LotInventorySnapshot;
import com.bodego.platform.monitoring.domain.model.commands.RecordAuditLogCommand;
import com.bodego.platform.monitoring.domain.services.AuditLogCommandService;
import com.bodego.platform.monitoring.interfaces.acl.MonitoringContextFacade;
import com.bodego.platform.strategicoffers.application.internal.outboundservices.acl.ExternalCatalogService;
import com.bodego.platform.strategicoffers.application.internal.outboundservices.acl.ExternalMonitoringService;
import com.bodego.platform.strategicoffers.domain.model.aggregates.Recommendation;
import com.bodego.platform.strategicoffers.domain.model.commands.*;
import com.bodego.platform.strategicoffers.domain.model.valueobjects.RecommendationType;
import com.bodego.platform.strategicoffers.infrastructure.persistence.jpa.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.*;
import org.mockito.MockMakers;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationCommandServiceTests {

    private static <T> T proxy(
            Class<T> type
    ) {
        return mock(type, withSettings().mockMaker(MockMakers.PROXY));
    }

    final AuditLogCommandService audit = mock(AuditLogCommandService.class, withSettings().mockMaker(MockMakers.PROXY));
    final ExternalMonitoringService monitoring = new ExternalMonitoringService(new MonitoringContextFacade(audit));
    final RecommendationRepository repository = proxy(RecommendationRepository.class);
    final EntityManager em = proxy(EntityManager.class);
    final Query lock = proxy(Query.class);
    final List<ProductInventorySnapshot> products = new ArrayList<>();
    final Map<Long, List<LotInventorySnapshot>> lots = new HashMap<>();
    final Instant now = Instant.parse("2026-11-10T23:59:59Z");
    final ExternalCatalogService catalog = new ExternalCatalogService(null, null) {
        public List<ProductInventorySnapshot> getProductsByBusinessId(
                Long businessId
        ) {
            return products;
        }

        public List<LotInventorySnapshot> getLotsByProductId(
                Long businessId,
                Long productId
        ) {
            return lots.getOrDefault(productId, List.of());
        }
    };
    final RecommendationCommandServiceImpl service = new RecommendationCommandServiceImpl(repository, catalog,
            new RecommendationGenerationLock(em), monitoring, Clock.fixed(now, ZoneOffset.UTC));

    @BeforeEach
    void setup() {
        when(em.createNativeQuery(anyString())).thenReturn(lock);
        when(lock.setParameter(anyString(), any())).thenReturn(lock);
        when(repository.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));
    }

    void product(
            long id,
            int minimum,
            boolean active,
            int... quantities
    ) {
        products.add(new ProductInventorySnapshot(id, 1L, minimum, "Product", active));
        var values = new ArrayList<LotInventorySnapshot>();
        for (int i = 0; i < quantities.length; i++) {
            values.add(new LotInventorySnapshot((long) i + 1, 1L, id, quantities[i], null));
        }
        lots.put(id, values);
    }

    @Test
    void stockThresholdsAreStrictAndSkipInactiveProducts() {
        product(1, 10, true, 9);
        product(2, 10, true, 10);
        product(3, 10, true, 30);
        product(4, 10, true, 31);
        product(5, 0, true, 100);
        product(6, 10, false);
        var result = service.handle(new GenerateRecommendationsCommand(1L));
        assertEquals(List.of(RecommendationType.RESTOCK, RecommendationType.OVERSTOCK), result.stream().map(Recommendation::getType).toList());
        assertTrue(result.stream().allMatch(r -> r.getGeneratedAt().equals(now)));
    }

    @Test
    void sumAndThresholdUseLong() {
        product(1, 1_000_000_000, true, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(RecommendationType.OVERSTOCK, service.handle(new GenerateRecommendationsCommand(1L)).getFirst().getType());
    }

    @Test
    void expirationIncludesBothBoundariesAndRequiresPositiveQuantity() {
        product(1, 0, true);
        var today = LocalDate.of(2026, 11, 10);
        lots.put(1L, List.of(new LotInventorySnapshot(1L, 1L, 1L, 1, today),
                new LotInventorySnapshot(2L, 1L, 1L, 1, today.plusDays(30)),
                new LotInventorySnapshot(3L, 1L, 1L, 1, today.minusDays(1)),
                new LotInventorySnapshot(4L, 1L, 1L, 1, today.plusDays(31)),
                new LotInventorySnapshot(5L, 1L, 1L, 0, today)));
        var result = service.handle(new GenerateRecommendationsCommand(1L));
        assertEquals(List.of(1L, 2L), result.stream().map(Recommendation::getLotId).toList());
    }

    @Test
    void activeDuplicateIsSkippedAndDismissedCanRegenerate() {
        product(1, 10, true);
        when(repository.existsByBusinessIdAndProductIdAndLotIdAndTypeAndDismissedFalse(1L, 1L, null, RecommendationType.RESTOCK))
                .thenReturn(true, false);
        assertTrue(service.handle(new GenerateRecommendationsCommand(1L)).isEmpty());
        assertEquals(1, service.handle(new GenerateRecommendationsCommand(1L)).size());
        verify(audit, times(1)).handle(argThat(c -> c.action().equals("RECOMMENDATION_GENERATED") && c.userId() == null));
    }

    @Test
    void dismissIsIdempotentAndMissingReturnsEmpty() {
        var entity = new Recommendation(1L, 1L, null, RecommendationType.RESTOCK, "Restock", now);
        when(repository.findWithLockById(1L)).thenReturn(Optional.of(entity));
        service.handle(new DismissRecommendationCommand(1L));
        service.handle(new DismissRecommendationCommand(1L));
        verify(repository, times(1)).save(entity);
        verify(audit, times(1)).handle(argThat(c -> c.action().equals("RECOMMENDATION_DISMISSED") && c.userId() == null));
        assertTrue(entity.isDismissed());
        assertTrue(service.handle(new DismissRecommendationCommand(2L)).isEmpty());
    }

    @Test
    void invalidCrossBusinessSnapshotIsRejected() {
        products.add(new ProductInventorySnapshot(1L, 2L, 1, "Other", true));
        assertThrows(IllegalArgumentException.class, () -> service.handle(new GenerateRecommendationsCommand(1L)));
        verify(repository, never()).save(any());
    }
}
