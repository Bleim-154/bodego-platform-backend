package com.bodego.platform.monitoring.application.internal.commandservices;

import com.bodego.platform.catalog.domain.model.aggregates.Lot;
import com.bodego.platform.catalog.domain.model.aggregates.Product;
import com.bodego.platform.catalog.domain.model.queries.GetLotsByProductIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetProductByIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetProductsByBusinessIdQuery;
import com.bodego.platform.catalog.domain.services.LotCommandService;
import com.bodego.platform.catalog.domain.services.LotQueryService;
import com.bodego.platform.catalog.domain.services.ProductQueryService;
import com.bodego.platform.catalog.interfaces.acl.CatalogContextFacade;
import com.bodego.platform.monitoring.application.internal.outboundservices.acl.ExternalCatalogService;
import com.bodego.platform.monitoring.domain.model.aggregates.Alert;
import com.bodego.platform.monitoring.domain.model.commands.GenerateAlertsCommand;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertSeverity;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertType;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AlertGenerationLock;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AlertRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertGenerationServiceTests {

    private final AlertRepository repository = proxy(AlertRepository.class);
    private final ProductQueryService products = proxy(ProductQueryService.class);
    private final LotQueryService lots = proxy(LotQueryService.class);
    private final LotCommandService lotCommands = proxy(LotCommandService.class);
    private final EntityManager entityManager = proxy(EntityManager.class);
    private final Query lockQuery = proxy(Query.class);
    private final CatalogContextFacade facade = new CatalogContextFacade(lotCommands, lots, products);
    private final List<Alert> saved = new ArrayList<>();
    private final LocalDate today = LocalDate.of(2026, 11, 10);
    private final AlertGenerationServiceImpl service = new AlertGenerationServiceImpl(
            repository, new ExternalCatalogService(facade), new AlertGenerationLock(entityManager),
            Clock.fixed(Instant.parse("2026-11-10T23:59:00Z"), ZoneOffset.UTC));

    private static <T> T proxy(Class<T> type) {
        return mock(type, withSettings().mockMaker(MockMakers.PROXY));
    }

    @BeforeEach
    void setUp() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(lockQuery);
        when(lockQuery.setParameter(anyString(), any())).thenReturn(lockQuery);
        when(lockQuery.getSingleResult()).thenReturn(1L);
        when(repository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            saved.add(alert);
            return alert;
        });
        when(repository.existsByBusinessIdAndProductIdAndLotIdAndTypeAndResolvedFalse(
                anyLong(), anyLong(), nullable(Long.class), any(AlertType.class)))
                .thenAnswer(invocation -> saved.stream().anyMatch(alert ->
                        Objects.equals(alert.getBusinessId(), invocation.getArgument(0))
                                && Objects.equals(alert.getProductId(), invocation.getArgument(1))
                                && Objects.equals(alert.getLotId(), invocation.getArgument(2))
                                && alert.getType() == invocation.getArgument(3)
                                && !alert.getResolved()));
    }

    private Product product(long businessId, int minimumStock) {
        var product = new Product(businessId, 1L, "Product", null, BigDecimal.ONE, minimumStock);
        ReflectionTestUtils.setField(product, "id", 2L);
        when(products.handle(new GetProductsByBusinessIdQuery(1L))).thenReturn(List.of(product));
        when(products.handle(new GetProductByIdQuery(2L))).thenReturn(Optional.of(product));
        return product;
    }

    private Lot lot(long id, long businessId, long productId, int quantity, LocalDate expiration) {
        var lot = new Lot(businessId, productId, "Batch-" + id, quantity, expiration);
        ReflectionTestUtils.setField(lot, "id", id);
        return lot;
    }

    private void inventory(Lot... inventory) {
        when(lots.handle(new GetLotsByProductIdQuery(2L))).thenReturn(List.of(inventory));
    }

    @Test
    void evaluatesExpirationBoundariesIncludingTodayAndSeventhDay() {
        product(1L, 0);
        inventory(lot(3L, 1L, 2L, 1, today.minusDays(1)),
                lot(4L, 1L, 2L, 1, today),
                lot(5L, 1L, 2L, 1, today.plusDays(7)),
                lot(6L, 1L, 2L, 1, today.plusDays(8)),
                lot(7L, 1L, 2L, 1, null));

        var result = service.handle(new GenerateAlertsCommand(1L));
        assertEquals(3, result.size());
        assertEquals(AlertType.EXPIRED, result.get(0).getType());
        assertEquals(AlertSeverity.HIGH, result.get(0).getSeverity());
        assertEquals(List.of(4L, 5L), result.subList(1, 3).stream().map(Alert::getLotId).toList());
        assertTrue(result.subList(1, 3).stream().allMatch(alert ->
                alert.getType() == AlertType.EXPIRING_SOON && alert.getSeverity() == AlertSeverity.MEDIUM));
    }

    @Test
    void usesSumOfAllLotsAndStrictMinimumThreshold() {
        product(1L, 10);
        inventory(lot(3L, 1L, 2L, 4, null), lot(4L, 1L, 2L, 6, null));
        assertTrue(service.handle(new GenerateAlertsCommand(1L)).isEmpty());

        inventory(lot(3L, 1L, 2L, 4, null), lot(4L, 1L, 2L, 5, null));
        var result = service.handle(new GenerateAlertsCommand(1L));
        assertEquals(1, result.size());
        assertEquals(AlertType.LOW_STOCK, result.getFirst().getType());
        assertNull(result.getFirst().getLotId());
        assertTrue(result.getFirst().getMessage().contains("stock 9"));
    }

    @Test
    void productWithoutLotsHasZeroStockAndMinimumZeroDoesNotAlert() {
        product(1L, 1);
        inventory();
        assertEquals(AlertType.LOW_STOCK,
                service.handle(new GenerateAlertsCommand(1L)).getFirst().getType());
        product(1L, 0);
        assertTrue(service.handle(new GenerateAlertsCommand(1L)).isEmpty());
    }

    @Test
    void stockSumDoesNotOverflowInteger() {
        product(1L, Integer.MAX_VALUE);
        inventory(lot(3L, 1L, 2L, Integer.MAX_VALUE, null), lot(4L, 1L, 2L, 1, null));
        assertTrue(service.handle(new GenerateAlertsCommand(1L)).isEmpty());
    }

    @Test
    void retriesDoNotDuplicateActiveAlertsButResolvedAlertCanGenerateAgain() {
        product(1L, 5);
        inventory(lot(3L, 1L, 2L, 1, today));
        var first = service.handle(new GenerateAlertsCommand(1L));
        assertEquals(2, first.size());
        assertTrue(service.handle(new GenerateAlertsCommand(1L)).isEmpty());
        first.getFirst().resolve();
        var regenerated = service.handle(new GenerateAlertsCommand(1L));
        assertEquals(1, regenerated.size());
        assertEquals(AlertType.EXPIRING_SOON, regenerated.getFirst().getType());
        assertTrue(first.getFirst().getResolved());
        assertEquals(3, saved.size());
    }

    @Test
    void noProductsReturnsEmptyAndAcquiresTransactionLockBeforeCatalogRead() {
        when(products.handle(new GetProductsByBusinessIdQuery(1L))).thenReturn(List.of());
        assertTrue(service.handle(new GenerateAlertsCommand(1L)).isEmpty());
        var order = inOrder(lockQuery, products);
        order.verify(lockQuery).getSingleResult();
        order.verify(products).handle(new GetProductsByBusinessIdQuery(1L));
        verify(lockQuery).setParameter("namespace", 1297043017);
        verify(lockQuery).setParameter("businessKey", Long.hashCode(1L));
        verifyNoInteractions(repository, lotCommands);
    }

    @Test
    void rejectsProductsFromAnotherBusinessWithoutCreatingAlerts() {
        product(9L, 5);
        assertThrows(IllegalArgumentException.class, () -> service.handle(new GenerateAlertsCommand(1L)));
        assertTrue(saved.isEmpty());
        verifyNoInteractions(repository, lots, lotCommands);
    }

    @Test
    void rejectsLotsFromAnotherBusinessOrProductWithoutCreatingAlerts() {
        product(1L, 5);
        inventory(lot(3L, 9L, 2L, 1, today));
        assertThrows(IllegalArgumentException.class, () -> service.handle(new GenerateAlertsCommand(1L)));
        inventory(lot(3L, 1L, 9L, 1, today));
        assertThrows(IllegalArgumentException.class, () -> service.handle(new GenerateAlertsCommand(1L)));
        assertTrue(saved.isEmpty());
    }

    @Test
    void snapshotQueriesValidateMissingProductAndCrossBusinessReferences() {
        when(products.handle(new GetProductByIdQuery(2L))).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> facade.getLotsByProductId(1L, 2L));
        product(9L, 5);
        assertThrows(IllegalArgumentException.class, () -> facade.getLotsByProductId(1L, 2L));
        verifyNoInteractions(lots, lotCommands);
    }

    @Test
    void validatesCommandAndFacadeInputsBeforeReadingInventory() {
        assertThrows(IllegalArgumentException.class, () -> new GenerateAlertsCommand(null));
        assertThrows(IllegalArgumentException.class, () -> new GenerateAlertsCommand(0L));
        assertThrows(IllegalArgumentException.class, () -> service.handle(null));
        assertThrows(IllegalArgumentException.class, () -> facade.getProductsByBusinessId(0L));
        assertThrows(IllegalArgumentException.class, () -> facade.getLotsByProductId(1L, -1L));
        assertThrows(IllegalArgumentException.class, () -> new AlertGenerationLock(entityManager).acquire(0L));
        verifyNoInteractions(entityManager, products, lots, repository);
    }

    @Test
    void expirationEvaluatesZeroQuantityLotsAndStockIncludesExpiredLots() {
        product(1L, 2);
        inventory(lot(3L, 1L, 2L, 2, today.minusDays(1)), lot(4L, 1L, 2L, 0, today));
        var result = service.handle(new GenerateAlertsCommand(1L));
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(alert -> alert.getType() == AlertType.LOW_STOCK));
    }

    @Test
    void componentScanKeepsOperationsAndMonitoringCatalogAdaptersDistinct() {
        var factory = new DefaultListableBeanFactory();
        factory.setAllowBeanDefinitionOverriding(false);
        var scanner = new ClassPathBeanDefinitionScanner(factory);
        assertDoesNotThrow(() -> scanner.scan("com.bodego.platform"));
        assertTrue(factory.containsBeanDefinition("externalCatalogService"));
        assertTrue(factory.containsBeanDefinition("monitoringExternalCatalogService"));
    }
}
