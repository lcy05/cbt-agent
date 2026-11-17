package com.luoch.cbt.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.serializer.std.SpringAIStateSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luoch.cbt.entity.SessionRecord;
import com.luoch.cbt.mapper.SessionRecordMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/** Builds the state graph. Tests compile the same graph with a MemorySaver. */
public final class CbtGraphFactory {

    private CbtGraphFactory() {
    }

    private static final EdgeAction FLOW_EDGE =
            state -> text(state, StateKeys.FLOW);
    private static final EdgeAction RESUME_EDGE =
            state -> text(state, StateKeys.RESUME_TARGET);

    public static StateGraph build(LlmClient llm, ObjectMapper mapper,
                                   SessionRecordMapper records) throws Exception {
        LlmNodeSupport support = new LlmNodeSupport(
                llm, mapper, LlmNodeSupport.readTemplate("system.txt"));

        StateGraph g = new StateGraph("cbt-minimal-loop", KeyStrategies.build(),
                new SpringAIStateSerializer());

        // --- LLM nodes -------------------------------------------------
        g.addNode(Nodes.SAFETY_TRIAGE, node_async(support.strict(safetySpec())));
        g.addNode(Nodes.PROBLEM_IDENTIFICATION, node_async(support.strict(problemSpec())));
        g.addNode(Nodes.CONTEXT_EXTRACTION, node_async(support.strict(contextSpec())));
        g.addNode(Nodes.EVIDENCE_REVIEW, node_async(support.strict(evidenceSpec())));
        g.addNode(Nodes.INTERVENTION_DECISION, node_async(support.strict(interventionSpec())));
        g.addNode(Nodes.BEHAVIORAL_EXPERIMENT, node_async(support.strict(behaviorSpec())));
        g.addNode(Nodes.PRACTICAL_PROBLEM_SOLVING, node_async(support.strict(practicalSpec())));
        g.addNode(Nodes.RESULT_EVALUATION, node_async(support.strict(resultSpec())));
        g.addNode(Nodes.SESSION_SUMMARY, node_async(support.strict(summarySpec())));
        g.addNode(Nodes.EMERGENCY_SUPPORT,
                node_async(support.freeform(Nodes.EMERGENCY_SUPPORT, "01b_emergency_support.txt")));

        // --- zero-side-effect control nodes ----------------------------
        g.addNode(Nodes.RESUME_GATE, node_async(state -> Map.<String, Object>of()));
        g.addNode(Nodes.AWAIT_USER, node_async(state -> Map.<String, Object>of()));

        // --- static text nodes (no invented local resources) -----------
        g.addNode(Nodes.PAUSE_AND_SUPPORT, node_async(textNode(Nodes.PAUSE_AND_SUPPORT,
                "我们先暂停这一轮的练习。如果你现在需要即时帮助，请联系所在地的急救服务、危机干预服务或最近的急诊机构，并尽量联系一位能够实际陪伴你的可信任的人。等你想继续时，我们再从这里开始。")));
        g.addNode(Nodes.PROFESSIONAL_SUPPORT, node_async(textNode(Nodes.PROFESSIONAL_SUPPORT,
                "这个问题可能超出自助练习的范围，建议尽快寻求具备资质的专业评估与支持。你可以随时回来继续记录已经梳理清楚的部分。")));
        g.addNode(Nodes.PARSE_ERROR, node_async(textNode(Nodes.PARSE_ERROR,
                "刚才这一步没能正确理解，可以换句话再说一次吗？")));
        // parse_error keeps the previous resume_target so the user can restate and retry.
        g.addNode(Nodes.PERSIST_RECORD, AsyncNodeActionWithConfig.node_async(
                (state, config) -> persist(state, config.threadId().orElse(""), mapper, records)));

        // --- fixed edges -----------------------------------------------
        g.addEdge(StateGraph.START, Nodes.SAFETY_TRIAGE);
        g.addEdge(Nodes.AWAIT_USER, Nodes.SAFETY_TRIAGE);   // every user answer re-enters the safety gate
        g.addEdge(Nodes.PARSE_ERROR, Nodes.AWAIT_USER);
        g.addEdge(Nodes.EMERGENCY_SUPPORT, StateGraph.END);
        g.addEdge(Nodes.PAUSE_AND_SUPPORT, StateGraph.END);
        g.addEdge(Nodes.PROFESSIONAL_SUPPORT, StateGraph.END);

        // --- conditional edges (all mappings exhaustive) ----------------
        g.addConditionalEdges(Nodes.SAFETY_TRIAGE, edge_async(FLOW_EDGE), Routes.SAFETY);
        g.addConditionalEdges(Nodes.RESUME_GATE, edge_async(RESUME_EDGE), Routes.RESUME_GATE);
        g.addConditionalEdges(Nodes.PROBLEM_IDENTIFICATION, edge_async(FLOW_EDGE), Routes.PROBLEM);
        g.addConditionalEdges(Nodes.CONTEXT_EXTRACTION, edge_async(FLOW_EDGE), Routes.CONTEXT);
        g.addConditionalEdges(Nodes.EVIDENCE_REVIEW, edge_async(FLOW_EDGE), Routes.EVIDENCE);
        g.addConditionalEdges(Nodes.INTERVENTION_DECISION, edge_async(FLOW_EDGE), Routes.INTERVENTION);
        g.addConditionalEdges(Nodes.BEHAVIORAL_EXPERIMENT, edge_async(FLOW_EDGE), Routes.BEHAVIOR);
        g.addConditionalEdges(Nodes.PRACTICAL_PROBLEM_SOLVING, edge_async(FLOW_EDGE), Routes.PRACTICAL);
        g.addConditionalEdges(Nodes.RESULT_EVALUATION, edge_async(FLOW_EDGE), Routes.RESULT);
        g.addConditionalEdges(Nodes.SESSION_SUMMARY, edge_async(FLOW_EDGE), Routes.SUMMARY);
        g.addConditionalEdges(Nodes.PERSIST_RECORD, edge_async(FLOW_EDGE), Routes.PERSIST);

        return g;
    }

