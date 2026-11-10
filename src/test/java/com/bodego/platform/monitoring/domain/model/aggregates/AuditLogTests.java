package com.bodego.platform.monitoring.domain.model.aggregates;

import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogByIdQuery;
import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogsQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogTests {

    @Test
    void createsHistoryWithServerTimeAndNoInventedUser() {
        var before = Instant.now();
        var log = new AuditLog(1L, null, " CREATED ", " Waste ", 2L, " Recorded waste ");
        assertNull(log.getUserId());
        assertEquals("CREATED", log.getAction());
        assertEquals("Waste", log.getEntityType());
        assertEquals("Recorded waste", log.getDescription());
        assertFalse(log.getOccurredAt().isBefore(before));
        assertFalse(log.getOccurredAt().isAfter(Instant.now()));
        assertEquals(1L, log.getBusinessId());
        assertEquals(2L, log.getEntityId());
    }

    @Test
    void preservesProvidedUserAndAcceptsMaximumTextLengths() {
        var log = new AuditLog(1L, 7L, "a".repeat(100), "e".repeat(100), 2L, "d".repeat(1000));
        assertEquals(7L, log.getUserId());
        assertEquals(100, log.getAction().length());
        assertEquals(1000, log.getDescription().length());
    }

    @Test
    void rejectsInvalidReferences() {
        for (Long invalid : new Long[]{null, 0L, -1L}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AuditLog(invalid, null, "CREATED", "Waste", 2L, "Recorded"));
            assertThrows(IllegalArgumentException.class,
                    () -> new AuditLog(1L, null, "CREATED", "Waste", invalid, "Recorded"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new AuditLog(1L, 0L, "CREATED", "Waste", 2L, "Recorded"));
    }

    @Test
    void rejectsMissingBlankAndOversizedText() {
        for (String invalid : new String[]{null, " ", "x".repeat(101)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AuditLog(1L, null, invalid, "Waste", 2L, "Recorded"));
            assertThrows(IllegalArgumentException.class,
                    () -> new AuditLog(1L, null, "CREATED", invalid, 2L, "Recorded"));
        }
        for (String invalid : new String[]{null, " ", "x".repeat(1001)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AuditLog(1L, null, "CREATED", "Waste", 2L, invalid));
        }
    }

    @Test
    void exposesNoBusinessMutationMethods() {
        assertTrue(Arrays.stream(AuditLog.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set")));
        assertNotNull(AuditLog.class.getAnnotation(org.hibernate.annotations.Immutable.class));
    }

    @Test
    void validatesQueriesAndTrimsExactEntityType() {
        assertEquals("Waste", new GetAuditLogsQuery(1L, " Waste ", 2L).entityType());
        assertDoesNotThrow(() -> new GetAuditLogsQuery(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new GetAuditLogByIdQuery(null));
        assertThrows(IllegalArgumentException.class, () -> new GetAuditLogByIdQuery(0L));
        assertThrows(IllegalArgumentException.class, () -> new GetAuditLogsQuery(-1L, null, null));
        assertThrows(IllegalArgumentException.class, () -> new GetAuditLogsQuery(null, null, 0L));
        assertThrows(IllegalArgumentException.class, () -> new GetAuditLogsQuery(null, " ", null));
        assertThrows(IllegalArgumentException.class, () -> new GetAuditLogsQuery(null, "x".repeat(101), null));
    }
}
