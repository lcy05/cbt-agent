package com.luoch.cbt.graph;

import java.util.Set;

/** Every state key is declared here; no bare string keys elsewhere. */
public final class StateKeys {

    private StateKeys() {
    }

    public static final String MESSAGES = "messages";
    public static final String USER_ANSWER = "user_answer";
    public static final String NODE = "node";
    public static final String FLOW = "flow";
    public static final String RESUME_TARGET = "resume_target";
    public static final String PENDING_QUESTION = "pending_question";
    public static final String USER_RESPONSE = "user_response";

    public static final String SAFETY_STATUS = "safety_status";
    public static final String SAFETY_ROUTE = "safety_route";
    public static final String SAFETY_REASON = "safety_reason";

    public static final String TARGET_PROBLEM = "target_problem";
    public static final String INTERVENTION_TYPE = "intervention_type";
    public static final String NEXT_STEP = "next_step";
    public static final String SESSION_SUMMARY = "session_summary";

    /** One-shot user authorization for saving the pending record. Consumed by persist_record. */
    public static final String RECORD_APPROVED = "record_approved";

    /** Only the user (via updateState) may write these. LLM output is filtered against them. */
    public static final Set<String> PROTECTED = Set.of(
            "balanced_thought_confirmed",
            "user_approved",
            RECORD_APPROVED,
            "action_plan",
            USER_ANSWER,
            RESUME_TARGET);

    /** Keys registered with an explicit KeyStrategy; anything else falls back to REPLACE. */
    public static final Set<String> ALL = Set.of(
            MESSAGES, USER_ANSWER, NODE, FLOW, RESUME_TARGET, PENDING_QUESTION, USER_RESPONSE,
            SAFETY_STATUS, SAFETY_ROUTE, SAFETY_REASON,
            TARGET_PROBLEM, INTERVENTION_TYPE, NEXT_STEP, SESSION_SUMMARY,
            "problem_type", "event_available", "route", "reason_summary", "question",
            "situation", "automatic_thought", "emotions", "physical_responses", "behaviors",
            "missing_information", "ready_for_next",
            "target_thought", "evidence_for", "evidence_against", "other_explanations",
            "uncertainties", "balanced_thought_candidate", "user_acceptance",
            "selection_reason", "target", "safety_considerations",
            "prediction", "confidence_before", "experiment_action", "observation_items",
            "evaluation_criteria", "safety_check", "user_approval",
            "problem_definition", "controllable_factors", "influenceable_factors",
            "uncontrollable_factors", "options", "selected_action",
            "actual_action", "actual_result", "prediction_comparison", "new_information",
            "remaining_uncertainties", "emotion_after",
            "confirmed_facts", "user_reported_thoughts", "user_reported_emotions",
            "evidence_review", "accepted_alternative_thought", "intervention",
            "action_result", "unresolved_issues", "confidence_of_record",
            "balanced_thought_confirmed", "user_approved", RECORD_APPROVED, "action_plan");
}