    private static NodeAction textNode(String nodeId, String message) {
        return state -> Map.<String, Object>of(
                StateKeys.NODE, nodeId,
                StateKeys.USER_RESPONSE, message,
                StateKeys.PENDING_QUESTION, Nodes.PARSE_ERROR.equals(nodeId) ? message : "",
                StateKeys.MESSAGES, List.of("[" + nodeId + "] " + message));
    }

    private static Map<String, Object> persist(OverAllState state, String threadId,
                                               ObjectMapper mapper, SessionRecordMapper records) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(StateKeys.NODE, Nodes.PERSIST_RECORD);
        out.put(StateKeys.PENDING_QUESTION, "");
        // One-shot: the authorization never survives this node, saved or skipped.
        out.put(StateKeys.RECORD_APPROVED, Boolean.FALSE);

        if (!approved(state.data().get(StateKeys.RECORD_APPROVED))) {
            String message = "本轮记录还没有得到你的确认，尚未写入数据库。确认无误后回复「确认保存」，我再写入。";
            out.put(StateKeys.FLOW, Routes.AWAIT_APPROVAL_FLOW);
            out.put(StateKeys.USER_RESPONSE, message);
            out.put(StateKeys.PENDING_QUESTION, message);
            out.put(StateKeys.MESSAGES, List.of("[persist_record] skipped: record not approved"));
            return out;
        }

        SessionRecord r = new SessionRecord();
        r.setThreadId(threadId);
        r.setSessionSummary(text(state, "session_summary"));
        r.setConfirmedFacts(json(mapper, state, "confirmed_facts"));
        r.setUserReportedThoughts(json(mapper, state, "user_reported_thoughts"));
        r.setUserReportedEmotions(json(mapper, state, "user_reported_emotions"));
        r.setEvidenceReview(json(mapper, state, "evidence_review"));
        r.setAcceptedAlternativeThought(text(state, "accepted_alternative_thought"));
        r.setIntervention(text(state, "intervention"));
        r.setActionResult(text(state, "action_result"));
        r.setUnresolvedIssues(json(mapper, state, "unresolved_issues"));
        r.setNextStep(text(state, "next_step"));
        r.setConfidenceOfRecord(text(state, "confidence_of_record"));
        // Rows only exist when the user authorized them, so the stored flag is always 1.
        r.setRecordApproved(1);
        records.insert(r);

