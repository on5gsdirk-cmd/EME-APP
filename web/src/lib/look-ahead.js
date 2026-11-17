// Slider on the Moon tab (#118): look ahead up to 48 hours in steps of 10 minutes.
// Same rules as moon/LookAhead.kt; shared values in app/src/test/resources/expected/look_ahead.csv.

export const STEP_MS = 600_000;
export const MAX_STEPS = 288; // 48 hours

/** Start time: now rounded down to 10 minutes. */
export const lookAheadBase = (now) => now - (((now % STEP_MS) + STEP_MS) % STEP_MS);

/** The time shown: now on step 0, else base plus the steps. */
export const shownMillis = (now, base, steps) => (steps <= 0 ? now : base + Math.min(steps, MAX_STEPS) * STEP_MS);

/** "+5:30" for 33 steps: hours and minutes ahead. */
export function lookAheadLabel(steps) {
  const minutes = Math.min(Math.max(steps, 0), MAX_STEPS) * 10;
  return `+${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`;
}
