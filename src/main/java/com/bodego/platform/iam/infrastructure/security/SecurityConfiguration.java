package com.bodego.platform.iam.infrastructure.security;

import com.bodego.platform.iam.interfaces.acl.IamContextFacade;
import com.bodego.platform.shared.application.security.AuthenticatedIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import java.net.URI;
import java.util.*;

@Configuration
public class SecurityConfiguration {
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<CorsFilter> corsRegistration(CorsFilter filter) {
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Only the Spring Security chain runs this filter.
        return registration;
    }
    @Bean SecurityFilterChain apiSecurity(HttpSecurity http,IamContextFacade iam,org.springframework.core.env.Environment environment) throws Exception {
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(s -> s.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(s -> s.requestCache(new NullRequestCache()))
                .csrf(c -> c.ignoringRequestMatchers("/api/**")).httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(a -> {
                    if(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("staging")))
                        a.requestMatchers(HttpMethod.GET,"/health/live","/health/ready").permitAll();
                    a.requestMatchers(HttpMethod.POST,"/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST,"/api/v1/iam/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/iam/users","/api/v1/iam/users/{userId}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/iam/users/{userId}/status").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/categories").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/categories/{id}").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/categories/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/products").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/products/{id}").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/products/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/products/{id}/activate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/products/{id}/deactivate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/lots").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/lots").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/lots/{id}").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/lots/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/correction-requests").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/correction-requests/{id}/review").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/correction-requests/{id}").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/correction-requests").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.POST,"/api/v1/inventory-movements").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/inventory-movements/{id}").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/inventory-movements").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.POST,"/api/v1/physical-counts").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/physical-counts/{id}").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/physical-counts").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.POST,"/api/v1/stock-adjustments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/stock-adjustments/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/stock-adjustments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/wastes").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/wastes/{id}").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/wastes").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.POST,"/api/v1/alerts/generate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/alerts").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/alerts/{id}").hasAnyRole("ADMIN","EMPLOYEE")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/alerts/{id}/resolve").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/audit-logs").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/audit-logs/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/offers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/offers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/offers/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/offers/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/offers/{id}/activate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/offers/{id}/deactivate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/recommendations/generate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/recommendations").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/recommendations/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/recommendations/{id}/dismiss").hasRole("ADMIN")
                        .anyRequest().denyAll();
                })
                .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> SecurityErrors.write(res,401))
                        .accessDeniedHandler((req,res,ex) -> SecurityErrors.write(res,403)))
                .oauth2ResourceServer(o -> o.authenticationEntryPoint((req,res,ex) -> SecurityErrors.write(res,401))
                        .accessDeniedHandler((req,res,ex) -> SecurityErrors.write(res,403))
                        .jwt(j -> j.jwtAuthenticationConverter(jwt -> {
                            long userId=Long.parseLong(jwt.getSubject());
                            long businessId=Long.parseLong(jwt.getClaims().get("businessId").toString());
                            String role=jwt.getClaimAsString("role");
                            var state=iam.getUserById(businessId,userId);
                            if(state.isEmpty() || !state.get().enabled() || !state.get().role().equals(role))
                                throw new InvalidBearerTokenException("Invalid token");
                            return new UsernamePasswordAuthenticationToken(new AuthenticatedIdentity(userId,businessId,role),null,
                                    List.of(new SimpleGrantedAuthority("ROLE_"+role)));
                        })));
        return http.build();
    }

    @Bean CorsFilter corsFilter(@Value("${bodego.security.cors.allowed-origins:}") String origins) {
        var cors=new CorsConfiguration();
        var allowed=Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        for(String origin:allowed) {
            URI uri=URI.create(origin);
            if(origin.contains("*") || !Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null
                    || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !uri.getPath().isEmpty())
                throw new IllegalStateException("CORS requires explicit HTTP(S) origins without paths or wildcards");
        }
        cors.setAllowedOrigins(allowed); cors.setAllowedMethods(List.of("GET","POST","PUT","PATCH"));
        cors.setAllowedHeaders(List.of("Authorization","Content-Type")); cors.setAllowCredentials(false); cors.setMaxAge(600L);
        var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",cors);
        var filter=new CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override protected void rejectRequest(org.springframework.http.server.ServerHttpResponse response) throws java.io.IOException {
                response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                response.getBody().write("{\"message\":\"Forbidden\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                response.flush();
            }
        });
        return filter;
    }
}
