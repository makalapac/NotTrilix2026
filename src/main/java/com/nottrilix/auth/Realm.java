package com.nottrilix.auth;

/** Kinds of system users; each has its own login and its own role (ROLE_CUSTOMER / ROLE_ADMIN / ROLE_AGENT). */
public enum Realm {
    /** Customer - logs in with e-mail and password, uses the shop. */
    CUSTOMER,
    /** Admin/support - logs in with username and password, uses the admin console. */
    ADMIN,
    /**
     * The agent application (MCP client) - identifies itself with its key. It may connect to the MCP server and see
     * the tool list; the tools work only on behalf of the logged-in customer, i.e. only with the customer's token.
     */
    AGENT
}
