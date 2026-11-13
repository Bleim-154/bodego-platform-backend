package com.bodego.platform.inventory.application.internal.commandservices;

import com.bodego.platform.catalog.interfaces.acl.CatalogContextFacade;
import com.bodego.platform.catalog.domain.model.aggregates.Product;
import com.bodego.platform.catalog.domain.model.queries.GetProductByIdQuery;
import com.bodego.platform.catalog.domain.services.ProductQueryService;
import com.bodego.platform.inventory.domain.model.aggregates.Lot;
import com.bodego.platform.inventory.domain.model.commands.CreateLotCommand;
import com.bodego.platform.inventory.infrastructure.persistence.jpa.repositories.LotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LotCommandServiceTests {

    private final LotRepository lots = mock(LotRepository.class, withSettings().mockMaker(MockMakers.PROXY));
    private final ProductQueryService products = mock(ProductQueryService.class, withSettings().mockMaker(MockMakers.PROXY));
    private final CatalogContextFacade catalog = new CatalogContextFacade(products);
    private final jakarta.persistence.EntityManager entityManager = mock(jakarta.persistence.EntityManager.class, withSettings().mockMaker(MockMakers.PROXY));
    private final LotCommandServiceImpl service = new LotCommandServiceImpl(lots, catalog, com.bodego.platform.shared.application.security.TestBusinessAccess.admin(), entityManager);

    @Test
    void createsZeroQuantityLotAfterCatalogConfirmsActiveProductOwnership() {
        var product = new Product(1L, 3L, "Product", null, BigDecimal.ONE, 0);
        when(products.handle(new GetProductByIdQuery(2L))).thenReturn(Optional.of(product));
        when(lots.save(any(Lot.class))).thenAnswer(invocation -> {
            var lot = invocation.getArgument(0, Lot.class);
            ReflectionTestUtils.setField(lot, "id", 5L);
            return lot;
        });

        var id = service.handle(new CreateLotCommand(1L, 2L, "Batch", 0, null));

        assertEquals(5L, id);
        var saved = ArgumentCaptor.forClass(Lot.class);
        verify(lots).save(saved.capture());
        assertEquals(0, saved.getValue().getQuantity());
        verify(products).handle(new GetProductByIdQuery(2L));
    }

    @Test
    void doesNotCreateLotForInactiveProductOrNonzeroInitialQuantity() {
        var product = new Product(1L, 3L, "Product", null, BigDecimal.ONE, 0);
        product.deactivate();
        when(products.handle(new GetProductByIdQuery(2L))).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class,
                () -> service.handle(new CreateLotCommand(1L, 2L, "Batch", 0, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.handle(new CreateLotCommand(1L, 2L, "Batch", 1, null)));
        verify(lots, never()).save(any(Lot.class));
        verify(products, times(1)).handle(new GetProductByIdQuery(2L));
    }
}
