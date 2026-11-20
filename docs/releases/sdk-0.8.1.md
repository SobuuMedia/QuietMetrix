# QuietMetrix Web SDK 0.8.1

This release updates only the npm package `@sobuumedia/quietmetrix-sdk`. Maven and iOS
artifacts remain at 0.8.0.

Install with:

```bash
npm install @sobuumedia/quietmetrix-sdk@0.8.1
```

The web SDK reports its platform as `web`, accepts an optional host-supplied ISO country
code, and classifies browser traffic as detected bot, likely human, or unclassified using
browser automation signals and user-agent patterns. These classifications are heuristic.
Browser background and page lifecycle events now close foreground visits so completed visit
durations can be reported. An abruptly terminated page may still have an unfinished visit.
