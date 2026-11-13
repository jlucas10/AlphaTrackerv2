package com.alphatracker.api.demo;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.alphatracker.api.user.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Runs after the JWT filter, so the authenticated principal is already the loaded User row. The decision
// comes from the database flag (User.demo), never from the token, so a forged claim cannot unlock or
// bypass anything.
public class DemoGuardInterceptor implements HandlerInterceptor {

    public static final String MESSAGE = "This action isn't available in the demo account. Sign up for a free account to use it.";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(BlockedForDemo.class)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user && user.isDemo()) {
            throw new DemoForbiddenException(MESSAGE);
        }
        return true;
    }
}
