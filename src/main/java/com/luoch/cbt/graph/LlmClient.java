package com.luoch.cbt.graph;

/** Minimal LLM boundary: production uses Spring AI ChatClient, tests use a fixed stub. */
@FunctionalInterface
public interface LlmClient {
    String chat(String system, String user);
}
