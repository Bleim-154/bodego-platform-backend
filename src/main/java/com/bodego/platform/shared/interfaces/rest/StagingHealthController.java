package com.bodego.platform.shared.interfaces.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

/** Technical probes only. No account, schema, connection or exception details are returned. */
@RestController
@Profile("staging")
public class StagingHealthController {
    private final DataSource dataSource;
    public StagingHealthController(DataSource dataSource) { this.dataSource=dataSource; }

    @GetMapping("/health/live")
    public ResponseEntity<Map<String,String>> live() { return status(200,"UP"); }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String,String>> ready() {
        try(var connection=dataSource.getConnection()) {
            connection.setReadOnly(true);
            try(var statement=connection.createStatement()) {
                statement.setQueryTimeout(2);
                try(var result=statement.executeQuery("SELECT 1")) {
                    if(result.next() && result.getInt(1)==1) return status(200,"UP");
                }
            }
        } catch(SQLException | RuntimeException failure) { /* Deliberately do not expose details. */ }
        return status(503,"OUT_OF_SERVICE");
    }

    private ResponseEntity<Map<String,String>> status(int code,String value) {
        return ResponseEntity.status(code).cacheControl(CacheControl.noStore()).body(Map.of("status",value));
    }
}
