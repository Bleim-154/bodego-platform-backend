package com.bodego.platform.iam.infrastructure;

import com.bodego.platform.iam.domain.services.*;
import com.bodego.platform.iam.interfaces.rest.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserManagementValidationTests {
    @Test void unsupportedContentTypeIsAClientErrorWithoutEchoingBody() throws Exception {
        var commands=mock(EmployeeAdministrationCommandService.class);
        var queries=mock(EmployeeAdministrationQueryService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new UsersController(commands,queries))
                .setControllerAdvice(new UserManagementExceptionHandler()).build();
        mvc.perform(post("/api/v1/iam/users").contentType("text/plain").content("do-not-echo-input"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().json("{\"message\":\"Unsupported media type\"}"));
        verifyNoInteractions(commands,queries);
    }
    @Test void malformedPaginationIsRejectedBeforeApplicationInvocation() throws Exception {
        var commands=mock(EmployeeAdministrationCommandService.class);
        var queries=mock(EmployeeAdministrationQueryService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new UsersController(commands,queries))
                .setControllerAdvice(new UserManagementExceptionHandler()).build();
        mvc.perform(get("/api/v1/iam/users").param("page","invalid-input"))
                .andExpect(status().isBadRequest()).andExpect(content().json("{\"message\":\"Invalid request\"}"));
        verifyNoInteractions(commands,queries);
    }
}
