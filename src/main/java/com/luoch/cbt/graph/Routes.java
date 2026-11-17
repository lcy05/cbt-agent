package com.luoch.cbt.graph;

import com.alibaba.cloud.ai.graph.StateGraph;

import java.util.Map;
import java.util.Set;

/**
 * All conditional-edge mappings live here so a single test can prove every enum value
 * is mapped. An unmapped value makes the graph throw at runtime, so exhaustiveness is
 * a hard requirement, not a style choice.
 */
public final class Routes {

    private Routes() {
    }

    public static final String PARSE_ERROR_FLOW = "parse_error";
    public static final String AWAIT_FLOW = "await";
    public static final String NEXT_FLOW = "next";
    public static final String SAVED_FLOW = "saved";
    public static final String AWAIT_APPROVAL_FLOW = "await_approval";

    /** Node 1: 4 route values + parse_error fallback. */
    public static final Map<String, String> SAFETY = Map.of(
            "continue", Nodes.RESUME_GATE,
            "clarify_safety", Nodes.AWAIT_USER,
            "pause_and_support", Nodes.PAUSE_AND_SUPPORT,
            "emergency_support", Nodes.EMERGENCY_SUPPORT,
            PARSE_ERROR_FLOW, Nodes.PARSE_ERROR);

    /** resume_gate: 10 resume_target values, END included. */
    public static final Map<String, String> RESUME_GATE = Map.ofEntries(
            Map.entry(Nodes.PROBLEM_IDENTIFICATION, Nodes.PROBLEM_IDENTIFICATION),
            Map.entry(Nodes.CONTEXT_EXTRACTION, Nodes.CONTEXT_EXTRACTION),
            Map.entry(Nodes.EVIDENCE_REVIEW, Nodes.EVIDENCE_REVIEW),
            Map.entry(Nodes.INTERVENTION_DECISION, Nodes.INTERVENTION_DECISION),
            Map.entry(Nodes.BEHAVIORAL_EXPERIMENT, Nodes.BEHAVIORAL_EXPERIMENT),
            Map.entry(Nodes.PRACTICAL_PROBLEM_SOLVING, Nodes.PRACTICAL_PROBLEM_SOLVING),
            Map.entry(Nodes.RESULT_EVALUATION, Nodes.RESULT_EVALUATION),
            Map.entry(Nodes.SESSION_SUMMARY, Nodes.SESSION_SUMMARY),
            Map.entry(Nodes.PERSIST_RECORD, Nodes.PERSIST_RECORD),
            Map.entry("END", StateGraph.END));

    /** Node 5: 6 intervention values + parse_error fallback. */
    public static final Map<String, String> INTERVENTION = Map.ofEntries(
            Map.entry("cognitive_review", Nodes.EVIDENCE_REVIEW),
            Map.entry("behavioral_experiment", Nodes.BEHAVIORAL_EXPERIMENT),
            Map.entry("practical_problem_solving", Nodes.PRACTICAL_PROBLEM_SOLVING),
            Map.entry("small_behavioral_step", Nodes.BEHAVIORAL_EXPERIMENT),
            Map.entry("observation_only", Nodes.SESSION_SUMMARY),
            Map.entry("professional_support", Nodes.PROFESSIONAL_SUPPORT),
            Map.entry(PARSE_ERROR_FLOW, Nodes.PARSE_ERROR));

    /** Node 7: 6 next_step values (incl. the added 完成本轮) + parse_error fallback. */
    public static final Map<String, String> RESULT = Map.ofEntries(
            Map.entry("保持当前理解", Nodes.SESSION_SUMMARY),
            Map.entry("修正理解", Nodes.EVIDENCE_REVIEW),
            Map.entry("继续收集证据", Nodes.EVIDENCE_REVIEW),
            Map.entry("调整行动", Nodes.INTERVENTION_DECISION),
            Map.entry("暂停", Nodes.SESSION_SUMMARY),
            Map.entry("完成本轮", Nodes.SESSION_SUMMARY),
            Map.entry(PARSE_ERROR_FLOW, Nodes.PARSE_ERROR));

    private static Map<String, String> collect(String next) {
        return Map.of(AWAIT_FLOW, Nodes.AWAIT_USER,
                NEXT_FLOW, next,
                PARSE_ERROR_FLOW, Nodes.PARSE_ERROR);
    }

    /** 6A / 6B always wait: either for plan approval or for the action outcome. */
    private static Map<String, String> collectAlwaysAwait() {
        return Map.of(AWAIT_FLOW, Nodes.AWAIT_USER,
                NEXT_FLOW, Nodes.AWAIT_USER,
                PARSE_ERROR_FLOW, Nodes.PARSE_ERROR);
    }

