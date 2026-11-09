package com.alphatracker.api.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Same upload as preview, now written. The trader's "did you follow your plan" answers ride along as plain
    // repeated params (no JSON inside multipart): followedPlanYes / followedPlanNo are lists of broker trade
    // ids, and defaultFollowedPlan (optional) covers every trade in neither list. Anything unanswered stays
    // unspecified (null). An id in both lists is a contradiction and a 400.
    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportConfirmResponse> confirm(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "accountIds", required = false) List<Long> accountIds,
            @RequestParam(value = "followedPlanYes", required = false) List<String> followedPlanYes,
            @RequestParam(value = "followedPlanNo", required = false) List<String> followedPlanNo,
            @RequestParam(value = "defaultFollowedPlan", required = false) Boolean defaultFollowedPlan,
            @AuthenticationPrincipal User user) {
        ImportSource importSource = ImportSource.fromValue(source);
        if (file == null) {
            throw new IllegalArgumentException("Choose a file to upload.");
        }
        Map<String, Boolean> followedPlan = new HashMap<>();
        if (followedPlanYes != null) {
            followedPlanYes.forEach(id -> followedPlan.put(id.trim(), true));
        }
        if (followedPlanNo != null) {
            for (String id : followedPlanNo) {
                if (followedPlan.put(id.trim(), false) != null) {
                    throw new IllegalArgumentException(
                            "Trade " + id.trim() + " is marked both as followed and as not followed the plan.");
                }
            }
        }
        try (InputStream content = file.getInputStream()) {
            return ResponseEntity.ok(importService.confirm(importSource, accountIds, content, followedPlan,
                    defaultFollowedPlan, user));
        } catch (IOException e) {
            throw new IllegalArgumentException("The file could not be read.");
        }
    }
}
