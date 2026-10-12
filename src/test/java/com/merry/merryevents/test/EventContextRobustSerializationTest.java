package com.merry.merryevents.test;

import com.merry.merryevents.api.EventContext;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EventContextRobustSerializationTest {

    @Test
    @DisplayName("Verify EventContext serializes primitives, collections, UUIDs, and nested compounds")
    void testEventContextCompleteRoundtrip() {
        EventContext context = new EventContext();

        // 1. Populate primitives
        context.set("speed_multiplier", 4.5F);
        context.set("breaker_id", 100500);
        context.set("is_active", true);
        context.set("owner_tag", "Tester");
        context.set("target_uuid", UUID.fromString("12345678-1234-1234-1234-123456789abc"));

        // 2. Populate collections and compounds
        context.setStringList("forbidden_blocks", List.of("minecraft:stone", "minecraft:dirt", "minecraft:bedrock"));

        NbtCompound nestedComp = new NbtCompound();
        nestedComp.putInt("inner_key", 42);
        context.setCompound("custom_state", nestedComp);

        // 3. Track entities & stop request
        UUID trackedEntity = UUID.randomUUID();
        context.trackEntity(trackedEntity);
        context.requestStop();

        // 4. Serialize to NBT
        NbtCompound serialized = context.toNbt();
        Assertions.assertNotNull(serialized, "Serialized NbtCompound must not be null");

        // 5. Deserialize into a clean context
        EventContext restored = new EventContext();
        restored.fromNbt(serialized);

        // 6. Assert all fields
        Assertions.assertEquals(4.5F, restored.getFloat("speed_multiplier", 0.0F), 0.001F);
        Assertions.assertEquals(100500, restored.getInt("breaker_id", 0));
        Assertions.assertTrue(restored.getBoolean("is_active", false));
        Assertions.assertEquals("Tester", restored.getString("owner_tag", ""));
        Assertions.assertEquals(UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                restored.getOrDefault("target_uuid", null));

        List<String> restoredList = restored.getStringList("forbidden_blocks");
        Assertions.assertEquals(3, restoredList.size());
        Assertions.assertTrue(restoredList.contains("minecraft:stone"));
        Assertions.assertTrue(restoredList.contains("minecraft:dirt"));
        Assertions.assertTrue(restoredList.contains("minecraft:bedrock"));

        Set<String> restoredSet = restored.getStringSet("forbidden_blocks");
        Assertions.assertEquals(3, restoredSet.size());
        Assertions.assertTrue(restoredSet.contains("minecraft:bedrock"));

        NbtCompound restoredComp = restored.getCompound("custom_state");
        Assertions.assertNotNull(restoredComp);
        Assertions.assertEquals(42, restoredComp.getInt("inner_key"));

        Assertions.assertTrue(restored.getTrackedEntities().contains(trackedEntity));
        Assertions.assertTrue(restored.isStopRequested());
    }
}