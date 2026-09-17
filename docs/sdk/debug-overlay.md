# Debug Overlay

`quietmetrix-sdk-debug` is a small, **optional** Compose Multiplatform module that renders a
panel showing the counter pipeline's most recent flush: every `metric{dims}` cell that was
sent, and whether the flush succeeded. It's meant for development, not production — mount it
behind a debug build flag, next to a `QuietMetrixConfig(debug = true)`.

It is a **separate Gradle artifact** from `quietmetrix-sdk` on purpose. The core SDK has zero
UI dependencies; pulling in Compose Multiplatform for every consumer — even ones who never use
this overlay — would be a real cost for a library whose whole pitch is being small and
unintrusive. A consumer who never adds this dependency pays nothing for it.

## Platform coverage

Available on **Android, iOS, JVM (desktop), and Web (wasmJs) only.** Compose Multiplatform has
no rendering backend for bare Kotlin/Native `linuxX64`/`macosArm64`/`mingwX64` (those are
native targets with no UI toolkit, distinct from the `jvm()` "desktop" target Compose Desktop
actually runs on) — that's a hard platform constraint, not a scoping choice. It's also
unavailable on `iosX64` (the Intel simulator): Compose Multiplatform 1.11.1 publishes no
artifacts for it at all.

None of the four supported platforms mount the overlay automatically — you add it to your own
Compose tree, the same as any other Composable.

## Installation

```kotlin
// build.gradle.kts, alongside the existing quietmetrix-sdk dependency
implementation("io.github.sobuumedia:quietmetrix-sdk-debug:0.1.0")
```

## Usage

```kotlin
import com.quietmetrix.analytics.debug.QuietMetrixDebugOverlay

@Composable
fun App() {
    Box {
        YourAppContent()
        if (BuildConfig.DEBUG) {
            QuietMetrixDebugOverlay(modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}
```

The overlay reads `QuietMetrixDebug.pending`/`QuietMetrixDebug.lastFlush` — both are `Flow`s
that only ever emit anything when `QuietMetrixConfig.debug == true`. With `debug = false`, the
overlay mounts harmlessly and shows "No flush yet" forever, at zero runtime cost: the
underlying state holder is never written to.

## Building your own overlay instead

You don't need this module at all to get the same data. `QuietMetrixDebug` (in the core
`quietmetrix-sdk` artifact, no Compose dependency) exposes the same information as plain
`Flow`s plus synchronous snapshot functions:

```kotlin
import com.quietmetrix.analytics.QuietMetrixDebug

QuietMetrixDebug.currentPending()     // List<QuietMetrixDebug.PendingCounter>, one-shot
QuietMetrixDebug.currentLastFlush()   // QuietMetrixDebug.FlushOutcome?, one-shot
QuietMetrixDebug.pending              // Flow<List<PendingCounter>>, for live updates
QuietMetrixDebug.lastFlush            // Flow<FlushOutcome?>, for live updates
```

This is the only path available on Linux/Windows/macOS-native and on `iosX64` — build a tiny
console/log-based debug view from these instead of a visual overlay.
