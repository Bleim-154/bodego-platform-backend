package com.bodego.platform.iam.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class SecurityErrors {
    private SecurityErrors() {}
    public static void write(HttpServletResponse response,int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control","no-store");
        if(status==401) response.setHeader("WWW-Authenticate","Bearer");
        response.getWriter().write("{\"message\":\""+(status==401?"Unauthorized":"Forbidden")+"\"}");
    }
}
