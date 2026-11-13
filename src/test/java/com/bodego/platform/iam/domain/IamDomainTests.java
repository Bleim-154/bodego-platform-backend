package com.bodego.platform.iam.domain;

import com.bodego.platform.iam.domain.exceptions.*;
import com.bodego.platform.iam.domain.model.aggregates.Business;
import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.commands.CreateEmployeeCommand;
import com.bodego.platform.iam.domain.model.valueobjects.*;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class IamDomainTests {
    @Test
    void employeeStatusChangesNeverModifyAdminsOrBusinessMembership() {
        var hash=new PasswordHash("{bcrypt}$2b$12$"+"A".repeat(53));
        var employee=new User(1L,"Fixture",new EmailAddress("fixture@example.invalid"),hash,UserRole.EMPLOYEE);
        employee.changeEmployeeStatus(false);assertFalse(employee.isEnabled());
        employee.changeEmployeeStatus(true);assertTrue(employee.isEnabled());
        assertEquals(1L,employee.getBusinessId());assertEquals(UserRole.EMPLOYEE,employee.getRole());
        var admin=new User(1L,"Fixture",new EmailAddress("admin@example.invalid"),hash,UserRole.ADMIN);
        assertThrows(com.bodego.platform.iam.domain.exceptions.EmployeeOnlyOperationException.class,() -> admin.changeEmployeeStatus(false));
        assertTrue(admin.isEnabled());
    }
    private static final PasswordHash HASH = new PasswordHash("{bcrypt}$2b$12$" + "A".repeat(53));

    @Test
    void businessValidatesAllFieldsBeforeUpdatingAnything() {
        var business = new Business(" Shop ", " Address ", " +51 123 ");
        assertEquals("Shop", business.getName());
        assertEquals("+51 123", business.getPhone());
        assertThrows(InvalidIamDataException.class, () -> business.updateInformation("New", " ", "123"));
        assertEquals("Shop", business.getName());
        assertEquals("Address", business.getAddress());
        assertThrows(InvalidIamDataException.class, () -> new Business("N".repeat(101), "Address", "123"));
        assertThrows(InvalidIamDataException.class, () -> new Business("Shop", "A".repeat(256), "123"));
        assertThrows(InvalidIamDataException.class, () -> new Business("Shop", "Address", "1".repeat(33)));
        business.deactivate();
        assertFalse(business.getActive());
    }

    @Test
    void emailNormalizesAndRejectsInvalidValues() {
        assertEquals(new EmailAddress("fixture@example.invalid"), new EmailAddress(" FIXTURE@EXAMPLE.INVALID "));
        for (String email : new String[]{"", "a b@example.invalid", "a..b@example.invalid", "a@", "@example.invalid", "a@-example.invalid", "a@" + "a".repeat(64) + ".invalid"}) {
            assertThrows(InvalidEmailException.class, () -> new EmailAddress(email));
        }
        assertThrows(InvalidEmailException.class, () -> new EmailAddress(null));
    }

    @Test
    void emailUsesLocaleIndependentNormalization() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("fixture@example.invalid", new EmailAddress("FIXTURE@EXAMPLE.INVALID").value());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void userRequiresPositiveBusinessRoleAndHashAndHasNoBusinessMutatorOrCredentialGetter() throws Exception {
        assertThrows(InvalidIamDataException.class, () -> new User(0L, "Name", new EmailAddress("a@example.invalid"), HASH, UserRole.ADMIN));
        assertThrows(InvalidIamDataException.class, () -> new User(1L, " ", new EmailAddress("a@example.invalid"), HASH, UserRole.ADMIN));
        assertThrows(InvalidIamDataException.class, () -> new User(1L, "Name", new EmailAddress("a@example.invalid"), HASH, null));
        assertThrows(InvalidPasswordException.class, () -> new PasswordHash("plaintext"));
        assertThrows(InvalidPasswordException.class, () -> new PasswordHash("{noop}plaintext"));
        var user = new User(1L, " Name ", new EmailAddress(" A@EXAMPLE.INVALID "), HASH, UserRole.EMPLOYEE);
        assertEquals(1L, user.getBusinessId());
        assertEquals("Name", user.getFullName());
        assertEquals("a@example.invalid", user.getEmail());
        assertTrue(user.isEnabled());
        user.deactivate();
        assertFalse(user.isEnabled());
        assertTrue(Arrays.stream(User.class.getMethods()).noneMatch(m -> m.getName().equals("setBusinessId")));
        assertTrue(Arrays.stream(Introspector.getBeanInfo(User.class).getPropertyDescriptors())
                .noneMatch(p -> p.getName().toLowerCase(Locale.ROOT).contains("password")));
        assertFalse(HASH.toString().contains(HASH.value()));
    }

    @Test
    void passwordCommandsDefensivelyCopyRedactAndEraseTheirOwnSecret() {
        char[] input = "Fixture-password-2026".toCharArray();
        var command = new CreateEmployeeCommand(1L, "Name", "a@example.invalid", input);
        input[0] = 'X';
        assertEquals('F', command.password()[0]);
        char[] exported = command.password();
        exported[0] = 'Y';
        assertEquals('F', command.password()[0]);
        assertFalse(command.toString().contains("Fixture-password"));
        command.close();
        assertArrayEquals(new char[input.length], command.password());
    }
}
