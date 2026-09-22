package com.nottrilix.api.dto;

public record KbIngestResult(int files, int addedChunks, int skippedFiles, int replacedFiles, long totalChunks) {
}
