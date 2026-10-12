package com.merry.merryevents.test;

import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.config.MerryConfig;
import com.merry.merryevents.engine.ChaosDirector;
import com.merry.merryevents.engine.EventManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class ChaosDirectorDeckBehaviorTest {

    private ChaosDirector director;

    @BeforeEach
    void setUp() {
        EventManager.getInstance().resetRuntimeState();
        this.director = ChaosDirector.getInstance();
        this.director.resetState();
    }

    @Test
    @DisplayName("Fast retry timer calculation must be bounded and strictly positive")
    void testFastRetryCalculation() {
        this.director.initializeGlobalCountdown();
        int initialCountdown = this.director.getTicksUntilNextGlobalEvent();
        Assertions.assertTrue(initialCountdown > 0, "Initial countdown must be greater than zero");

        MerryConfig config = ConfigManager.get();
        int maxPossibleRetryTicks = (config.getMaxGlobalIntervalSeconds() * 20) / Math.max(1, config.getRetryDivisor());

        this.director.applyFastRetryGlobal();
        int retryCountdown = this.director.getTicksUntilNextGlobalEvent();

        Assertions.assertTrue(retryCountdown >= 20, "Fast retry ticks must be at least 20 ticks (1 second)");
        Assertions.assertTrue(retryCountdown <= maxPossibleRetryTicks,
                "Fast retry ticks must not exceed theoretical upper bound");
    }

    @Test
    @DisplayName("Personal countdown initialization must register unique timer per UUID")
    void testPersonalPlayerCountdownTracking() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        this.director.onPlayerJoin(player1);
        this.director.onPlayerJoin(player2);

        Assertions.assertTrue(this.director.getPlayerCountdowns().containsKey(player1));
        Assertions.assertTrue(this.director.getPlayerCountdowns().containsKey(player2));
        Assertions.assertTrue(this.director.getPlayerCountdowns().getInt(player1) > 0);
        Assertions.assertTrue(this.director.getPlayerCountdowns().getInt(player2) > 0);

        this.director.applyFastRetryPlayer(player1);
        Assertions.assertTrue(this.director.getPlayerCountdowns().getInt(player1) >= 20);
    }
}