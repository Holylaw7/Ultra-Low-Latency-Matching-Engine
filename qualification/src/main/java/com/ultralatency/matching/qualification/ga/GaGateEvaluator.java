package com.ultralatency.matching.qualification.ga;

import java.util.Map;
import java.util.Objects;

/** Deterministic semantic evaluation for GA gate and campaign evidence. */
public final class GaGateEvaluator {

    private GaGateEvaluator() {
    }

    /** Result of evaluating one gate document. */
    public record GateDecision(boolean passed, String outcome, String blocker) {
    }

    /** Result of evaluating one campaign summary document. */
    public record CampaignDecision(
            boolean passed,
            String outcome,
            int requiredRunCount,
            int validRunCount,
            int observedRunCount,
            boolean configurationIdentityEqual) {
    }

    /** Validates and evaluates a gate field map. */
    public static GateDecision evaluateGate(final Map<String, String> fields) {
        final Map<String, String> validated = GaEvidenceCodec.decode(
                GaEvidenceCodec.Schema.GATE, GaEvidenceCodec.encode(
                        GaEvidenceCodec.Schema.GATE, Objects.requireNonNull(fields, "fields")));
        final int criterionCount = integer(validated, "criterion.count");
        boolean criteriaPass = true;
        for (int index = 1; index <= criterionCount; index++) {
            if (!"PASS".equals(validated.get(
                    String.format("criterion.%04d.result", index)))) {
                criteriaPass = false;
                break;
            }
        }
        final String outcome = validated.get("evidence.outcome");
        final String blocker = validated.get("blocker.classification");
        return new GateDecision("PASS".equals(outcome) && "NONE".equals(blocker)
                && criteriaPass, outcome, blocker);
    }

    /** Validates and evaluates canonical gate bytes. */
    public static GateDecision evaluateGate(final byte[] bytes) {
        return evaluateGate(GaEvidenceCodec.decode(GaEvidenceCodec.Schema.GATE, bytes));
    }

    /** Validates and evaluates a campaign summary field map. */
    public static CampaignDecision evaluateCampaign(final Map<String, String> fields) {
        final Map<String, String> validated = GaEvidenceCodec.decode(
                GaEvidenceCodec.Schema.CAMPAIGN, GaEvidenceCodec.encode(
                        GaEvidenceCodec.Schema.CAMPAIGN,
                        Objects.requireNonNull(fields, "fields")));
        final int required = integer(validated, "campaign.requiredRunCount");
        final int valid = integer(validated, "campaign.validRunCount");
        final int observed = integer(validated, "run.count");
        final boolean identityEqual = "true".equals(
                validated.get("campaign.configurationIdentityEqual"));
        boolean allPass = true;
        for (int index = 1; index <= observed; index++) {
            if (!"PASS".equals(validated.get(
                    String.format("run.%04d.outcome", index)))) {
                allPass = false;
                break;
            }
        }
        final boolean passed = "PASS".equals(validated.get("campaign.outcome"))
                && identityEqual && observed == required && valid == required && allPass;
        return new CampaignDecision(passed, validated.get("campaign.outcome"), required,
                valid, observed, identityEqual);
    }

    /** Validates and evaluates canonical campaign bytes. */
    public static CampaignDecision evaluateCampaign(final byte[] bytes) {
        return evaluateCampaign(GaEvidenceCodec.decode(GaEvidenceCodec.Schema.CAMPAIGN, bytes));
    }

    private static int integer(final Map<String, String> fields, final String key) {
        return Integer.parseInt(fields.get(key));
    }
}
