package com.nottrilix.agent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** What the agent did in one turn (tool calls in order); returned to the client along with the reply. */
public class AgentTrace {

    private final List<AgentToolCall> toolCalls = new CopyOnWriteArrayList<>();

    public void add(AgentToolCall call) {
        toolCalls.add(call);
    }

    public List<AgentToolCall> toolCalls() {
        return List.copyOf(toolCalls);
    }
}
