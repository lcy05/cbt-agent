package com.luoch.cbt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TurnResponse(
        @JsonProperty("threadId") String threadId,
        @JsonProperty("node") String node,
        @JsonProperty("status") String status,
        @JsonProperty("question") String question,
        @JsonProperty("userResponse") String userResponse) {
}
