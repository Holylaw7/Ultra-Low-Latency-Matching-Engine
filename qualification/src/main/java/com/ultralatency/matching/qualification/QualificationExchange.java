package com.ultralatency.matching.qualification;

import java.util.List;
import java.util.Objects;

/**
 * One complete Protocol v1 request/response exchange observed over TCP.
 *
 * @param requestId client request identifier
 * @param commandSequence applied engine command sequence
 * @param outcomeCode protocol command outcome code
 * @param matches ordered match observations
 * @param responseFrameCount number of response frames in this exchange
 * @param transcriptDigestHex digest of the exact ordered response frames
 */
public record QualificationExchange(
        long requestId,
        long commandSequence,
        int outcomeCode,
        List<QualificationMatch> matches,
        int responseFrameCount,
        String transcriptDigestHex) {

    /** Creates a validated immutable exchange observation. */
    public QualificationExchange {
        if (requestId <= 0 || commandSequence <= 0) {
            throw new IllegalArgumentException("exchange identities must be positive");
        }
        if (outcomeCode < 1 || outcomeCode > 3) {
            throw new IllegalArgumentException("unsupported command outcome code");
        }
        matches = List.copyOf(Objects.requireNonNull(matches, "matches"));
        if (responseFrameCount != matches.size() + 1) {
            throw new IllegalArgumentException("response frame count does not match matches");
        }
        Objects.requireNonNull(transcriptDigestHex, "transcriptDigestHex");
        if (!transcriptDigestHex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("transcriptDigestHex must be lowercase SHA-256");
        }
    }
}
