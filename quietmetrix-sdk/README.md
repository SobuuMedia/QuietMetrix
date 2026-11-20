# QuietMetrix Core SDK

Version 0.8.2. Maven coordinates: `io.github.sobuumedia:quietmetrix-sdk:0.8.2`.

For JavaScript/TypeScript browser apps, install `@sobuumedia/quietmetrix-sdk@0.8.2`
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

See [Android integration documentation](https://github.com/SobuuMedia/QuietMetrix/blob/main/docs/sdk/android.md),
the [project overview](https://github.com/SobuuMedia/QuietMetrix#what-is-quietmetrix), and
[iOS documentation](https://github.com/SobuuMedia/QuietMetrix/blob/main/docs/sdk/ios.md).
Compose experiment wrappers use
`io.github.sobuumedia:quietmetrix-sdk-compose:0.8.2`.
