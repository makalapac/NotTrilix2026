package com.nottrilix.kb;

/** Knowledge base is unavailable (no OpenAI key or the embedding service is not responding). The API maps it to HTTP 503. */
public class KnowledgeBaseUnavailableException extends RuntimeException {

    public KnowledgeBaseUnavailableException(String message) {
        super(message);
    }
}
