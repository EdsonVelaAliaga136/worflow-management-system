package com.evela.sigotrab.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SigAuthenticationFailureHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String code = switch (exception){
            case DisabledException e -> "disabled";
            case LockedException e -> "locked";
            case AccountExpiredException e -> "expired";
            case CredentialsExpiredException e -> "credentials";
            default -> "badCredentials";
        };
        request.getSession(true).setAttribute("loginError", code);
        response.sendRedirect(request.getContextPath()+"/login?error");
    }
}
