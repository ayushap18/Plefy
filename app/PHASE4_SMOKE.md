# Phase 4 — `:app` Manual End-to-End Smoke Test

The correctness of the `:app` module is **wiring correctness**: the single Hilt module
(`di/AppModule.kt`) must satisfy every `@HiltViewModel` / `@HiltWorker` / `@Inject` consumer
across `:data`, `:domain`, `:feature:import`, and `:feature:viewer`, and the
Navigation-Compose graph in `MainActivity.kt` must connect the two screens.

There are **no JVM unit tests for `:app`**. Its correctness is proven at build time — if the
Hilt graph is under-provided or miswired, the Hilt annotation processor fails the
`:app:kaptDebugKotlin` / `:app:hiltJavaCompileDebug` task and the app will not assemble. The
per-module logic (ViewModels, repositories, use cases, parser, inference) is covered by unit
tests in those modules. What remains is the human-observable end-to-end path, captured below as
a manual smoke test to run on a device/emulator after each `:app` change.

## Preconditions

- Build and install a debug build: `./gradlew :app:installDebug` (or Run from Android Studio).
- Have at least one `.csv` **and** one `.xlsx` file reachable by the system file picker
  (on-device Downloads, Drive, or `adb push`ed to `/sdcard/Download/`).
  - A tiny CSV with a header row and a handful of rows, mixing text / numeric / date-like
    columns, exercises type inference and the default view best.

## Steps

1. **Launch** — Cold-start the app. It opens on the **Library** screen (the `library` start
   destination). Expected: top app bar, an empty-state (or previously-imported sheets) list, and
   a `+` floating action button.

2. **Import a spreadsheet** — Tap the `+` FAB. The **system file picker (SAF)** opens.
   - Pick the `.csv` file. Expected: a linear progress bar appears at the top while the
     `ImportWorker` parses and inserts the sheet in the background; no ANR/freeze (import runs
     off the main thread via WorkManager).
   - Repeat with the `.xlsx` file to confirm both formats import.

3. **See it in the library** — When import completes, the new sheet appears as a row/card in the
   Library list (display name derived from the file). Expected: progress bar disappears; on a
   parse failure a **snackbar** surfaces the error and no row is added.

4. **Open it** — Tap the sheet row. Expected: navigation to `viewer/{tableId}`; the **Viewer**
   screen renders a horizontally-scrollable table (header row + paged data rows) using the smart
   default view (`BuildDefaultViewUseCase`). Columns are aligned under horizontal scroll.

5. **Sort** — Tap **Sort** in the top bar, choose a column and direction. Expected: row order
   updates to match; the sort indicator reflects the active column/direction.

6. **Filter** — Tap **Filter**, pick a column, an operator, and a value. Expected: only matching
   rows remain; clearing the filter restores the full set.

7. **Search** — Enter a query in the search field. Expected: rows narrow to those matching the
   term across searchable columns; clearing it restores all rows.

8. **Group** — Tap **Group**, choose a column. Expected: a group summary (distinct values +
   counts, via `GroupSummaryUseCase`) is shown/applied; ungrouping restores the flat list.

9. **Open a row detail** — Tap any data row. Expected: a modal bottom sheet opens listing
   **every column's value** for that row (`GetRowCellsUseCase`). Dismiss it to return to the
   table.

10. **Back** — Tap **Back** in the Viewer top bar. Expected: return to the Library screen with
    the imported sheet(s) still listed (persisted in Room).

## Optional resilience checks

- **Delete** — On the Library screen, swipe a row (or tap its trailing delete icon). Expected:
  the sheet is removed and stays gone after relaunch.
- **Process restart** — Kill and relaunch the app. Expected: previously imported sheets are
  still present (Room-backed), confirming the singleton `AppDatabase` wiring.
- **Rotation** — Rotate the device on the Viewer. Expected: current sort/filter/search/group
  state survives (held in the ViewModel).

## Pass criteria

All steps 1–10 complete without a crash, ANR, or missing-binding runtime error, and each
observable expectation holds. Any Hilt "missing binding" or "cannot be provided" crash at
launch or on `hiltViewModel()` indicates an `AppModule` wiring gap and is an automatic fail.
