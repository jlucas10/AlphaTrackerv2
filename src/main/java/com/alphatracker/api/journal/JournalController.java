package com.alphatracker.api.journal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeService;
import com.alphatracker.api.user.User;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/journal")
@RequiredArgsConstructor
public class JournalController {

    private final JournalEntryService journalEntryService;
    private final JournalAttachmentService journalAttachmentService;
    private final TradeService tradeService;

    @GetMapping("/{date}")
    public ResponseEntity<JournalDayResponse> getDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(buildDayResponse(date, journalEntryService.getOrDefault(date, user), user));
    }

    // Upserts notes/HTF bias for the day - creates the JournalEntry row if
    // this is the first write, same as an attachment upload would.
    @PutMapping("/{date}")
    public ResponseEntity<JournalDayResponse> upsertDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody JournalDayRequest request,
            @AuthenticationPrincipal User user) {
        JournalEntry entry = journalEntryService.upsertNotes(date, request.getNotes(), request.getHtfBias(), user);
        return ResponseEntity.ok(buildDayResponse(date, entry, user));
    }

    // Takes the already-fetched/just-written entry rather than re-querying it,
    // so a PUT doesn't pay for a redundant read right after its own write.
    private JournalDayResponse buildDayResponse(LocalDate date, JournalEntry entry, User user) {
        List<JournalAttachment> attachments = journalAttachmentService.getAttachmentsForDate(date, user);
        List<Trade> trades = tradeService.getTradesForDate(date, user);
        return JournalDayResponse.fromEntities(date, entry, attachments, trades);
    }
}
