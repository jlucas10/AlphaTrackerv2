package com.alphatracker.api.storage;

import java.util.UUID;

// Shared key-generation logic for every StorageService adapter. The key
// format ({ownerId}/{uuid}{extension}) and the filename sanitization it
// depends on live in exactly one place, so a future adapter can't
// accidentally diverge from the security property this guarantees: the
// generated key never comes from anything the caller supplied, only a fresh
// UUID plus a whitelisted extension.
final class StorageKeys {

    private StorageKeys() {
    }

    static String generate(Long ownerId, String originalFilename) {
        return ownerId + "/" + UUID.randomUUID() + extractExtension(originalFilename);
    }

    // Keeps only a short, whitelisted extension from the caller-supplied
    // filename. Everything else about the original filename is discarded -
    // the generated key is always a fresh UUID, so this is purely cosmetic
    // (keeps ".png"/".jpg" etc. on the object) and never trusted for pathing.
    private static String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        String ext = originalFilename.substring(dot + 1);
        if (ext.length() > 10 || !ext.chars().allMatch(Character::isLetterOrDigit)) {
            return "";
        }
        return "." + ext.toLowerCase();
    }
}
