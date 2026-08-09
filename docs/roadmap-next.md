# Plan: multi-anchor tracking, other anchor types, and the rest

Investigation only — no code written. Everything below is checked against the ARCore documentation
or against the code as it stands, and where I could not confirm something I say so.

---

## 1. Multiple augmented images, and whether they improve tracking

### Short answer

Yes, and more than you might expect — but the benefit is to **display accuracy over a large area**,
not to the measurements themselves. A shell is solved in whatever frame its views were taken in;
switching anchors afterwards cannot make a bad solve good. What it fixes is the shell being drawn
40 cm from the object because you walked to the far end of a workshop.

### Why it helps

An augmented image's pose error grows with distance and with viewing angle — the marker occupies
fewer pixels, so the same pixel error is a larger angular error. At five metres, a 200 mm marker is
a small patch of the frame. At one metre it is not. If two markers are up, the one you are standing
next to gives a far better frame than the one across the room, and everything drawn in it lands
closer to where it belongs.

There is also a hard limit being lifted: one marker can only serve the area it tracks well from.
Several markers cover a building.

### The real problem: relating the frames

This is the part that needs designing properly. If object A is stored against marker 1 and object B
against marker 2, then standing at marker 2 you can draw B — but to draw A you need the transform
**marker 1 → marker 2**, and marker 1 is not in shot.

You cannot compute that on demand. You have to have recorded it earlier, when both were visible in
the same frame.

**Proposal: an anchor graph.**

- **Nodes** are anchors.
- **Edges** are relative poses, `anchorAInB`, recorded whenever two anchors were tracked in the same
  frame. ARCore gives both poses in the session frame, so the relative pose falls out of one
  composition — the same `inverse() * other` already tested in `cameraInMarkerFrame`.
- **Drawing** an object stored against anchor X, while anchor Y is the current reference, means
  walking the shortest path Y → X and composing the edges.

Consequences worth planning for rather than discovering:

| Issue | What to do about it |
|---|---|
| **Disconnected graph** — two markers never seen together have no path, so objects on one are undrawable from the other | Detect it and say so: "Walk between the two markers with both in view to link them." This is a first-class UX state, not an error |
| **Error compounds along a path** | Prefer short paths. Expose hop count and total edge quality as a confidence, the way `viewSpreadRadians` already works |
| **Edges vary in quality** | Score each observation (both markers `FULL_TRACKING`, both close, both near frame centre) and keep the best rather than averaging. Averaging rotations properly is a rabbit hole and buys little at these sample counts |
| **Switching jitter** | Hysteresis is essential. Requiring a new candidate to beat the current one by a clear margin, and to hold it for a second or so, is the difference between a stable overlay and a strobing one |

`AugmentedImage.getTrackingMethod()` — confirmed present in the SceneView-bundled ARCore — returns
`FULL_TRACKING` or `LAST_KNOWN_POSE`. That distinction is the primary quality signal: a marker
reporting `LAST_KNOWN_POSE` is ARCore guessing from odometry, not seeing the marker, and must never
win the selection.

### An extra operation this unlocks

**Re-homing.** Once a good edge exists between the anchor an object was measured against and a
better one, the object can be re-expressed into the better frame permanently. That does improve
long-term accuracy, and it is a natural "tidy up this session" action.

### Where the code changes

Almost all of the difficulty is pure geometry and graph work, which is the kind of thing that has
caught the most bugs on this project and is the cheapest to test.

| Layer | Change |
|---|---|
| `:geometry` | Nothing. `Pose` composition already does what is needed |
| `:ar` | Anchor observation per frame: which anchors are visible, their poses, a quality score. Selection with hysteresis |
| `session/domain` | `Session.markerId` becomes a set of anchors. `MeasuredObject` gains the anchor it is homed to. New: the anchor graph, edge recording, shortest path, re-homing — all pure, all testable |
| `session/data` | Two tables: anchors per session, and edges. Both straightforward |
| `session/presentation` | Show which anchor is active and why; surface the disconnected state |

---

## 2. Cloud Anchors and Geospatial as alternatives

### What the documentation says

Confirmed from the ARCore docs:

- Both use "the ARCore API hosted on Google Cloud" and **share one authorization mechanism**.
- Android API key goes in the manifest as meta-data named **`com.google.android.ar.API_KEY`**.
- Cloud Anchors: `config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED`.
- Geospatial: `config.geospatialMode = Config.GeospatialMode.ENABLED`, and you **must** call
  `Session.checkGeospatialModeSupported()` first — configuring an unsupported device "will throw an
  `UnsupportedConfigurationException`".
- Geospatial needs `ACCESS_FINE_LOCATION` (coarse alone will fail requests) and internet, plus
  `Session.checkVpsAvailabilityAsync()` to know whether there is coverage where you are standing.

### Two findings that affect the design

