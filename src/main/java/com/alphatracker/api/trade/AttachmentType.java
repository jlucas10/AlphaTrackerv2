package com.alphatracker.api.trade;

// Only SCREENSHOT exists today (the Sprint 3 upload path is drag-and-drop /
// clipboard-paste images only), kept as an enum instead of a boolean flag so
// a future attachment kind (e.g. a PDF trade plan) doesn't require a schema
// migration to introduce - just a new constant.
public enum AttachmentType {
    SCREENSHOT
}
