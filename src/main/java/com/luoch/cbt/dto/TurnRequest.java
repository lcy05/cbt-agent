package com.luoch.cbt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One turn from the user. {@code recordApproved} is the only channel through which the
 * user can authorize saving the pending record; it defaults to false when the field is
 * absent from the JSON body.
 */
public record TurnRequest(
        @JsonProperty("threadId") String threadId,
        @JsonProperty("answer") String answer,
        @JsonProperty("recordApproved") boolean recordApproved) {

    public TurnRequest(String threadId, String answer) {
        this(threadId, answer, false);
    }
}
