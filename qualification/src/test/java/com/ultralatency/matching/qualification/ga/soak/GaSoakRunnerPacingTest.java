package com.ultralatency.matching.qualification.ga.soak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Direct deterministic tests for missed-slot accounting and non-burst pacing. */
class GaSoakRunnerPacingTest {

    private static final long START = 1_000_000_000L;
    private static final long SLOT = 5_000_000L;

    @Test
    void slightlyAfterSlotStartRemainsAnActiveOfferWindow() {
        final GaSoakRunner.PacingSchedule schedule =
                new GaSoakRunner.PacingSchedule(START, GaSoakMatrix.quick());

        assertEquals(0L, schedule.offerAt(START + 1L).ordinal());
        assertEquals(1L, schedule.actualOfferedCommands());
        assertEquals(0L, schedule.missedOfferOpportunities());
    }

    @Test
    void activeWindowIncludesMiddleAndEndMinusOneNanosecond() {
        final GaSoakRunner.PacingSchedule schedule =
                new GaSoakRunner.PacingSchedule(START, GaSoakMatrix.quick());

        assertEquals(0L, schedule.offerAt(START + SLOT / 2L).ordinal());
        assertEquals(1L, schedule.offerAt(START + SLOT + SLOT - 1L).ordinal());
        assertEquals(2L, schedule.actualOfferedCommands());
        assertEquals(0L, schedule.missedOfferOpportunities());
    }

    @Test
    void exactBoundaryMissesPreviousSlotAndOffersNewActiveSlot() {
        final GaSoakRunner.PacingSchedule schedule =
                new GaSoakRunner.PacingSchedule(START, GaSoakMatrix.quick());

        final GaSoakRunner.PacingOffer next = schedule.offerAt(START + SLOT);
        assertNotNull(next);
        assertEquals(1L, next.ordinal());
        assertEquals(1L, schedule.missedOfferOpportunities());
    }

    @Test
    void multipleElapsedSlotsOfferOnlyTheActiveSlot() {
        final GaSoakRunner.PacingSchedule schedule =
                new GaSoakRunner.PacingSchedule(START, GaSoakMatrix.quick());

        assertEquals(0L, schedule.offerAt(START).ordinal());
        final GaSoakRunner.PacingOffer active = schedule.offerAt(START + 17_000_000L);
        assertNotNull(active);
        assertEquals(3L, active.ordinal());
        assertEquals(2L, schedule.missedOfferOpportunities());
        assertEquals(2L, schedule.actualOfferedCommands());
    }

    @Test
    void verySlowFirstResponseLeavesCurrentSlotActive() {
        final GaSoakRunner.PacingSchedule schedule =
                new GaSoakRunner.PacingSchedule(START, GaSoakMatrix.quick());

        schedule.offerAt(START);
        final GaSoakRunner.PacingOffer next = schedule.offerAt(START + 1_800_000_000L);
        assertNotNull(next);
        assertEquals(360L, next.ordinal());
        assertEquals(359L, schedule.missedOfferOpportunities());
    }

    @Test
    void fullNominalScheduleAccountsEveryOpportunity() {
        final GaSoakRunner.PacingSchedule schedule =
                new GaSoakRunner.PacingSchedule(START, GaSoakMatrix.quick());
        for (int ordinal = 0; ordinal < 12_000; ordinal++) {
            final GaSoakRunner.PacingOffer offer =
                    schedule.offerAt(START + ordinal * SLOT);
            assertNotNull(offer, "nominal slot " + ordinal);
            assertEquals(ordinal, offer.ordinal());
        }
        schedule.accountMissedThrough(schedule.deadlineNanos());
        assertEquals(12_000L, schedule.nominalOfferOpportunities());
        assertEquals(12_000L, schedule.actualOfferedCommands());
        assertEquals(0L, schedule.missedOfferOpportunities());
    }
}