**Cloud Anchors expire in 24 hours with an API key.** The docs are explicit: "Apps that host and
resolve Cloud Anchors with a TTL greater than 1 day must use keyless authorization." For a
measure-it-now-come-back-next-month app, that is disqualifying. Keyless works but needs OAuth client
IDs registered against each signing key's SHA-1 — awkward in a public repo where every contributor
has a different debug key.

**Geospatial gives you one global frame for free.** Every geospatial anchor is expressed against
Earth, so any two of them are already related — no graph edges needed. That is a large simplification
for outdoor or large-site use, and it means the anchor graph only ever needs edges for image and
cloud anchors.

### How to fit three backends without infecting the domain

The same shape as `ShellFitter`: a port in the domain, implementations outside.

```
AnchorSource            (domain port)
  observable(): Flow<List<ObservedAnchor>>   // id, pose, quality, kind

AugmentedImageAnchorSource   (:ar)
CloudAnchorSource            (:ar)  — needs network + authorization
GeospatialAnchorSource       (:ar)  — needs permissions + VPS + support check
```

`AnchorKind` on the domain model; the selection and graph logic never learns which is which except
to know that geospatial anchors are mutually connected by construction.

### Mixed sessions — yes, they work

You suspected ARCore would forbid this. It does not. `setAugmentedImageDatabase`,
`setCloudAnchorMode` and `setGeospatialMode` are independent `Config` fields and the reference
documentation states **no** mutual-exclusivity between them. The only exclusivity noted anywhere
nearby is that Augmented Faces requires the front camera, which is irrelevant here.

Practical caveats, none of them blocking:

- Geospatial must be guarded by `checkGeospatialModeSupported()` or it throws.
- Cloud and geospatial both need network and authorization; images need neither. A session mixing
  them degrades unevenly — offline, the image anchors keep working and the others do not.
- Location permission is only needed if geospatial is actually enabled. Requesting it for a session
  that uses images only would be asking for something you do not need.

There is a nice consequence: an image anchor observed while Earth is localized gains an edge to the
Earth frame, which **georeferences an image-anchored session** without any extra work. Your workshop
measured against printed markers acquires real-world coordinates the first time you stand outside
with both available.

---

## 3. API keys in a public repository

Never in git. The standard arrangement, which this repo is already set up for:

1. **`local.properties`** — already gitignored — holds `arcoreApiKey=...` and `sentryDsn=...`.
2. **`app/build.gradle.kts`** reads it and feeds `manifestPlaceholders`, defaulting to an empty
   string so a clone with no key still builds.
3. **`AndroidManifest.xml`** carries
   `<meta-data android:name="com.google.android.ar.API_KEY" android:value="${arcoreApiKey}" />`.
4. **CI** injects from a GitHub secret, exactly as the plugin's release workflow already does.

The part that matters more than the plumbing: **the app must degrade honestly.** With no key, Cloud
Anchors and Geospatial should be visibly unavailable with a one-line reason and a link to
`docs/arcore-api-key.md`, not silently broken or crashed. Augmented images keep working, so a
key-less clone is still a complete app.

The doc itself should be short enough to follow in five minutes: create a Cloud project, enable the
ARCore API, create an API key, restrict it to Android with your package name and SHA-1, paste one
line into `local.properties`. Plus a paragraph on when keyless is worth the extra effort — which is
precisely when you want cloud anchors to outlive a day.

---

## 4. Rename, delete, and undo

The smallest and most immediately useful item on this list. Much of it already exists below the UI.

| Item | State today |
|---|---|
| Rename session | `RenameSessionUseCase` and the event exist, tested. **No UI calls them** |
| Rename object | Nothing. Needs a use case, mirroring the session one |
| Delete session | In the repository contract and tested. **No UI** |
| Delete object | Fully wired |
| Undo | Nothing |

### Undo: soft delete, not an in-memory stack

Recommend a `deletedAt` column rather than an undo stack in the view model.

- Survives process death, so a snackbar that disappears when the app is backgrounded has not silently
  destroyed anything.
- Makes a "recently deleted" view possible later at no extra cost.
- Costs one `WHERE deletedAt IS NULL` per read.

**The risk is specific and worth naming**: forgetting that clause in one query resurrects deleted
items in one place only. The repository contract should assert absence through *every* read path —
list, single, and the object lists inside a session — so a missed filter fails a test rather than
appearing as a ghost row. Purge after some window, or on explicit "empty trash".

---

## 5. Debug and telemetry

### Finding: your plugin already has this, and this project does not have it

`clean-android-skeleton` ships a complete `telemetry` feature behind
`-PcleanSkeletonIncludeTelemetry=true`: Sentry diagnostics, Firebase analytics, a consent screen,
DataStore-backed settings, a sanitizer, and a `ProductionTelemetryTree` for Timber. I did not pass
that flag at M0, so husk-ar has none of it.

