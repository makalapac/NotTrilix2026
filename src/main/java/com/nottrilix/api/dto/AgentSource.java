package com.nottrilix.api.dto;

/** Knowledge base chunk that was added to the question (RAG): document, section and similarity 0..1. */
public record AgentSource(String source, String title, String section, double score) {
}
