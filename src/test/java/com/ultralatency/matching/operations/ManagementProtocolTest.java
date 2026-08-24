package com.ultralatency.matching.operations;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.app.RuntimeFailureCode;
import com.ultralatency.matching.app.RuntimeLifecycleState;
import com.ultralatency.matching.app.RuntimeStatusSnapshot;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ManagementProtocolTest {

    @Test
    void decodesOnlyOneBoundedAsciiCommandLine() {
        assertEquals(
                ManagementProtocol.Request.STATUS,
                ManagementProtocol.decode("STATUS\n".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> ManagementProtocol.decode(
                "STATUS".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> ManagementProtocol.decode(
                "STATUS\nLIVE\n".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> ManagementProtocol.decode(
                "status\n".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> ManagementProtocol.decode(
                new byte[] {'L', 'I', 'V', 'E', '\r', '\n'}));
    }

    @Test
    void rendersCanonicalStatusAndMetricsFieldOrder() {
        final RuntimeStatusSnapshot status = new RuntimeStatusSnapshot(
                1,
                RuntimeLifecycleState.READY,
                true,
                true,
                RuntimeFailureCode.NONE,
                true,
                "PURE_WAL",
                7,
                0,
                12);

        final String statusText = new String(ManagementProtocol.encode(
                ManagementProtocol.Request.STATUS, status, 2, 1), StandardCharsets.UTF_8);
        assertEquals(
                "{\"schemaVersion\":1,\"state\":\"READY\",\"live\":true,"
                        + "\"ready\":true,\"failureCode\":\"NONE\",\"protocolBound\":true,"
                        + "\"recoveryMode\":\"PURE_WAL\",\"acceptedCommands\":7,"
                        + "\"terminalFailures\":0,\"uptimeMillis\":12}\n",
                statusText);

        final byte[] metrics = ManagementProtocol.encode(
                ManagementProtocol.Request.METRICS, status, 2, 1);
        assertTrue(metrics.length <= ManagementProtocol.MAX_RESPONSE_BYTES);
        assertTrue(new String(metrics, StandardCharsets.UTF_8).endsWith(
                "\"managementRequests\":2,\"managementRejected\":1}\n"));
        assertArrayEquals(
                "{\"schemaVersion\":1,\"live\":true}\n".getBytes(StandardCharsets.UTF_8),
                ManagementProtocol.encode(ManagementProtocol.Request.LIVE, status, 0, 0));
    }

    @Test
    void invalidResponseDoesNotEchoInput() {
        final String response = new String(
                ManagementProtocol.invalidResponse(), StandardCharsets.UTF_8);
        assertEquals("{\"schemaVersion\":1,\"error\":\"INVALID_REQUEST\"}\n", response);
        assertTrue(!response.contains("STATUS"));
    }
}