    public static final Map<String, String> PROBLEM = collect(Nodes.CONTEXT_EXTRACTION);
    public static final Map<String, String> CONTEXT = collect(Nodes.EVIDENCE_REVIEW);
    public static final Map<String, String> EVIDENCE = collect(Nodes.INTERVENTION_DECISION);
    public static final Map<String, String> BEHAVIOR = collectAlwaysAwait();
    public static final Map<String, String> PRACTICAL = collectAlwaysAwait();
    public static final Map<String, String> SUMMARY = collect(Nodes.PERSIST_RECORD);

    /**
     * persist_record: 2 values, no LLM. Unapproved records are not inserted; the graph
     * returns to await_user so the pending record can still be authorized next turn.
     */
    public static final Map<String, String> PERSIST = Map.of(
            SAVED_FLOW, StateGraph.END,
            AWAIT_APPROVAL_FLOW, Nodes.AWAIT_USER);

    /** source node -> its exhaustive flow mapping, used by V9. */
    public static final Map<String, Map<String, String>> ROUTE_MAPS = Map.ofEntries(
            Map.entry(Nodes.SAFETY_TRIAGE, SAFETY),
            Map.entry(Nodes.RESUME_GATE, RESUME_GATE),
            Map.entry(Nodes.PROBLEM_IDENTIFICATION, PROBLEM),
            Map.entry(Nodes.CONTEXT_EXTRACTION, CONTEXT),
            Map.entry(Nodes.EVIDENCE_REVIEW, EVIDENCE),
            Map.entry(Nodes.INTERVENTION_DECISION, INTERVENTION),
            Map.entry(Nodes.BEHAVIORAL_EXPERIMENT, BEHAVIOR),
            Map.entry(Nodes.PRACTICAL_PROBLEM_SOLVING, PRACTICAL),
            Map.entry(Nodes.RESULT_EVALUATION, RESULT),
            Map.entry(Nodes.SESSION_SUMMARY, SUMMARY),
            Map.entry(Nodes.PERSIST_RECORD, PERSIST));

    /** source node -> every flow value its node action is allowed to emit, used by V9. */
    public static final Map<String, Set<String>> EXPECTED_FLOWS = Map.ofEntries(
            Map.entry(Nodes.SAFETY_TRIAGE, Set.of("continue", "clarify_safety",
                    "pause_and_support", "emergency_support", PARSE_ERROR_FLOW)),
            Map.entry(Nodes.RESUME_GATE, RESUME_GATE.keySet()),
            Map.entry(Nodes.PROBLEM_IDENTIFICATION, Set.of(AWAIT_FLOW, NEXT_FLOW, PARSE_ERROR_FLOW)),
            Map.entry(Nodes.CONTEXT_EXTRACTION, Set.of(AWAIT_FLOW, NEXT_FLOW, PARSE_ERROR_FLOW)),
            Map.entry(Nodes.EVIDENCE_REVIEW, Set.of(AWAIT_FLOW, NEXT_FLOW, PARSE_ERROR_FLOW)),
            Map.entry(Nodes.INTERVENTION_DECISION, Set.of("cognitive_review",
                    "behavioral_experiment", "practical_problem_solving", "small_behavioral_step",
                    "observation_only", "professional_support", PARSE_ERROR_FLOW)),
            Map.entry(Nodes.BEHAVIORAL_EXPERIMENT, Set.of(AWAIT_FLOW, NEXT_FLOW, PARSE_ERROR_FLOW)),
            Map.entry(Nodes.PRACTICAL_PROBLEM_SOLVING, Set.of(AWAIT_FLOW, NEXT_FLOW, PARSE_ERROR_FLOW)),
            Map.entry(Nodes.RESULT_EVALUATION, Set.of("保持当前理解", "修正理解", "继续收集证据",
                    "调整行动", "暂停", "完成本轮", PARSE_ERROR_FLOW)),
            Map.entry(Nodes.SESSION_SUMMARY, Set.of(AWAIT_FLOW, NEXT_FLOW, PARSE_ERROR_FLOW)),
            Map.entry(Nodes.PERSIST_RECORD, Set.of(SAVED_FLOW, AWAIT_APPROVAL_FLOW)));

    public static final Set<String> SAFETY_ROUTES = Set.of("continue", "clarify_safety",
            "pause_and_support", "emergency_support");
    public static final Set<String> INTERVENTION_TYPES = Set.of("cognitive_review",
            "behavioral_experiment", "practical_problem_solving", "small_behavioral_step",
            "observation_only", "professional_support");
    public static final Set<String> NEXT_STEPS = Set.of("保持当前理解", "修正理解",
            "继续收集证据", "调整行动", "暂停", "完成本轮");
    public static final Set<String> SAFETY_STATUSES = Set.of("无明确信号", "需要澄清",
            "已提及自伤或自杀想法", "出现即时危险线索", "信息不足");
    public static final Set<String> RESUME_TARGETS = RESUME_GATE.keySet();
}
