package com.aquasense.reporting.domain;

public enum ReportFormat {
    MARKDOWN("text/markdown", "md"),
    HTML("text/html", "html");

    private final String mediaType;
    private final String extension;

    ReportFormat(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }
}
