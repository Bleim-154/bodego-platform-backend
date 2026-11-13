package com.bodego.platform.iam.interfaces.rest;

import com.bodego.platform.iam.domain.exceptions.InvalidIamDataException;
import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.commands.*;
import com.bodego.platform.iam.domain.model.queries.*;
import com.bodego.platform.iam.domain.services.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/v1/iam/users")
public class UsersController {
    public record CreateEmployeeResource(@NotBlank @Size(max=150) String fullName,
            @NotBlank @Size(max=254) String email,@NotBlank @Size(min=8,max=72) String password) {
        @JsonAnySetter public void rejectUnknown(String field,Object value) { throw new InvalidIamDataException(); }
        @Override public String toString() { return "CreateEmployeeResource[REDACTED]"; }
    }
    public record ChangeStatusResource(@NotNull Boolean active) {
        @JsonAnySetter public void rejectUnknown(String field,Object value) { throw new InvalidIamDataException(); }
    }
    public record UserResource(Long id,Long businessId,String fullName,String email,String role,boolean active) {
        static UserResource from(User user) {
            return new UserResource(user.getId(),user.getBusinessId(),user.getFullName(),user.getEmail(),
                    user.getRole().name(),user.isEnabled());
        }
    }
    public record UsersPageResource(List<UserResource> items,int page,int size,boolean hasNext) {}
    private final EmployeeAdministrationCommandService commands;
    private final EmployeeAdministrationQueryService queries;
    public UsersController(EmployeeAdministrationCommandService commands,EmployeeAdministrationQueryService queries) {
        this.commands=commands; this.queries=queries;
    }
    @PostMapping
    public ResponseEntity<UserResource> create(@Valid @RequestBody CreateEmployeeResource resource) {
        char[] password=resource.password().toCharArray();
        try(var command=new RegisterEmployeeCommand(resource.fullName(),resource.email(),password)) {
            var result=UserResource.from(commands.handle(command));
            return ResponseEntity.created(URI.create("/api/v1/iam/users/"+result.id()))
                    .cacheControl(CacheControl.noStore()).body(result);
        } finally {Arrays.fill(password,'\0');}
    }
    @GetMapping
    public ResponseEntity<UsersPageResource> list(@RequestParam(defaultValue="0") int page,
                                                  @RequestParam(defaultValue="20") int size) {
        var result=queries.handle(new ListBusinessUsersQuery(page,size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new UsersPageResource(
                result.users().stream().map(UserResource::from).toList(),result.page(),result.size(),result.hasNext()));
    }
    @GetMapping("/{userId}")
    public ResponseEntity<UserResource> get(@PathVariable Long userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(UserResource.from(queries.handle(new GetManagedUserByIdQuery(userId))));
    }
    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserResource> status(@PathVariable Long userId,@Valid @RequestBody ChangeStatusResource resource) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(UserResource.from(commands.handle(new ChangeEmployeeStatusCommand(userId,resource.active()))));
    }
}
