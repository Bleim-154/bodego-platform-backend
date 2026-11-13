package com.bodego.platform.iam.infrastructure;

import com.bodego.platform.iam.application.internal.commandservices.AuthenticationCommandServiceImpl;
import com.bodego.platform.iam.application.internal.outboundservices.hashing.PasswordHashingService;
import com.bodego.platform.iam.application.internal.outboundservices.tokens.AccessTokenService;
import com.bodego.platform.iam.domain.exceptions.InvalidCredentialsException;
import com.bodego.platform.iam.domain.model.commands.SignInCommand;
import com.bodego.platform.iam.domain.model.valueobjects.*;
import com.bodego.platform.iam.domain.services.AuthenticationCommandService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.*;
import com.bodego.platform.iam.interfaces.rest.*;
import com.bodego.platform.shared.application.security.CurrentIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationUnitTests {
    @Test void commandsDefensivelyCopyRedactAndErasePasswords() {
        char[] input="fixture-password".toCharArray();
        var command=new SignInCommand("fixture@example.invalid",input);
        input[0]='X'; assertEquals('f',command.password()[0]);
        assertFalse(command.toString().contains("fixture-password"));
        command.close(); assertEquals('\0',command.password()[0]);
    }
    @Test void nonexistentEmailStillPerformsPasswordVerificationAndConsumesCommand() {
        var users=mock(UserRepository.class); var businesses=mock(BusinessRepository.class);
        var hashing=mock(PasswordHashingService.class);
        var hash=new PasswordHash("{bcrypt}"+new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("fixture-password"));
        when(hashing.hash(any())).thenReturn(hash);
        when(users.findByEmail(new EmailAddress("fixture@example.invalid"))).thenReturn(Optional.empty());
        var service=new AuthenticationCommandServiceImpl(users,businesses,hashing);
        var command=new SignInCommand(" FIXTURE@example.invalid ","fixture-password".toCharArray());
        assertThrows(InvalidCredentialsException.class,() -> service.handle(command));
        verify(hashing).matches(any(),eq(hash)); verifyNoInteractions(businesses);
        assertEquals('\0',command.password()[0]);
    }
    @Test void unexpectedHttpErrorsNeverExposeInternalMessages() throws Exception {
        var authentication=mock(AuthenticationCommandService.class);
        when(authentication.handle(any())).thenThrow(new IllegalStateException("sensitive-internal-detail"));
        var controller=new AuthenticationController(authentication,mock(AccessTokenService.class),mock(CurrentIdentity.class),
                new com.bodego.platform.iam.infrastructure.security.login.LoginRateLimiter(
                        new com.bodego.platform.iam.infrastructure.security.login.LoginRateLimitPolicy(java.time.Duration.ofMinutes(1),30,5,100,4096,1),System::nanoTime));
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new AuthenticationExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"fixture@example.invalid\",\"password\":\"fixture-password\"}"))
                .andExpect(status().isInternalServerError()).andExpect(content().json("{\"message\":\"Internal server error\"}"));
        // Unexpected failures must release the sole in-flight slot; a second login reaches the service.
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"fixture@example.invalid\",\"password\":\"fixture-password\"}"))
                .andExpect(status().isInternalServerError()).andExpect(content().json("{\"message\":\"Internal server error\"}"));
        verify(authentication,times(2)).handle(any());
    }
}
