package com.merry.merryevents.test;

import com.merry.merryevents.api.EventContext;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class EventContextSerializationTest {

    @Test
    @DisplayName("Verify EventContext NBT round-trip preservation across all supported primitives")
    public void testEventContextNbtRoundTrip() {
        EventContext original = new EventContext();
        UUID entityUuid1 = UUID.randomUUID();
        UUID entityUuid2 = UUID.randomUUID();
        UUID stateUuid = UUID.randomUUID();

        original.set("intKey", 42);
        original.set("doubleKey", 123.456);
        original.set("floatKey", 7.89F);
        original.set("stringKey", "MerryEventsTest");
        original.set("boolKey", true);
        original.set("uuidKey", stateUuid);

        original.trackEntity(entityUuid1);
        original.trackEntity(entityUuid2);
        original.requestStop();

        NbtCompound serialized = original.toNbt();
        Assertions.assertNotNull(serialized);

        EventContext restored = new EventContext();
        restored.fromNbt(serialized);

        Assertions.assertEquals(42, restored.getInt("intKey", 0));
        Assertions.assertEquals(123.456, restored.getDouble("doubleKey", 0.0), 0.0001);
        Assertions.assertEquals(7.89F, restored.getFloat("floatKey", 0.0F), 0.0001F);
        Assertions.assertEquals("MerryEventsTest", restored.getString("stringKey", ""));
        Assertions.assertTrue(restored.getBoolean("boolKey", false));
        Assertions.assertEquals(stateUuid, restored.getOrDefault("uuidKey", null));
        Assertions.assertTrue(restored.isStopRequested());
    }

    @Test
    @DisplayName("Verify atomic flag claiming idempotency in EventContext")
    public void testAtomicFlagClaiming() {
        EventContext context = new EventContext();

        boolean firstClaim = context.setFlag("test_flag");
        boolean secondClaim = context.setFlag("test_flag");

        Assertions.assertTrue(firstClaim);
        Assertions.assertFalse(secondClaim);
        Assertions.assertTrue(context.getBoolean("test_flag", false));
    }
}