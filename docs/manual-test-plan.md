# Manual test plan

Everything in the app as it stands, in the order that finds problems soonest. Automated tests cover
the arithmetic; this covers what only a phone can answer.

## Before you start

You need an **ARCore-capable Android phone** (developer options on, USB debugging enabled), a
**printer**, and a **ruler with millimetres**.

```bash
./gradlew :app:installDebug
```

Nothing in the AR sections works on an emulator — ARCore does not track there.

### What "fail" means here

Record the section, what you saw, and — for anything numerical — the number you got against the
number expected. A wrong measurement is far more useful than "it looked off".

---

## A. Groundwork, no camera needed

Do these first. If A fails, nothing after it is worth attempting.

| # | Do this | Expect |
|---|---|---|
| A1 | Launch the app fresh | The session list opens. Not a crash, not a blank screen |
| A2 | Read the empty state | "No sessions yet. Start one to begin measuring." — **not** a spinner that never stops |
| A3 | Tap **New session** | A row appears, titled `Session ` and today's date |
| A4 | Tap **New session** twice more | Three distinct rows. Newest at the **top** |
| A5 | Read a row's subtitle | "0 objects measured" — plural wording, not "0 object" |
| A6 | Force-stop the app, reopen | All three sessions still there. This is the whole point of the database |
| A7 | Tap a session | The session screen opens with its name at the top |
| A8 | Press back | Returns to the list, not out of the app |

**A6 is the one to care about.** If sessions vanish, stop and report it — everything downstream
writes to that same database.

---

## B. Markers and printing

The printed width scales every measurement the app will ever make. Do this properly.

| # | Do this | Expect |
|---|---|---|
| B1 | From the session list, tap **Markers** | The library opens with one marker, "Earth" |
| B2 | Read the instructions | A width, a height, side margins and top/bottom margins, plus "Print at actual size" |
| B3 | Check the arithmetic on A4 | Width **200 mm**, height **200 mm**, sides **5 mm**, top/bottom **48 mm** |
| B4 | Tap **LETTER** | Side margins change to **8 mm**. Top/bottom change too |
| B5 | Tap **A4** again | Back to 5 mm |
| B6 | Look for a delete button on Earth | There is **none**. The bundled marker cannot be removed |

### B7 — Print it

Print the marker at **actual size / 100% / no scaling**. Do **not** use "Fit to page".

### B8 — Measure the printed sheet

Measure the image's width with your ruler.

- **200 mm** → correct, carry on.
- **Anything else** → this is the case the app exists to catch. Type what the **reference bar**
  measures into the field and tap **Correct the size**.

> ⚠️ **Known gap.** The reference bar is described on screen and the correction arithmetic is
> tested, but **the bar is not yet drawn on the printed sheet** — there is no print-rendering code.
> For now, measure the marker image's own width instead, and enter `measured ÷ 2` as the bar figure
> (the bar is defined as 100 mm against a 200 mm marker). Report this as a gap rather than a bug.

| # | Do this | Expect |
|---|---|---|
| B9 | Enter a nonsense value — `0` — and apply | The width does **not** change. Nothing vanishes later |
| B10 | Force-stop, reopen, return to Markers | Your corrected width is still there, not reset to 200 |

**B10 matters:** if a relaunch resets your correction, every measurement silently reverts to the
wrong scale.

---

## C. First capture — the moment of truth

Lay the printed marker flat, well lit, with a small object beside it (a mug is ideal — 60–120 mm
across). Marker and object both in shot.

| # | Do this | Expect |
|---|---|---|
| C1 | Open a session, tap **Measure something** | A row appears, called "Object" |
| C2 | Tap that row | The camera opens. Grant permission if asked |
| C3 | Point away from the marker | "Point the camera at the marker" |
| C4 | Point at the marker | Changes to "Drag a box around the object" |
| C5 | Move the phone around, marker in view | The message stays on the outline prompt — it should not flicker back and forth |
| C6 | Drag a box around the object | An outline follows your finger while it is down |
| C7 | Release | Count goes to "1 view". Message asks for another angle |
| C8 | **Deliberately swing the phone while dragging** | **"Hold still while outlining"**, and the count does **not** increase |
| C9 | Move ~30° around the object, outline again | "2 views" |
| C10 | Move another ~30°, outline again | "3 views" and **"Measured"** |
| C11 | Go back to the session | The row shows a size in mm and a confidence |

### What C11 should say

- Three views spread widely → **`W x H x D mm`** plain.
- Three views close together → **"About W x H x D mm, views too close together"**.

### Judging C11's numbers

Measure the real object with your ruler and compare.

| Result | Reading |
|---|---|
| Within ~5% | Working as designed |
| Consistently ~2× or ~0.5× | Radii/diameter confusion — report the exact ratio |
| Off by a constant % | Suspect the **printed marker width** first. Redo B8 |
| Wildly wrong, varies per attempt | Marker tracking or the frame conversion. Report with view count and spread |

