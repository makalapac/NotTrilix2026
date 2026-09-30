package com.nottrilix.agent;

/** Knowledge base chunk added to the question (RAG): document, section and similarity 0..1. */
public record AgentSource(String source, String title, String section, double score) {
}
