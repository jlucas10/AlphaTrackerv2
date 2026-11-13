package com.alphatracker.api.demo;

import jakarta.servlet.http.HttpServletRequest;

// Works out which IP to rate-limit. Behind Railway the socket address is the proxy's, so the real client
// is in X-Forwarded-For. Each proxy APPENDS the address it received the request from, so the left-hand
// entries are whatever the client claimed (spoofable) and only the entries our own proxies added can be
// trusted. `trustedProxyHops` is how many of those there are; we take the entry that many from the right.
public final class ClientIp {

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request, int trustedProxyHops) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && trustedProxyHops > 0) {
            String[] parts = forwarded.split(",");
            int index = parts.length - trustedProxyHops;
            if (index >= 0 && !parts[index].isBlank()) {
                return parts[index].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
