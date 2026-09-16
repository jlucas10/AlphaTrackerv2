package com.alphatracker.api.trade;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
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
public class TradeAttachmentController {

    private final TradeAttachmentService attachmentService;

    // Multipart upload onto an existing trade. Sizing/content-type rules live
    // in TradeAttachmentService, not here - this controller only adapts the
    // HTTP multipart shape into the plain (InputStream, size, filename,
    // contentType) shape the service and StorageService both speak.
    @PostMapping(value = "/trades/{tradeId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TradeAttachmentResponse> uploadAttachment(
            @PathVariable Long tradeId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String caption,
            @AuthenticationPrincipal User user) {
        try (InputStream content = file.getInputStream()) {
            TradeAttachment attachment = attachmentService.uploadAttachment(
                    tradeId, content, file.getSize(), file.getOriginalFilename(),
                    file.getContentType(), caption, user);
            return ResponseEntity.ok(TradeAttachmentResponse.fromEntity(attachment));
        } catch (IOException e) {
            throw new StorageException("Failed to read uploaded file.", e);
        }
    }

    @GetMapping("/trades/{tradeId}/attachments")
    public ResponseEntity<List<TradeAttachmentResponse>> getAttachmentsForTrade(
            @PathVariable Long tradeId,
            @AuthenticationPrincipal User user) {
        List<TradeAttachmentResponse> attachments = attachmentService.getAttachmentsForTrade(tradeId, user)
                .stream()
                .map(TradeAttachmentResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(attachments);
    }

    // The ownership-checked retrieval endpoint every attachment's "url" points
    // at. getAttachmentForDownload re-verifies ownership on every call - a
    // trader can't hand this link to someone else and have it keep working for
    // them, since there's no token embedded in the URL, only the id.
    @GetMapping("/attachments/{id}/file")
    public ResponseEntity<InputStreamResource> getAttachmentFile(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        TradeAttachment attachment = attachmentService.getAttachmentForDownload(id, user);
        InputStream content = attachmentService.openAttachmentContent(attachment);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .body(new InputStreamResource(content));
    }

    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<String> deleteAttachment(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        attachmentService.deleteAttachment(id, user);
        return ResponseEntity.ok("Attachment successfully deleted.");
    }
}
