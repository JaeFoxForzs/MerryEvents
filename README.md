# MerryEvents

A modern, high-performance, event-driven chaos engine for **Minecraft 1.21.1 (Fabric)**.

The mod orchestrates timed, randomized game-altering phenomena split into **Global** (server-wide) and **Unique** (per-player) events. Built from the ground up on modern Minecraft 1.21.1 architectural paradigms: **Data Components**, **Typed Payload Networking**, and zero-allocation tick loops.

---

## ⚡ Architectural Advantages (Why 1.21.1?)

- **Immutable Data Components**: Fully migrated from legacy NBT compound tags to modern `DataComponentType` APIs (`DataComponents.DAMAGE`, custom item modifiers). Zero arbitrary string-tag lookups, guaranteed type-safety, and seamless integration with modern 3D model predicates.
- **Modern Payload Networking**: Uses Minecraft 1.21's `CustomPayload` architecture paired with modern Java 21 `record` declarations and zero-boilerplate `PacketCodec` serialization. No raw byte stream desyncs.
- **Strict Logical Isolation**: Enforced via `splitEnvironmentSourceSets()`:
  - `src/main`: Common & Server-Authoritative domain (Event scheduler, anti-conflict matrix, ticking state machines, network transmitters).
  - `src/client`: Client-only domain (Custom post-processing shaders, camera manipulation mixins, dynamic skybox hooks, HUD occlusion controllers).
- **Deterministic Anti-Conflict Matrix**: Events belong to 8 strict categories. The state manager guarantees that concurrent events cannot claim the same architectural domain, preventing game-breaking rendering/gameplay softlocks.
- **Zero-Allocation Hot Paths**: Server tick listeners (`ServerTickEvents.END_SERVER_TICK`) and entity damage hooks are strictly free of stream pipelines, closure captures, and object allocation overhead.

---

## 🏷 Event Categories

Both Global and Unique events are partitioned across 8 universal categories:

| Category    | Domain                          | Example Mechanics                                               |
| :---------- | :------------------------------ | :-------------------------------------------------------------- |
| `WEATHER`   | Atmosphere & Falling Hazards    | Acid Rain, Anvil Showers, Fire Storm, Orbital Strikes           |
| `PHYSICS`   | Kinematics & Spatial Movement   | Moon Gravity, Slime Bounce, Axis-Locked Camera, Auto-Jump       |
| `WORLD`     | Environmental Mutation & Blocks | False Water (acts as Lava), Boiled Fluids, X-Ray Vision         |
| `MOBS`      | Spawning, Scaling & Entity AI   | Baby Horde, Creeper Death Spawns, Personal Stalker Bunny        |
| `STATUS`    | Player Attributes & Needs       | Fragility (3 HP cap), Fall Multiplier, Hydrophile (Water Diet)  |
| `INVENTORY` | Economies, Crafts & Drops       | Resource Deflation (x4 Craft Cost), Leaky Pockets, Diamond Rush |
| `VISUAL`    | Post-Processing & Rendering     | Ghost Interface (Hidden GUI), Cucumber Clouds, Inverted Shaders |
| `SOUNDS`    | Auditory Overrides & Illusions  | Phantom Creeper Hiss, Anvil Drops, Pitch Shift, Auditory Horror |

---

## 💻 In-Game Commands (CLI)

The mod includes permission-level 2 operator commands equipped with Brigadier autocompletion:

- `/merry start <eventId> [durationSeconds] [targetPlayer]`  
  Starts an event. Global events target the entire server; unique events target the sender (or specified player). Default duration: `30` seconds.
- `/merry stop <eventId>`  
  Stops an active global or personal event and safely triggers state rollback.
- `/merry status`  
  Displays active server events and current category bitmasks.

---

## 🛠 Prerequisites & Toolchain

- **Build Tooling Environment**: **JDK 25** (Required by Fabric Loom 1.18+ / Gradle 9+ FFM native runtime).
- **Target Bytecode**: **Java 21** (`it.options.release = 21`).
- **Minecraft**: `1.21.1`
- **Mappings**: Yarn Mappings (`net.fabricmc:yarn:1.21.1+build.3:v2`).

### Building & Running

```bash
# Build production artifact (build/libs/merryevents-1.0.0.jar)
./gradlew build

# Launch client development instance
./gradlew runClient

# Launch dedicated server development instance
./gradlew runServer
```

---

## 📜 Engineering Hygiene

1. **Dedicated Server Purity**: Never reference client classes (`net.minecraft.client.*`) outside `src/client`. Dedicated server crashes due to client class leaks are completely prohibited.
2. **Mixin Restraint**: Prefer `@Inject` and `@WrapOperation` over destructive `@Redirect` or `@Overwrite`. Always encapsulate synthetic mixin members with `@Unique`.
3. **Idempotent Teardown**: Every active event must implement an explicit, failure-proof `onStop()` hook to guarantee world/player states revert seamlessly upon expiration.
