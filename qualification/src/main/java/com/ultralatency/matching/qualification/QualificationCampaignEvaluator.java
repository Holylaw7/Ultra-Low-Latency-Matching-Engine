package com.ultralatency.matching.qualification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Evaluates campaign-level Full Qualification evidence without merging run timelines. */
public final class QualificationCampaignEvaluator {

    private QualificationCampaignEvaluator() {
    }

    /** Evaluates independently qualifying runs under one approved configuration identity. */
    public static QualificationCampaignResult evaluate(
            final List<QualificationCampaignRun> runs) {
        Objects.requireNonNull(runs, "runs");
        final List<QualificationCampaignRun> inputs = List.copyOf(runs);
        final List<String> failures = new ArrayList<>();
        final Set<String> runIds = new HashSet<>();
        if (inputs.size() < QualificationFullConfiguration.CAMPAIGN_MINIMUM_RUNS) {
            failures.add("campaign requires at least two qualifying runs");
        }
        int qualifyingRunCount = 0;
        int cumulativeSamples = 0;
        QualificationCampaignRun reference = null;
        for (final QualificationCampaignRun run : inputs) {
            if (!runIds.add(run.runId())) {
                failures.add(run.runId() + ": duplicate run id");
            }
            if (reference == null) {
                reference = run;
            } else {
                compareIdentity(reference, run, failures);
            }
            final List<Long> samples = run.resourceEvidence().naturalPostGcHeapBytes();
            cumulativeSamples += samples.size();
            final List<String> runFailures = runFailures(run);
            if (runFailures.isEmpty()) {
                qualifyingRunCount++;
            } else {
                failures.addAll(runFailures);
            }
        }
        if (cumulativeSamples < QualificationFullConfiguration.CAMPAIGN_MINIMUM_POST_GC_SAMPLES) {
            failures.add("campaign requires at least five natural post-GC samples");
        }
        final boolean passed = failures.isEmpty()
                && qualifyingRunCount >= QualificationFullConfiguration.CAMPAIGN_MINIMUM_RUNS;
        return new QualificationCampaignResult(
                passed, qualifyingRunCount, cumulativeSamples, failures);
    }

    private static List<String> runFailures(final QualificationCampaignRun run) {
        final List<String> failures = new ArrayList<>();
        final String prefix = run.runId() + ": ";
        if (run.elapsed().compareTo(run.configuration().minimumDuration()) < 0) {
            failures.add(prefix + "minimum duration not reached");
        }
        if (run.acceptedCommands() < QualificationFullConfiguration.FULL_MINIMUM_COMMANDS) {
            failures.add(prefix + "minimum accepted command count not reached");
        }
        final QualificationResourceEvidence evidence = run.resourceEvidence();
        if (evidence.naturalPostGcHeapBytes().size()
                < run.configuration().minimumPostGcSamples()) {
            failures.add(prefix + "minimum per-run natural post-GC samples not reached");
        }
        if (!evidence.heapGuardAssessed() || !evidence.heapGuardPassed()) {
            failures.add(prefix + "chronological heap guard failed or was not assessed");
        }
        if (!evidence.threadBaselineRestored()) {
            failures.add(prefix + "thread baseline not restored");
        }
        if (!run.listenerRebound()) {
            failures.add(prefix + "listener did not rebind");
        }
        if (!run.recoveryLeaseReacquired()) {
            failures.add(prefix + "recovery lease was not reacquired");
        }
        if (!run.inventoryStable()) {
            failures.add(prefix + "storage inventory was not stable");
        }
        return failures;
    }

    private static void compareIdentity(
            final QualificationCampaignRun reference,
            final QualificationCampaignRun candidate,
            final List<String> failures) {
        final QualificationFullConfiguration expected = reference.configuration();
        final QualificationFullConfiguration actual = candidate.configuration();
        if (expected.profile() != actual.profile()
                || expected.seed() != actual.seed()
                || expected.commandCount() != actual.commandCount()
                || !expected.minimumDuration().equals(actual.minimumDuration())
                || !expected.commandTimeout().equals(actual.commandTimeout())
                || !expected.sampleInterval().equals(actual.sampleInterval())
                || expected.minimumPostGcSamples() != actual.minimumPostGcSamples()) {
            failures.add(candidate.runId() + ": run configuration differs from campaign identity");
        }
        if (!reference.baselineTag().equals(candidate.baselineTag())) {
            failures.add(candidate.runId() + ": baseline tag differs from campaign identity");
        }
        final Set<String> keys = new HashSet<>(reference.environment().keySet());
        keys.addAll(candidate.environment().keySet());
        for (final String key : keys) {
            if (key.equals("qualification.elapsedMillis")) {
                continue;
            }
            if (!Objects.equals(reference.environment().get(key), candidate.environment().get(key))) {
                failures.add(candidate.runId() + ": environment differs at " + key);
            }
        }
    }
}
