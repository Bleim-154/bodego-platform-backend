package com.bodego.platform.strategicoffers.interfaces.rest;

import com.bodego.platform.shared.interfaces.rest.GlobalExceptionHandler;
import com.bodego.platform.strategicoffers.domain.model.aggregates.*;
import com.bodego.platform.strategicoffers.domain.model.commands.*;
import com.bodego.platform.strategicoffers.domain.model.queries.*;
import com.bodego.platform.strategicoffers.domain.model.valueobjects.RecommendationType;
import com.bodego.platform.strategicoffers.domain.services.*;
import org.junit.jupiter.api.*;
import org.mockito.MockMakers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StrategicOffersRestTests {

    static <T> T proxy(
            Class<T> type
    ) {
        return mock(type, withSettings().mockMaker(MockMakers.PROXY));
    }

    final OfferCommandService offers = proxy(OfferCommandService.class);
    final OfferQueryService offerQueries = proxy(OfferQueryService.class);
    final RecommendationCommandService recommendations = proxy(RecommendationCommandService.class);
    final RecommendationQueryService recommendationQueries = proxy(RecommendationQueryService.class);
    MockMvc mvc;
    final String createBody = """
            {"businessId":1,"productId":2,"title":"Promo","discountPercentage":10.25,
             "startDate":"2026-11-10","endDate":"2026-11-10"}
            """;
    final String updateBody = """
            {"title":"Promo","discountPercentage":10.25,"startDate":"2026-11-10","endDate":"2026-11-10"}
            """;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new OffersController(offers, offerQueries),
                        new RecommendationsController(recommendations, recommendationQueries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void generationReturnsOnlyNewAndValidatesBody() throws Exception {
        when(recommendations.handle(new GenerateRecommendationsCommand(1L))).thenReturn(List.of());
        mvc.perform(post("/api/v1/recommendations/generate").contentType(MediaType.APPLICATION_JSON).content("{\"businessId\":1}"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/v1/recommendations/generate").contentType(MediaType.APPLICATION_JSON).content("{\"businessId\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verify(recommendations, times(1)).handle(any(GenerateRecommendationsCommand.class));
    }

    @Test
    void recommendationFiltersBindWithAndAndResourceContainsHistory() throws Exception {
        var entity = new Recommendation(1L, 2L, null, RecommendationType.RESTOCK, "Restock", Instant.parse("2026-11-10T00:00:00Z"));
        when(recommendationQueries.handle(new GetRecommendationsQuery(1L, 2L, RecommendationType.RESTOCK, false))).thenReturn(List.of(entity));
        mvc.perform(get("/api/v1/recommendations?businessId=1&productId=2&type=RESTOCK&dismissed=false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("RESTOCK"))
                .andExpect(jsonPath("$[0].dismissed").value(false));
        mvc.perform(get("/api/v1/recommendations?type=UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/recommendations?businessId=-1")).andExpect(status().isBadRequest());
    }

    @Test
    void allMissingEntityRoutesReturn404() throws Exception {
        mvc.perform(get("/api/v1/recommendations/99")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/recommendations/99/dismiss")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/offers/99")).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/offers/99").contentType(MediaType.APPLICATION_JSON).content(updateBody)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/offers/99/activate")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/offers/99/deactivate")).andExpect(status().isNotFound());
    }

    @Test
    void offerCreationReturns201AndDiscountValidationUsesDecimalBounds() throws Exception {
        var entity = new Offer(1L, 2L, "Promo", new BigDecimal("10.25"), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));
        when(offers.handle(any(CreateOfferCommand.class))).thenReturn(7L);
        when(offerQueries.handle(new GetOfferByIdQuery(7L))).thenReturn(Optional.of(entity));
        mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.discountPercentage").value(10.25))
                .andExpect(jsonPath("$.active").value(true));
        for (var value : List.of("0", "100", "-1")) {
            mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(createBody.replace("10.25", value)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(createBody.replace("Promo", " ")))
                .andExpect(status().isBadRequest());
        verify(offers, times(1)).handle(any(CreateOfferCommand.class));
    }

    @Test
    void offerFiltersBindTogetherAndInvalidIdsFail() throws Exception {
        when(offerQueries.handle(new GetOffersQuery(1L, 2L, false))).thenReturn(List.of());
        mvc.perform(get("/api/v1/offers?businessId=1&productId=2&active=false"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(offerQueries).handle(new GetOffersQuery(1L, 2L, false));
        mvc.perform(get("/api/v1/offers/0")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/recommendations/0/dismiss")).andExpect(status().isBadRequest());
    }

    @Test
    void domainErrorsUseExistingMessageResponseAndUnsupportedMethodsFail() throws Exception {
        when(offers.handle(any(CreateOfferCommand.class))).thenThrow(new IllegalArgumentException("Product must be active"));
        mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Product must be active"));
        mvc.perform(delete("/api/v1/offers/1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/v1/recommendations")).andExpect(status().isMethodNotAllowed());
    }
}
