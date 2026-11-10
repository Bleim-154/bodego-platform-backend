package com.bodego.platform.monitoring.interfaces.rest;

import com.bodego.platform.monitoring.domain.model.aggregates.AuditLog;
import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogByIdQuery;
import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogsQuery;
import com.bodego.platform.monitoring.domain.services.AuditLogQueryService;
import com.bodego.platform.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuditLogsControllerTests {

    private final AuditLogQueryService queries = mock(
            AuditLogQueryService.class, withSettings().mockMaker(MockMakers.PROXY));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuditLogsController(queries))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void combinedFiltersBindAndSerializeNullableUser() throws Exception {
        var log = new AuditLog(1L, null, "CREATED", "Waste", 2L, "Recorded");
        ReflectionTestUtils.setField(log, "id", 9L);
        when(queries.handle(new GetAuditLogsQuery(1L, "Waste", 2L))).thenReturn(List.of(log));
        var result = mvc.perform(get("/api/v1/audit-logs")
                        .param("businessId", "1").param("entityType", " Waste ").param("entityId", "2"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("\"action\":\"CREATED\""));
        assertTrue(result.getResponse().getContentAsString().contains("\"userId\":null"));
        verify(queries).handle(new GetAuditLogsQuery(1L, "Waste", 2L));
    }

    @Test
    void missingIdReturns404AndExistingIdReturnsResource() throws Exception {
        when(queries.handle(new GetAuditLogByIdQuery(9L))).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/audit-logs/9")).andExpect(status().isNotFound());
        when(queries.handle(new GetAuditLogByIdQuery(9L))).thenReturn(Optional.of(
                new AuditLog(1L, null, "CREATED", "Waste", 2L, "Recorded")));
        mvc.perform(get("/api/v1/audit-logs/9")).andExpect(status().isOk());
    }

    @Test
    void invalidIdsAndBlankTypeReturn400ThroughExistingHandler() throws Exception {
        mvc.perform(get("/api/v1/audit-logs/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit-logs").param("businessId", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit-logs").param("entityType", " ")).andExpect(status().isBadRequest());
        verifyNoInteractions(queries);
    }

    @Test
    void allPublicWriteMethodsAreRejected() throws Exception {
        for (var method : new HttpMethod[]{HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
            for (var path : new String[]{"/api/v1/audit-logs", "/api/v1/audit-logs/9"}) {
                mvc.perform(request(method, path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isMethodNotAllowed());
            }
        }
        verifyNoInteractions(queries);
    }

    @Test
    void noFiltersReturnsEmptyList() throws Exception {
        when(queries.handle(new GetAuditLogsQuery(null, null, null))).thenReturn(List.of());
        mvc.perform(get("/api/v1/audit-logs")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
