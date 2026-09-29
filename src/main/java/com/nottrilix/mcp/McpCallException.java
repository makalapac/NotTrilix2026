package com.nottrilix.mcp;

/** Poziv alata bez potrebnog konteksta (nema kupca ili nema razgovora); alat ga vraca kao ok=false + kod. */
public class McpCallException extends RuntimeException {

    private final String code;

    public McpCallException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
