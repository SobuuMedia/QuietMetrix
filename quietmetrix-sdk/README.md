# QuietMetrix Core SDK

Version 0.7.0. Maven coordinates: `io.github.sobuumedia:quietmetrix-sdk:0.7.0`.

For JavaScript/TypeScript browser apps, install `@sobuumedia/quietmetrix-sdk@0.7.0`
from npm and use ES module imports. Compose/WASM apps use the Maven artifacts.

The SDK records aggregate counters on the device. It sends metric names, allowed
categorical dimensions, UTC days/hours, contribution counts, platform and SDK version.
It never sends a raw event stream, installation identifier, or session trail. Optional
country/language targeting uses app-supplied values; it does not infer country from locale.

Consent and analytics opt-out persist in platform storage. Android initialization must
supply an application Context. Browser builds use localStorage; storage failure disables
tracking. Native apps use SharedPreferences or NSUserDefaults; JVM uses a local store.
Set `trackingAllowedByDefault = false` and call `setCookieConsent(true)` after acceptance.
Revocation removes unsent data and returns experiment elements to their control view.

The aggregate outbox persists before transport. Lost responses retry the same receipt IDs;
schema-2 envelopes contain at most 512 items. Outbox retention is bounded, and unfinished
session duration is omitted after an OS kill. Visits are persisted at foreground start.
A process can still terminate before an asynchronous call executes; await recording where
an application needs a completed local-write guarantee.

See [integration documentation](../docs/sdk/android.md),
[metrics and privacy](../docs/sdk/metrics-and-privacy.md), and
[iOS artifacts](../docs/sdk/ios.md). Compose experiment wrappers use
`io.github.sobuumedia:quietmetrix-sdk-compose:0.7.0`.