The domain port is already the right shape:

```
DiagnosticsReporterRepository
  setCollectionEnabled(enabled)
  addBreadcrumb(message, category)
  captureWarning(message, category, throwable)
  captureError(message, category, throwable)
```

**Caution before regenerating.** `generateCleanAndroidSkeleton` writes the whole skeleton, and this
project now has a great deal of its own code alongside it. Generate into a scratch directory with the
flag on, diff, and copy the `telemetry/` module and its DI wiring across by hand. Do not run the
generator over husk-ar with overwrite enabled.

### What to instrument, specifically

You want to know why a shell came out as it did. The single most valuable breadcrumb is a record of
every solve:

- view count, `viewSpreadRadians`, `nullSpaceMargin`, `conicResidual`, refusal reason
- the anchor it was solved against, and that anchor's quality at the time
- the marker's recorded printed width

That last one is worth calling out. If someone measured their printed sheet as 96 mm and corrected
it, every measurement in their dataset shifts by 4% — and without that field in the breadcrumb you
would spend a long time hunting a bug that was a printer setting.

Also worth recording: anchor switches (from, to, why), hold-still refusals, and the preview/image
dimension pair — the last of which would immediately reveal a phone whose preview mapping assumption
does not hold.

### Do the cheap thing first

Before Sentry, build the **in-app debug log** behind the toggle that already exists: the last N solve
records with their numbers, readable on the phone, no network, no consent, no DSN. For a personal app
this probably answers most of "why is that shell wrong" on its own, and it costs a fraction of the
telemetry integration.

Then add Sentry for what the in-app log cannot do: catching the case you did not think to look at.

**Privacy is not a footnote here.** Dimensions and positions of objects in someone's home are
personal. The plugin's consent gate and sanitizer exist for exactly this; diagnostics must stay off
until switched on, and the DSN belongs in `local.properties` with the API key.

---

## 6. Snapshot mode and the preview buffer

### Snapshot

A shutter button that produces an axis-aligned ellipse, for when you can simply hold the phone close
to the object.

Everything downstream already supports it. `TracedEllipse` with `rotationRadians = 0` is exactly what
`fromDragBox` produces for a wide box, and the whole pipeline from there is unchanged. The design
question is only *what rectangle* the snapshot uses.

Recommendation: show a fixed reticle — an axis-aligned oval at some fraction of the frame — and let
the shutter commit it. The person frames the object inside it. That is predictable, and it inverts
the effort: instead of tracing accurately, you move the phone until the object fits. A pinch to
resize the reticle, still axis-aligned, is a cheap refinement.

The hold-still guard is unnecessary here — a shutter press is instantaneous — but the *marker* still
has to be tracked, so the same "point at the marker" gate applies.

### Preview buffer

Camera-app style: a thumbnail bottom-right after each capture, tappable to review, keep or delete.

**The design decision that makes this more than a nicety**: the buffer should be a *view onto the
object's observations*, not a separate list. Then deleting a preview means deleting that observation
and re-solving — which is the correction workflow the app currently lacks entirely. Right now a bad
view can only be escaped by deleting the whole object and starting again.

Practicalities:

| Concern | Approach |
|---|---|
| Where the image comes from | `frame.acquireCameraImage()` at capture time only — never per frame — then downscale immediately |
| Memory | Thumbnails at roughly 320×240 are about 300 KB each; ten is ~3 MB. Cap the buffer and drop the oldest |
| YUV conversion | The camera image is YUV; converting costs real time. Doing it once per capture is fine, once per frame is not |
| Not persisted | In-memory as you asked, so thumbnails are gone after a restart. Replay still works — it redraws the outline over the live camera — but the photographs are not there. Persisting to files later is a small change if you want it |

Deleting the last remaining view of an object leaves it unmeasured rather than deleted; that should
be the stated behaviour rather than something discovered.

---

## 7. Suggested order

Test on the phone first. Several of these decisions — particularly how aggressive anchor switching
needs to be — depend on how augmented-image tracking actually behaves for you.

| Order | Item | Why here |
|---|---|---|
| 1 | Rename, delete, undo | Small, no AR, removes daily friction while you are testing everything else |
| 2 | In-app debug log | Answers "why is this shell wrong" immediately, with no infrastructure |
| 3 | Snapshot + preview buffer | Makes capture much faster, which means more real data for the next item |
| 4 | Multi-anchor graph | The big one. Wants real tracking data to tune the switching thresholds |
| 5 | Anchor backends + API key plumbing | Builds on the graph; geospatial simplifies rather than complicates it |
| 6 | Sentry telemetry | Most valuable once there is a corpus of sessions to explain |

Items 1–3 are mostly things the codebase is already shaped for. Item 4 is the substantial piece of
design, and it is almost entirely pure geometry and graph traversal — the part of this project that
has proven cheapest to get right and most expensive to get wrong.
