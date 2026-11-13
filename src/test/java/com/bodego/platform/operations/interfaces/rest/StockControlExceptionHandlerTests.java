package com.bodego.platform.operations.interfaces.rest;

import com.bodego.platform.operations.domain.exceptions.*;
import com.bodego.platform.operations.domain.model.commands.ApplyStockAdjustmentCommand;
import com.bodego.platform.operations.domain.services.*;
import com.bodego.platform.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pure HTTP contract tests: no Spring Boot application, datasource, tokens or network. */
class StockControlExceptionHandlerTests {
    private final StockAdjustmentCommandService commands = mock(StockAdjustmentCommandService.class,
            withSettings().mockMaker(MockMakers.PROXY));
    private MockMvc mvc;
    private static final String BODY = "{\"businessId\":1,\"physicalCountId\":2,\"reason\":\"Fixture\"}";

    @BeforeEach void setup() {
        var queries = mock(StockAdjustmentQueryService.class, withSettings().mockMaker(MockMakers.PROXY));
        var requestCommands = mock(CorrectionRequestCommandService.class, withSettings().mockMaker(MockMakers.PROXY));
        var requestQueries = mock(CorrectionRequestQueryService.class, withSettings().mockMaker(MockMakers.PROXY));
        mvc = MockMvcBuilders.standaloneSetup(new StockAdjustmentsController(commands, queries),
                        new CorrectionRequestsController(requestCommands, requestQueries), new UnrelatedController())
                .setControllerAdvice(new StockControlExceptionHandler(), new GlobalExceptionHandler()).build();
    }

    @Test void onlySpecificBusinessConflictsAre409AndDoNotExposeTheirCause() throws Exception {
        for (var conflict : List.of(new PhysicalCountWithoutDifferenceException(),
                new PhysicalCountAlreadyAdjustedException(), new PendingCorrectionRequestAlreadyExistsException(),
                new CorrectionRequestAlreadyReviewedException(),
                new PhysicalCountStockChangedException(new IllegalStateException("PRIVATE_INTERNAL_FIXTURE")))) {
            when(commands.handle(any(ApplyStockAdjustmentCommand.class))).thenThrow(conflict);
            mvc.perform(post("/api/v1/stock-adjustments").contentType("application/json").content(BODY))
                    .andExpect(status().isConflict())
                    .andExpect(content().string("{\"message\":\"" + conflict.getMessage() + "\"}"));
            reset(commands);
        }
    }

    @Test void unexpectedIllegalStateAndPersistenceErrorsStaySafe500RatherThan409() throws Exception {
        for (var failure : List.of(new IllegalStateException("PRIVATE_INTERNAL_FIXTURE"),
                new DataIntegrityViolationException("PRIVATE_SQL_FIXTURE"))) {
            when(commands.handle(any(ApplyStockAdjustmentCommand.class))).thenThrow(failure);
            mvc.perform(post("/api/v1/stock-adjustments").contentType("application/json").content(BODY))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string("{\"message\":\"Internal server error\"}"));
            reset(commands);
        }
    }

    @Test void invalidDomainArgumentsAre400WithSafeMessages() throws Exception {
        when(commands.handle(any(ApplyStockAdjustmentCommand.class)))
                .thenThrow(new IllegalArgumentException("PRIVATE_INPUT_FIXTURE"));
        mvc.perform(post("/api/v1/stock-adjustments").contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("{\"message\":\"Invalid request\"}"));
    }

    @Test void malformedJsonValidationAndFiltersKeep400WithoutInvokingACommand() throws Exception {
        for (String body : List.of("{", "{}", BODY.replace("Fixture", " "), BODY.replace("Fixture", "a".repeat(501)))) {
            mvc.perform(post("/api/v1/stock-adjustments").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request"));
        }
        mvc.perform(get("/api/v1/stock-adjustments/not-a-number"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/correction-requests?businessId=1&physicalCountId=2"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(commands);
    }

    @Test void frameworkStatusesAndHeadersAreNotConvertedIntoUnexpected500() throws Exception {
        mvc.perform(put("/api/v1/stock-adjustments").contentType("application/json").content(BODY))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"));
        mvc.perform(post("/api/v1/stock-adjustments").contentType("text/plain").content("Fixture"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(commands);
    }

    @Test void adviceDoesNotTranslateUnexpectedErrorsOutsideStockControl() {
        var failure = assertThrows(jakarta.servlet.ServletException.class,
                () -> mvc.perform(get("/unrelated-fixture")));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @RestController
    static class UnrelatedController {
        @GetMapping("/unrelated-fixture") String fail() { throw new IllegalStateException("Fixture"); }
    }
}
