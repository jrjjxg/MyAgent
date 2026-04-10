package com.xg.platform.document.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentChunk(
        String chunkId,
        String documentId,
        String documentName,
        int pageStart,
        int pageEnd,
        String text,
        String sectionTitle,
        Integer chunkOrder
) implements Serializable {

    public DocumentChunk(String chunkId,
                         String documentId,
                         String documentName,
                         int pageStart,
                         int pageEnd,
                         String text) {
        this(chunkId, documentId, documentName, pageStart, pageEnd, text, null, null);
    }

    public String searchableText() {
        if (sectionTitle == null || sectionTitle.isBlank()) {
            return text == null ? "" : text;
        }
        return sectionTitle + System.lineSeparator() + (text == null ? "" : text);
    }
}
