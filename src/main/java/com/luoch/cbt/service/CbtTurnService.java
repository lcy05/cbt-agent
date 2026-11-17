package com.luoch.cbt.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.luoch.cbt.dto.TurnRequest;
import com.luoch.cbt.dto.TurnResponse;
import com.luoch.cbt.graph.Nodes;
import com.luoch.cbt.graph.StateKeys;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CbtTurnService {

    private final CompiledGraph graph;

    public CbtTurnService(CompiledGraph graph) {
        this.graph = graph;
    }

    public TurnResponse turn(TurnRequest request) {
        boolean fresh = request.threadId() == null || request.threadId().isBlank();
        String threadId = fresh ? UUID.randomUUID().toString() : request.threadId();
        String answer = request.answer() == null ? "" : request.answer();

        try {
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
            Optional<OverAllState> result;
            if (fresh) {
                Map<String, Object> initial = new LinkedHashMap<>();
                initial.put(StateKeys.USER_ANSWER, answer);
                initial.put(StateKeys.RESUME_TARGET, Nodes.PROBLEM_IDENTIFICATION);
                initial.put(StateKeys.RECORD_APPROVED, request.recordApproved());
                initial.put(StateKeys.MESSAGES, List.of("[user] " + answer));
                result = graph.invoke(initial, config);
            }
            else {
                Map<String, Object> update = new LinkedHashMap<>();
                update.put(StateKeys.USER_ANSWER, answer);
                // Written on every turn: an absent approval explicitly resets a stale one,
                // so authorization can never be reused by a later record.
                update.put(StateKeys.RECORD_APPROVED, request.recordApproved());
                update.put(StateKeys.MESSAGES, List.of("[user] " + answer));
                RunnableConfig resumed = graph.updateState(config, update, null);
                // NOT invoke(null, config): 1.1.2.0 has two invoke overloads, bare null is ambiguous.
                result = graph.invoke(Map.<String, Object>of(), resumed);
            }

            Map<String, Object> state = result.map(OverAllState::data).orElse(Map.<String, Object>of());
            String question = text(state, StateKeys.PENDING_QUESTION);
            String status = question.isEmpty() ? "completed" : "waiting_user";
            return new TurnResponse(threadId,
                    text(state, StateKeys.NODE), status, question,
                    text(state, StateKeys.USER_RESPONSE));
        }
        catch (Exception e) {
            return new TurnResponse(threadId, "", "error", "", "本轮处理失败，请稍后重试。");
        }
    }

    private static String text(Map<String, Object> state, String key) {
        Object v = state.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
