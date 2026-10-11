# MerryEvents

A modern, high-performance, event-driven chaos engine for **Minecraft 1.21.1 (Fabric)**.

The mod orchestrates timed, randomized game-altering phenomena split into **Global** (server-wide) and **Personal** (targeting one player) events. Built from the ground up on modern Minecraft 1.21.1 architectural paradigms: **Data Components**, **Typed Payload Networking**, and server-authoritative event logic.

---

## ⚡ Architectural Advantages (Why 1.21.1?)

- **Immutable Data Components**: Fully migrated from legacy NBT compound tags to modern `DataComponentType` APIs (`DataComponents.DAMAGE`, custom item modifiers). Zero arbitrary string-tag lookups, guaranteed type-safety, and seamless integration with modern 3D model predicates.
- **Modern Payload Networking**: Uses Minecraft 1.21's `CustomPayload` architecture paired with modern Java 21 `record` declarations and zero-boilerplate `PacketCodec` serialization. No raw byte stream desyncs.
- **Strict Logical Isolation**: Enforced via `splitEnvironmentSourceSets()`:
  - `src/main`: Common & Server-Authoritative domain (Event scheduler, anti-conflict matrix, ticking state machines, network transmitters).
  - `src/client`: Client-only domain (Custom post-processing shaders, camera manipulation mixins, dynamic skybox hooks, HUD occlusion controllers).
- **Deterministic Anti-Conflict Matrix**: The state manager prevents events in the same category from running together in the same scope.
- **Weighted Event Director**: Automatic events are selected from a shuffled, weighted queue. Event weights affect how often an event appears; events whose category is occupied wait for a later opportunity.

---

## 🏷 Event Categories

Global and Personal events use the same 12 categories:

| Category    | Domain                                           | Example Events                                |
| :---------- | :----------------------------------------------- | :-------------------------------------------- |
| `WEATHER`   | Weather, precipitation, and weather hazards      | Acid Rain, Rust Rain, Anvil Rain, Fire Storms |
| `SKY`       | Sun, moon, clouds, and sky appearance            | —                                             |
| `PHYSICS`   | Movement, gravity, speed, and collisions         | Moon Gravity                                  |
| `WORLD`     | Blocks, water, and lava mechanics                | False Water, Floor Hole, Wandering Blocks     |
| `MOBS`      | Mob spawning and behavior                        | Exploding Mobs, Personal Killer               |
| `STATUS`    | Health, hunger, effects, and entity states       | Fragility                                     |
| `INVENTORY` | Items, recipes, experience, slots, and trading   | Diamond Rush                                  |
| `VISION`    | Player perception, darkness, and screen filters  | Dense Darkness                                |
| `VISUAL`    | World and object textures, models, and rendering | —                                             |
| `UI`        | HUD, menus, and GUI elements                     | Ghost GUI                                     |
| `SOUNDS`    | Sound effects                                    | Paranoia Sounds                               |
| `CAMERA`    | Camera direction and position                    | Stiff Neck                                    |

### Event Director

The director starts events automatically at randomized intervals. It selects from a shuffled queue whose entries are weighted by each event's configuration; the queue count is the number of remaining weighted entries, so an event may appear more than once. The `/merry director status` command shows whether automatic starts are enabled, the time until the next event, and how many entries remain in the current queue. If an event's category is already occupied, that event is skipped for now and remains eligible later.

The weather events include several distinct bombardments: **Fire Storm** launches small fireballs, **Ghast Fire Storm** sends explosive ghast fireballs from high above, and **Orbital Strike** calls down warned strikes. Their projectiles or strikes are limited by randomized per-event quotas.

---

## 💻 In-Game Commands (CLI)

The mod includes permission-level 2 operator commands equipped with Brigadier autocompletion:

- `/merry start <eventId> [durationSeconds] [targetPlayer]`  
  Starts an event. Global events target the entire server; Personal events target the sender (or specified player). Default duration: `30` seconds.
- `/merry stop <eventId>`  
  Stops an active global or personal event and safely triggers state rollback.
- `/merry status`  
  Displays active global and personal events, category availability per scope, and the director queue status.
- `/merry events`
  Lists all registered events with their IDs, categories, scope, and auto-start setting.
- `/merry director status`
  Displays automatic event scheduling status, the next-event countdown, and the number of entries left in the queue.

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

### Localization Validation

`./gradlew validateAndFormatLocalization` (also run by `check` and `processResources`) validates locale JSON, duplicate and missing/extra keys, unused translation keys, missing keys referenced by Java, hardcoded text passed to `Text.literal`/`Text.of`, and mismatched format placeholders. It also recognizes event and category keys generated by the APIs.
