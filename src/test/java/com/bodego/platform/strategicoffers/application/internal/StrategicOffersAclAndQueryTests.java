package com.bodego.platform.strategicoffers.application.internal;

import com.bodego.platform.catalog.domain.model.aggregates.Product;
import com.bodego.platform.catalog.domain.model.queries.*;
import com.bodego.platform.catalog.domain.services.*;
import com.bodego.platform.catalog.interfaces.acl.CatalogContextFacade;
import com.bodego.platform.catalog.interfaces.acl.resources.ProductInventorySnapshot;
import com.bodego.platform.inventory.domain.services.LotCommandService;
import com.bodego.platform.inventory.domain.services.LotQueryService;
import com.bodego.platform.inventory.interfaces.acl.InventoryContextFacade;
import com.bodego.platform.strategicoffers.application.internal.outboundservices.acl.ExternalCatalogService;
import com.bodego.platform.strategicoffers.application.internal.queryservices.*;
import com.bodego.platform.strategicoffers.domain.model.queries.*;
import com.bodego.platform.strategicoffers.domain.model.valueobjects.RecommendationType;
import com.bodego.platform.strategicoffers.infrastructure.persistence.jpa.repositories.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StrategicOffersAclAndQueryTests {

    static <T> T proxy(
            Class<T> type
    ) {
        return mock(type, withSettings().mockMaker(MockMakers.PROXY));
    }

    final ProductQueryService products = proxy(ProductQueryService.class);
    final CatalogContextFacade facade = new CatalogContextFacade(products);
    final InventoryContextFacade inventoryFacade = new InventoryContextFacade(
            proxy(LotCommandService.class), proxy(LotQueryService.class), facade);
    final ExternalCatalogService catalog = new ExternalCatalogService(facade, inventoryFacade);
    final Product product = new Product(1L, 2L, "Milk", null, BigDecimal.TEN, 10);

    @Test
    void expandedSnapshotPreservesRealActiveStateAndLegacyConstructor() {
        ReflectionTestUtils.setField(product, "id", 3L);
        when(products.handle(new GetProductsByBusinessIdQuery(1L))).thenReturn(List.of(product));
        assertTrue(facade.getProductsByBusinessId(1L).getFirst().active());
        product.deactivate();
        var snapshot = facade.getProductsByBusinessId(1L).getFirst();
        assertFalse(snapshot.active());
        assertEquals("Milk", snapshot.name());
        assertEquals(10, snapshot.minimumStock());
        assertEquals(10, new ProductInventorySnapshot(3L, 1L, 10).minimumStock());
    }

    @Test
    void offerLookupRejectsMissingInactiveAndForeignProducts() {
        when(products.handle(new GetProductByIdQuery(3L))).thenReturn(Optional.of(product));
        assertDoesNotThrow(() -> catalog.validateActiveProduct(1L, 3L));
        assertThrows(IllegalArgumentException.class, () -> catalog.validateActiveProduct(2L, 3L));
        product.deactivate();
        assertThrows(IllegalArgumentException.class, () -> catalog.validateActiveProduct(1L, 3L));
        assertThrows(IllegalArgumentException.class, () -> catalog.validateActiveProduct(1L, 99L));
    }

    @Test
    void recommendationQueryForwardsEveryFilterIncludingNulls() {
        var repository = proxy(RecommendationRepository.class);
        var service = new RecommendationQueryServiceImpl(repository, com.bodego.platform.shared.application.security.TestBusinessAccess.admin());
        when(repository.findByFilters(1L, 2L, RecommendationType.EXPIRING, false)).thenReturn(List.of());
        assertTrue(service.handle(new GetRecommendationsQuery(1L, 2L, RecommendationType.EXPIRING, false)).isEmpty());
        verify(repository).findByFilters(1L, 2L, RecommendationType.EXPIRING, false);
        service.handle(new GetRecommendationsQuery(null, null, null, null));
        verify(repository).findByFilters(1L, null, null, null);
        assertTrue(service.handle(new GetRecommendationByIdQuery(99L)).isEmpty());
    }

    @Test
    void offerQueryForwardsAllFiltersAndMissingId() {
        var repository = proxy(OfferRepository.class);
        var service = new OfferQueryServiceImpl(repository, com.bodego.platform.shared.application.security.TestBusinessAccess.admin());
        service.handle(new GetOffersQuery(1L, 2L, false));
        verify(repository).findByFilters(1L, 2L, false);
        assertTrue(service.handle(new GetOfferByIdQuery(99L)).isEmpty());
    }
}
