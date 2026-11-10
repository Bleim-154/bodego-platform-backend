package com.bodego.platform.strategicoffers.application.internal.commandservices;

import com.bodego.platform.monitoring.domain.model.commands.RecordAuditLogCommand;
import com.bodego.platform.monitoring.domain.services.AuditLogCommandService;
import com.bodego.platform.monitoring.interfaces.acl.MonitoringContextFacade;
import com.bodego.platform.strategicoffers.application.internal.outboundservices.acl.ExternalCatalogService;
import com.bodego.platform.strategicoffers.application.internal.outboundservices.acl.ExternalMonitoringService;
import com.bodego.platform.strategicoffers.domain.model.aggregates.Offer;
import com.bodego.platform.strategicoffers.domain.model.commands.*;
import com.bodego.platform.strategicoffers.infrastructure.persistence.jpa.repositories.OfferRepository;
import org.junit.jupiter.api.*;
import org.mockito.MockMakers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfferCommandServiceTests {

    final AuditLogCommandService audit = mock(AuditLogCommandService.class, withSettings().mockMaker(MockMakers.PROXY));
    final ExternalMonitoringService monitoring = new ExternalMonitoringService(new MonitoringContextFacade(audit));
    final OfferRepository repository = mock(OfferRepository.class, withSettings().mockMaker(MockMakers.PROXY));
    boolean productActive = true;
    final ExternalCatalogService catalog = new ExternalCatalogService(null) {
        public void validateActiveProduct(
                Long businessId,
                Long productId
        ) {
            if (!productActive || businessId != 1L || productId != 2L) {
                throw new IllegalArgumentException("Invalid product");
            }
        }
    };
    final OfferCommandServiceImpl service = new OfferCommandServiceImpl(repository, catalog, monitoring);
    final LocalDate today = LocalDate.of(2026, 11, 10);
    final Offer offer = new Offer(1L, 2L, "Promo", new BigDecimal("10.25"), today, today.plusDays(5));

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(offer, "id", 1L);
        when(repository.findWithLockById(1L)).thenReturn(Optional.of(offer));
        when(repository.save(any(Offer.class))).thenAnswer(i -> {
            Offer saved = i.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
    }

    @Test
    void createsActiveOfferForValidProduct() {
        assertEquals(1L, service.handle(new CreateOfferCommand(1L, 2L, "Promo", new BigDecimal("0.001"), today, today)));
        verify(audit).handle(argThat(c -> c.action().equals("OFFER_CREATED") && c.userId() == null && c.entityId().equals(1L)));
        verify(repository).save(argThat(o -> o.isActive() && o.getDiscountPercentage().compareTo(new BigDecimal("0.001")) == 0));
    }

    @Test
    void rejectsInactiveMissingOrOtherBusinessProduct() {
        productActive = false;
        assertThrows(IllegalArgumentException.class, () -> service.handle(new CreateOfferCommand(1L, 2L, "Promo", BigDecimal.TEN, today, today)));
        productActive = true;
        assertThrows(IllegalArgumentException.class, () -> service.handle(new CreateOfferCommand(2L, 2L, "Promo", BigDecimal.TEN, today, today)));
        verify(repository, never()).save(any());
    }

    @Test
    void activationRequiresActiveProductAndTransitionsAreIdempotent() {
        service.handle(new DeactivateOfferCommand(1L));
        service.handle(new DeactivateOfferCommand(1L));
        verify(repository, times(1)).save(offer);
        productActive = false;
        assertThrows(IllegalArgumentException.class, () -> service.handle(new ActivateOfferCommand(1L)));
        assertFalse(offer.isActive());
        productActive = true;
        service.handle(new ActivateOfferCommand(1L));
        service.handle(new ActivateOfferCommand(1L));
        verify(repository, times(2)).save(offer);
        assertTrue(offer.isActive());
        verify(audit, times(1)).handle(argThat(c -> c.action().equals("OFFER_ACTIVATED") && c.userId() == null));
        verify(audit, times(1)).handle(argThat(c -> c.action().equals("OFFER_DEACTIVATED") && c.userId() == null));
    }

    @Test
    void updatesKeepReferencesAndActivationState() {
        offer.deactivate();
        service.handle(new UpdateOfferCommand(1L, "New", new BigDecimal("99.999"), today, today));
        verify(audit).handle(argThat(c -> c.action().equals("OFFER_UPDATED") && c.entityId().equals(1L)));
        assertEquals("New", offer.getTitle());
        assertEquals(1L, offer.getBusinessId());
        assertEquals(2L, offer.getProductId());
        assertFalse(offer.isActive());
    }

    @Test
    void domainRejectsInvalidDiscountTitleAndDateRange() {
        for (var discount : new BigDecimal[]{BigDecimal.ZERO, BigDecimal.valueOf(100), BigDecimal.valueOf(-1)}) {
            assertThrows(IllegalArgumentException.class, () -> new Offer(1L, 2L, "Promo", discount, today, today));
        }
        assertThrows(IllegalArgumentException.class, () -> new Offer(1L, 2L, " ", BigDecimal.TEN, today, today));
        assertThrows(IllegalArgumentException.class, () -> new Offer(1L, 2L, "Promo", BigDecimal.TEN, today, today.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> new Offer(1L, 2L, "Promo", null, today, today));
    }

    @Test
    void effectiveValidityIncludesDatesAndNeverAutoDeactivates() {
        assertTrue(offer.isEffectiveOn(today));
        assertTrue(offer.isEffectiveOn(today.plusDays(5)));
        assertFalse(offer.isEffectiveOn(today.minusDays(1)));
        assertFalse(offer.isEffectiveOn(today.plusDays(6)));
        assertTrue(offer.isActive());
        offer.deactivate();
        assertFalse(offer.isEffectiveOn(today));
    }

    @Test
    void repeatedUpdateDoesNotSaveOrAuditAgain() {
        var command = new UpdateOfferCommand(1L, "New", BigDecimal.TEN, today, today);
        service.handle(command);
        service.handle(new UpdateOfferCommand(1L, " New ", new BigDecimal("10.00"), today, today));
        verify(repository, times(1)).save(offer);
        verify(audit, times(1)).handle(any(RecordAuditLogCommand.class));
    }

    @Test
    void missingOfferReturnsEmptyForAllCommands() {
        assertTrue(service.handle(new UpdateOfferCommand(99L, "New", BigDecimal.TEN, today, today)).isEmpty());
        assertTrue(service.handle(new ActivateOfferCommand(99L)).isEmpty());
        assertTrue(service.handle(new DeactivateOfferCommand(99L)).isEmpty());
    }
}
