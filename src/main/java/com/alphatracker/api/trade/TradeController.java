// Instead of forcing the frontend to pass a vulnerable userId manually in the JSON
// body (which anyone could intercept and alter), we use Spring Security to grab 
// the user directly from the encrypted JWT token. This completely blocks 
// unauthorized access.

package com.alphatracker.api.trade;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.alphatracker.api.user.User;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/trades") // All endpoints in this class are automatically prefixed with this URL
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    // Handles POST requests to /api/v1/trades.
    // @AuthenticationPrincipal is a Spring annotation that automatically looks
    // inside the
    // SecurityContextHolder, extracts the logged-in User, and injects it straight
    // into our method.
    @PostMapping
    public ResponseEntity<Trade> createTrade(
            @RequestBody Trade trade,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tradeService.logTrade(trade, user));
    }

    // Get All Trades for the Current User
    // Handles GET requests to /api/v1/trades. Returns only the trades belonging to
    // the active token.
    @GetMapping
    public ResponseEntity<List<Trade>> getAllTrades(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tradeService.getTradesForUser(user));
    }

    // Handles GET requests to /api/v1/trades/{id} (ex, /api/v1/trades/5)
    @GetMapping("/{id}")
    public ResponseEntity<Trade> getTradeById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tradeService.getTradeById(id, user));
    }

    // Handles DELETE requests to /api/v1/trades/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTrade(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        tradeService.deleteTrade(id, user);
        return ResponseEntity.ok("Trade entry successfully deleted.");
    }
}