        out.put(StateKeys.FLOW, Routes.SAVED_FLOW);
        out.put(StateKeys.USER_RESPONSE, text(state, "session_summary"));
        out.put(StateKeys.MESSAGES, List.of("[persist_record] 本轮记录已保存"));
        return out;
    }

    /**
     * Only a value written by the service from the user's request counts. LLM output can
     * never reach this key: NodeOutputGuard strips every protected key first.
     */
    private static boolean approved(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String json(ObjectMapper mapper, OverAllState state, String key) {
        Object v = state.data().get(key);
        try {
            return mapper.writeValueAsString(v == null ? List.of() : v);
        }
        catch (Exception e) {
            return "[]";
        }
    }

    private static String text(OverAllState state, String key) {
        Object v = state.data().get(key);
        return v == null ? "" : String.valueOf(v);
    }

    // ---- node specs: required fields + enum allow-lists, straight from 最小介入闭环.md ----

    private static NodeSpec safetySpec() {
        return NodeSpec.routed(Nodes.SAFETY_TRIAGE, "01_safety_triage.txt",
                Set.of("safety_status", "route", "reason_summary", "question", "user_response"),
                Map.of("safety_status", Routes.SAFETY_STATUSES,
                        "route", Routes.SAFETY_ROUTES),
                NodeSpec.FlowMode.SAFETY);
    }

    private static NodeSpec problemSpec() {
        return NodeSpec.collect(Nodes.PROBLEM_IDENTIFICATION, "02_problem_identification.txt",
                Set.of("problem_type", "target_problem", "event_available", "question",
                        "ready_for_next", "user_response"),
                Map.of("problem_type", Set.of("具体事件", "抽象困扰", "多个问题", "理论咨询", "暂时不明确")),
                Nodes.CONTEXT_EXTRACTION, Nodes.PROBLEM_IDENTIFICATION, false);
    }

    private static NodeSpec contextSpec() {
        return NodeSpec.collect(Nodes.CONTEXT_EXTRACTION, "03_context_extraction.txt",
                Set.of("situation", "automatic_thought", "emotions", "physical_responses",
                        "behaviors", "missing_information", "question", "ready_for_next", "user_response"),
                Map.<String, Set<String>>of(), Nodes.EVIDENCE_REVIEW, Nodes.CONTEXT_EXTRACTION, false);
    }

    private static NodeSpec evidenceSpec() {
        return NodeSpec.collect(Nodes.EVIDENCE_REVIEW, "04_evidence_review.txt",
                Set.of("target_thought", "evidence_for", "evidence_against", "other_explanations",
                        "uncertainties", "balanced_thought_candidate", "user_acceptance",
                        "question", "ready_for_next", "user_response"),
                Map.of("user_acceptance", Set.of("未确认", "接受", "修改", "不接受")),
                Nodes.INTERVENTION_DECISION, Nodes.EVIDENCE_REVIEW, false);
    }

    private static NodeSpec interventionSpec() {
        return NodeSpec.routed(Nodes.INTERVENTION_DECISION, "05_intervention_decision.txt",
                Set.of("intervention_type", "selection_reason", "target",
                        "safety_considerations", "question", "user_response"),
                Map.of("intervention_type", Routes.INTERVENTION_TYPES),
                NodeSpec.FlowMode.INTERVENTION);
    }

    private static NodeSpec behaviorSpec() {
        return NodeSpec.collect(Nodes.BEHAVIORAL_EXPERIMENT, "06a_behavioral_experiment.txt",
                Set.of("prediction", "confidence_before", "experiment_action", "observation_items",
                        "evaluation_criteria", "safety_check", "user_approval", "user_response"),
                Map.of("user_approval", Set.of("未确认", "已确认", "拒绝", "需要修改")),
                Nodes.RESULT_EVALUATION, Nodes.BEHAVIORAL_EXPERIMENT, true);
    }

    private static NodeSpec practicalSpec() {
        return NodeSpec.collect(Nodes.PRACTICAL_PROBLEM_SOLVING, "06b_practical_problem_solving.txt",
                Set.of("problem_definition", "controllable_factors", "influenceable_factors",
                        "uncontrollable_factors", "options", "selected_action",
                        "evaluation_criteria", "user_approval", "user_response"),
                Map.of("user_approval", Set.of("未确认", "已确认", "拒绝", "需要修改")),
                Nodes.RESULT_EVALUATION, Nodes.PRACTICAL_PROBLEM_SOLVING, true);
    }

    private static NodeSpec resultSpec() {
        return NodeSpec.routed(Nodes.RESULT_EVALUATION, "07_result_evaluation.txt",
                Set.of("actual_action", "actual_result", "prediction_comparison",
                        "new_information", "remaining_uncertainties", "emotion_after",
                        "next_step", "question", "user_response"),
                Map.of("prediction_comparison", Set.of("支持", "部分支持", "不支持", "不确定", "未完成"),
                        "next_step", Routes.NEXT_STEPS),
                NodeSpec.FlowMode.RESULT);
    }

    private static NodeSpec summarySpec() {
        return NodeSpec.collect(Nodes.SESSION_SUMMARY, "08_session_summary.txt",
                Set.of("session_summary", "confirmed_facts", "user_reported_thoughts",
                        "user_reported_emotions", "evidence_review", "accepted_alternative_thought",
                        "intervention", "action_result", "unresolved_issues", "next_step",
                        "confidence_of_record"),
                Map.of("confidence_of_record", Set.of("信息完整", "部分信息缺失")),
                Nodes.PERSIST_RECORD, Nodes.PERSIST_RECORD, true);
    }
}
