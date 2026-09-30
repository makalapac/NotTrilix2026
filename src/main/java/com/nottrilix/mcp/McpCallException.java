package com.nottrilix.mcp;

/** Tool call without the required context (no customer or no conversation); the tool returns it as ok=false + code. */
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
