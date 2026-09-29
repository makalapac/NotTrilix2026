package com.nottrilix.agent;

import com.nottrilix.api.dto.AgentToolCall;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Sto je agent napravio u jednom turnu (pozivi alata redom); vraca se klijentu uz odgovor. */
public class AgentTrace {

    private final List<AgentToolCall> toolCalls = new CopyOnWriteArrayList<>();

    public void add(AgentToolCall call) {
        toolCalls.add(call);
    }

    public List<AgentToolCall> toolCalls() {
        return List.copyOf(toolCalls);
    }
}
