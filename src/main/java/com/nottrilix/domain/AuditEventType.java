package com.nottrilix.domain;

public enum AuditEventType {
    /** The agent called a tool. */
    TOOL_CALL,
    /** Retrieval of knowledge base sections for the customer's question (RAG). */
    KB_RETRIEVAL,
    /** One conversation turn: the customer's message and the agent's reply. */
    CHAT_TURN,
    /** Conversation analysis and learning from it (back-office, no customer in the conversation). */
    ANALYSIS,
    /** Privacy: consent, data export, erasure on request, retention. */
    PRIVACY,
    /** Phone call: the caller identified (or not) by caller id, and the end of the call. */
    PHONE_CALL
}
