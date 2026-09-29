package com.nottrilix.api.dto;

/** Odlomak baze znanja koji je dodan pitanju (RAG): dokument, odjeljak i slicnost 0..1. */
public record AgentSource(String source, String title, String section, double score) {
}
