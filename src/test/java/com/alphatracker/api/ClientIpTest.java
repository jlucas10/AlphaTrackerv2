package com.alphatracker.api;

import com.alphatracker.api.demo.ClientIp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientIpTest {

    private MockHttpServletRequest request(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    @DisplayName("takes the entry our own proxy appended (rightmost), ignoring a client-spoofed leftmost one")
    void ignoresSpoofedLeftmostEntry() {
        assertEquals("203.0.113.7", ClientIp.resolve(request("6.6.6.6, 203.0.113.7"), 1));
    }

    @Test
    @DisplayName("with two trusted hops it takes the second entry from the right")
    void honoursHopCount() {
        assertEquals("203.0.113.7", ClientIp.resolve(request("6.6.6.6, 203.0.113.7, 172.16.0.1"), 2));
    }

    @Test
    @DisplayName("falls back to the socket address when there is no header, or fewer entries than hops")
    void fallsBackToRemoteAddr() {
        assertEquals("10.0.0.9", ClientIp.resolve(request(null), 1));
        assertEquals("10.0.0.9", ClientIp.resolve(request("203.0.113.7"), 2));
        assertEquals("10.0.0.9", ClientIp.resolve(request("203.0.113.7"), 0));
    }
}
