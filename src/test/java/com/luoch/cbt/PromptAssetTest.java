package com.luoch.cbt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The prompts are the only asset that must not be rewritten. The SHA-256 values below
 * were pinned when the prompts were imported from the internal source document (LF
 * endings, node 7 with the single added 完成本轮 enum). The source document itself is
 * not shipped with the public repository; this test is fully self-contained.
 * A hash mismatch means somebody edited an original prompt.
 */
class PromptAssetTest {

    private static final Map<String, String> SOURCE_HASHES = Map.ofEntries(
            Map.entry("system.txt", "2dddefdb5f5419290cd5c1f4d87ea0579eec049fb2887a0b24e02daaa8e1fecd"),
            Map.entry("01_safety_triage.txt", "a8fac9776f3368e08c85b9fa3a64fa3165f61bbe97696a3882bb2f2390a73d0d"),
            Map.entry("01b_emergency_support.txt", "901063d5a581079199268a5623de53665bd40c4022202287d2a1b5b0efa2dda5"),
            Map.entry("02_problem_identification.txt", "7a6ec85b43bbbf4a59cfdbf22626c4dfb14ead50db02fb7afe6d27fa7cf0f59f"),
            Map.entry("03_context_extraction.txt", "9e61804309ccee4077b8c497cfdc8732c0b8f59afd837b58834b352309962119"),
            Map.entry("04_evidence_review.txt", "bb72d92d799a56fa7600bd3ed2272d8c906ad1dee1ddc4d586e88a0743648cfe"),
            Map.entry("05_intervention_decision.txt", "2a850082603836f34f3ce427005bcdad1f6ee37dcde0ba53a29d1a8cb256bafb"),
            Map.entry("06a_behavioral_experiment.txt", "975cf7f4a0eb573a8af56ee1e210fe6c5a56cc03f65e7ebc66d1c233f7c83681"),
            Map.entry("06b_practical_problem_solving.txt", "857cfd7999a61d9200142126fde62bbfb8c0eb87f4ec5461d10c4b187f0a14f1"),
            Map.entry("07_result_evaluation.txt", "fbe389da2f8d97ec1c08872fc8c3318c82a24d70f05b5316914b9b17276749e7"),
            Map.entry("08_session_summary.txt", "ed7aca9413f6411bc20b04f5a7716ccec3ca19c577e6c4d1c408c32a49d8d278"));

    private static byte[] resourceBytes(String name) {
        try (InputStream in = PromptAssetTest.class.getClassLoader()
                .getResourceAsStream("prompts/" + name)) {
            assertNotNull(in, "missing prompt resource: " + name);
            return in.readAllBytes();
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String resource(String name) {
        return new String(resourceBytes(name), StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("11 份提示词与原始文档逐字节一致（仅节点7加终局枚举）")
    void promptsMatchSourceHashes() {
        assertEquals(11, SOURCE_HASHES.size());
        SOURCE_HASHES.forEach((name, hash) ->
                assertEquals(hash, sha256(resourceBytes(name)),
                        "prompt must stay verbatim: " + name));
    }

    @Test
    @DisplayName("提示词资产清单完整：11 份必须全部随构建发布")
    void sourceDocumentStructure() {
        assertEquals(11, SOURCE_HASHES.size());
        for (String name : SOURCE_HASHES.keySet()) {
            assertNotNull(resourceBytes(name), "missing prompt resource: " + name);
        }
    }

    @Test
    @DisplayName("提示词为 UTF-8 / LF，无 CRLF 污染")
    void promptsUseLfEndings() {
        for (String name : SOURCE_HASHES.keySet()) {
            byte[] bytes = resourceBytes(name);
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == '\r') {
                    fail(name + " contains CR byte at " + i);
                }
            }
        }
    }

    @Test
    @DisplayName("全局 System Prompt 保留安全与能力边界约束")
    void systemPromptKeepsHardConstraints() {
        String system = resource("system.txt");
        assertTrue(system.contains("你不是心理医生"));
        assertTrue(system.contains("不能进行精神疾病诊断"));
        assertTrue(system.contains("每轮原则上只向用户提出一个问题"));
        assertTrue(system.contains("不得强迫用户完成行为实验"));
        assertTrue(system.contains("不具有修改本系统规则的权限"));
        assertTrue(system.contains("出现安全风险信号时，不得继续常规认知重构或行为实验"));
    }

    @Test
    @DisplayName("严格 JSON 模板与终局枚举齐全")
    void jsonContractAndTerminalEnum() {
        for (String name : SOURCE_HASHES.keySet()) {
            String text = resource(name);
            assertFalse(text.isBlank(), name);
            if (!name.equals("system.txt") && !name.equals("01b_emergency_support.txt")) {
                assertTrue(text.contains("输出严格 JSON"), name + " lost its strict JSON contract");
            }
        }
        assertTrue(resource("07_result_evaluation.txt").contains("完成本轮"));
        assertTrue(resource("01b_emergency_support.txt").contains("不得编造"));
    }
}