**Report the actual numbers.** "Mug measured 82 × 79 × 95 mm, ruler says 80 × 80 × 95" is
diagnostic; "about right" is not.

### C12 — refusals

| Do this | Expect |
|---|---|
| Start a new object, take 3 views **from nearly the same spot** | "Move further around the object" |
| Tap without dragging | Nothing recorded, count unchanged |
| Take only 2 views | "Move to a new angle and outline it again" |

---

## D. Replay

Needs an object with at least 3 views from C.

| # | Do this | Expect |
|---|---|---|
| D1 | On the object's row, tap **Replay** | Camera opens, "Mug, view 1 of 3" |
| D2 | Point away from the marker | "Point the camera at the marker" |
| D3 | Stand somewhere else, marker in view | A direction and a distance: "Move left, about 40 cm" |
| D4 | **Follow the instruction** | The distance **decreases**. If it grows, the direction is inverted — report which way |
| D5 | Get close to the original spot | "You are back where this was taken", and the outline appears |
| D6 | Check where the outline sits | It should sit **on the object**, not floating elsewhere |
| D7 | Turn on the spot, facing away | No longer aligned; the outline disappears |
| D8 | Tap **Next** | "view 2 of 3", and the guidance re-aims at a different place |
| D9 | Tap **Next** past the last | Wraps to "view 1 of 3" |
| D10 | Tap **Previous** from view 1 | Wraps to view 3 |

**D4 and D6 are the ones that matter.** D4 catches an inverted direction; D6 catches a wrong
coordinate frame. Both would be invisible in every automated test.

---

## E. Adjust and delete

| # | Do this | Expect |
|---|---|---|
| E1 | On a measured row, tap **Adjust** | A sheet with three size sliders and three position sliders |
| E2 | Note the current size, drag **Width** right, tap **Apply** | The row's first dimension grows |
| E3 | Read the row's subtitle now | **"…adjusted by hand"** — it must stop claiming the solver's confidence |
| E4 | Adjust again | Still says adjusted. No reversion to a confidence band |
| E5 | Force-stop, reopen | The adjustment survived |
| E6 | Tap **Replay** on the adjusted object | Still replays — the original views were kept, not discarded |
| E7 | Tap **Delete** on an object | That row goes. Others stay |
| E8 | Force-stop, reopen | It stays deleted |

---

## F. Search and debug

| # | Do this | Expect |
|---|---|---|
| F1 | Create objects and rename... | ⚠️ **Not possible.** See Known gaps — objects are all called "Object" |
| F2 | Type into **Search measurements** | Matching rows are **tinted**, non-matching stay visible. The list does **not** shrink |
| F3 | Type in CAPITALS | Still matches |
| F4 | Clear the box | **Nothing** is tinted. Not everything |
| F5 | Tap **Debug** in the top bar | Toggles. It changes state, but see Known gaps |

---

## G. Robustness

| # | Do this | Expect |
|---|---|---|
| G1 | Rotate the phone on each screen | No crash. State survives |
| G2 | On the capture screen, background the app and return | Camera resumes, no crash |
| G3 | Deny camera permission, then open capture | Handled gracefully, not a crash |
| G4 | Cover the marker mid-capture | Falls back to "Point the camera at the marker" |
| G5 | Delete a session with measured objects, force-stop, reopen | Session and its objects both gone; no orphans reappear |
| G6 | Airplane mode throughout | Everything works — the app needs no network |

---

## Known gaps — do not report these as bugs

Found by auditing the wired UI against the code before writing this plan.

| Gap | Effect on testing |
|---|---|
| **No rename** for sessions or objects | Everything is "Object" and "Session <date>". The use cases and tests exist; no UI calls them. Makes search (F) hard to exercise meaningfully — create objects in different sessions to tell them apart |
| **No session delete** in the list | Only objects can be deleted from the UI. G5 can only be done by clearing app data |
| **No marker import** | The library shows and prints the bundled marker only. "Upload a new one" was specified but the image picker is not built |
| **Debug view draws nothing** | The toggle flips state; the frustum, ray and axis geometry is written and tested but not rendered |
| **Off-screen arrows not drawn** | The direction maths is written and tested; nothing draws the arrows |
| **Reference bar not printed** | See B8. Instructions describe it; no print rendering exists |
| **No marker picker** for new sessions | Every session uses the bundled marker |

The first three are the ones most likely to matter to you in practice.

---

## Priority order if time is short

1. **A6** — persistence. Everything depends on it.
2. **B3, B8** — the printed scale. Everything numeric depends on it.
3. **C6–C11** — does capture work at all, and are the numbers right.
4. **C8** — the hold-still guard.
5. **D4, D6** — direction and frame correctness.
6. **E3** — that an adjusted shell stops claiming measured confidence.

Sections F and G can wait.
