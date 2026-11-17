package com.luoch.cbt.graph;

import java.util.Map;
import java.util.Set;

/** Declarative description of one strict-JSON LLM node. */
public record NodeSpec(
        String id,
        String template,
        Set<String> required,
        Map<String, Set<String>> enums,
        FlowMode mode,
        String nextTarget,
        String resumeTarget,
        boolean alwaysAwait) {

    public enum FlowMode {
        /** flow = route (4 values) */
        SAFETY,
        /** flow = intervention_type (6 values) */
        INTERVENTION,
        /** flow = next_step (6 values) */
        RESULT,
        /** flow = await | next, decided by ready_for_next / question */
        COLLECT
    }

    public static NodeSpec collect(String id, String template, Set<String> required,
                                   Map<String, Set<String>> enums, String nextTarget,
                                   String resumeTarget, boolean alwaysAwait) {
        return new NodeSpec(id, template, required, enums, FlowMode.COLLECT,
                nextTarget, resumeTarget, alwaysAwait);
    }

    public static NodeSpec routed(String id, String template, Set<String> required,
                                  Map<String, Set<String>> enums, FlowMode mode) {
        return new NodeSpec(id, template, required, enums, mode, null, null, false);
    }
}
