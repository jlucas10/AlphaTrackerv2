package com.alphatracker.api.journal;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.alphatracker.api.storage.StorageException;
import com.alphatracker.api.user.User;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class JournalAttachmentController {

    private final JournalAttachmentService attachmentService;

    // Multipart upload onto a day's journal entry (created on first write).
    // Sizing/content-type rules live in JournalAttachmentService, not here -
    // this controller only adapts the HTTP multipart shape into the plain
    // (InputStream, size, filename, contentType) shape the service and
    // StorageService both speak.
    @PostMapping(value = "/journal/{date}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JournalAttachmentResponse> uploadAttachment(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String caption,
            @AuthenticationPrincipal User user) {
        try (InputStream content = file.getInputStream()) {
            JournalAttachment attachment = attachmentService.uploadAttachment(
                    date, content, file.getSize(), file.getOriginalFilename(),
                    file.getContentType(), caption, user);
            return ResponseEntity.ok(JournalAttachmentResponse.fromEntity(attachment));
        } catch (IOException e) {
            throw new StorageException("Failed to read uploaded file.", e);
        }
    }

    @GetMapping("/journal/{date}/attachments")
    public ResponseEntity<List<JournalAttachmentResponse>> getAttachmentsForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal User user) {
        List<JournalAttachmentResponse> attachments = attachmentService.getAttachmentsForDate(date, user)
                .stream()
                .map(JournalAttachmentResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(attachments);
    }

    // The ownership-checked retrieval endpoint every attachment's "url"
    // points at. getAttachmentForDownload re-verifies ownership on every
    // call - a trader can't hand this link to someone else and have it keep
    // working for them, since there's no token embedded in the URL, only the id.
    @GetMapping("/journal-attachments/{id}/file")
    public ResponseEntity<InputStreamResource> getAttachmentFile(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        JournalAttachment attachment = attachmentService.getAttachmentForDownload(id, user);
        InputStream content = attachmentService.openAttachmentContent(attachment);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .body(new InputStreamResource(content));
    }

    @DeleteMapping("/journal-attachments/{id}")
    public ResponseEntity<String> deleteAttachment(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        attachmentService.deleteAttachment(id, user);
        return ResponseEntity.ok("Attachment successfully deleted.");
    }
}
