package com.rikdevos.energy.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit checks for {@link Battery#equivalentFullCycles()}. Lives in the same package
 * so the test can write the mutable {@code totalCharged} / {@code totalDischarged}
 * fields directly without going through {@link BatterySimulator} — keeps the test
 * focused on the cycle-counting formula, not the simulation loop.
 */
class BatteryTest {

    // 10 kWh nameplate, 100%/0% bounds → usable = 10 kWh.
    private Battery fullWindowBattery() {
        return new Battery(10, 100, 0, 5, 5, 0.9, 0);
    }

    @Test
    void symmetricCycleCount() {
        Battery b = fullWindowBattery();
        b.totalCharged = 100;     // 10 full charges
        b.totalDischarged = 90;   // 9 full discharges (post-RTE)

        // (100 + 90) / (2 * 10) = 9.5
        assertThat(b.equivalentFullCycles()).isEqualTo(9.5);
    }

    @Test
    void zeroThroughputIsZeroCycles() {
        assertThat(fullWindowBattery().equivalentFullCycles()).isZero();
    }

    @Test
    void oneFullChargePlusOneFullDischargeIsOneCycle() {
        Battery b = fullWindowBattery();
        b.totalCharged = 10;
        b.totalDischarged = 10;

        assertThat(b.equivalentFullCycles()).isEqualTo(1.0);
    }

    @Test
    void usesUsableWindowNotNameplate() {
        // 10 kWh nameplate, 90%/10% bounds → usable = 8 kWh.
        Battery b = new Battery(10, 90, 10, 5, 5, 0.9, 0);
        b.totalCharged = 80;
        b.totalDischarged = 80;

        // (80 + 80) / (2 * 8) = 10 — would be 8 if the formula used maxCapacity.
        assertThat(b.equivalentFullCycles()).isEqualTo(10.0);
    }

    @Test
    void throwsWhenUsableCapacityIsZero() {
        Battery b = new Battery(0, 100, 0, 5, 5, 0.9, 0);

        assertThatThrownBy(b::equivalentFullCycles)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0");
    }
}
