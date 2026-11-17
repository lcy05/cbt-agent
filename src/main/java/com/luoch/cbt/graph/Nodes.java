package com.luoch.cbt.graph;

/** Graph node identifiers. Kept as constants so route maps cannot contain typos. */
public final class Nodes {

    private Nodes() {
    }

    public static final String SAFETY_TRIAGE = "safety_triage";
    public static final String EMERGENCY_SUPPORT = "emergency_support";
    public static final String PAUSE_AND_SUPPORT = "pause_and_support";
    public static final String PROFESSIONAL_SUPPORT = "professional_support";
    public static final String PARSE_ERROR = "parse_error";
    public static final String RESUME_GATE = "resume_gate";
    public static final String PROBLEM_IDENTIFICATION = "problem_identification";
    public static final String CONTEXT_EXTRACTION = "context_extraction";
    public static final String EVIDENCE_REVIEW = "evidence_review";
    public static final String INTERVENTION_DECISION = "intervention_decision";
    public static final String BEHAVIORAL_EXPERIMENT = "behavioral_experiment";
    public static final String PRACTICAL_PROBLEM_SOLVING = "practical_problem_solving";
    public static final String RESULT_EVALUATION = "result_evaluation";
    public static final String SESSION_SUMMARY = "session_summary";
    public static final String AWAIT_USER = "await_user";
    public static final String PERSIST_RECORD = "persist_record";

    /** Nodes that must never be reachable while a safety route is active. */
    public static final java.util.Set<String> CBT_NODES = java.util.Set.of(
            PROBLEM_IDENTIFICATION, CONTEXT_EXTRACTION, EVIDENCE_REVIEW,
            INTERVENTION_DECISION, BEHAVIORAL_EXPERIMENT, PRACTICAL_PROBLEM_SOLVING,
            RESULT_EVALUATION, SESSION_SUMMARY, PERSIST_RECORD);
}
