package com.alphatracker.api.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.alphatracker.api.user.User;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    // Multipart: file, source (e.g. TOPSTEPX) and one or more accountIds (repeat the param, or comma-separate).
    // Like the other controllers this only adapts HTTP into plain arguments; the rules live in ImportService.
    // Every parameter is declared optional so a missing one reaches our own check and comes back as a 400
    // with a readable "message", instead of Spring's empty-bodied default.
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreviewResponse> preview(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "accountIds", required = false) List<Long> accountIds,
            @AuthenticationPrincipal User user) {
        ImportSource importSource = ImportSource.fromValue(source);
        if (file == null) {
            throw new IllegalArgumentException("Choose a file to upload.");
        }
        try (InputStream content = file.getInputStream()) {
            return ResponseEntity.ok(importService.preview(importSource, accountIds, content, user));
        } catch (IOException e) {
            throw new IllegalArgumentException("The file could not be read.");
        }
    }
}
