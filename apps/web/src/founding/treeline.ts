/**
 * Where the pines stand along the near ridge of HighlandScene.
 *
 * Computed once at module load, and deterministic — a seeded walk, never
 * Math.random. The build prerenders this page and writes the rendered DOM back
 * into `founding.html` (scripts/prerender.mjs), so a random treeline would
 * produce a different file on every build and turn a real diff into noise.
 *
 * Irregular spacing and three sizes are the point: an even rhythm reads as a
 * pattern rather than as a hillside.
 */

/** The near ridge's height at 24 sample points, so trees sit on the hill. */
const RIDGE = [
  420, 396, 386, 400, 414, 392, 376, 396, 412, 388, 380, 400,
  414, 390, 378, 398, 414, 386, 380, 402, 414, 392, 382, 404,
]

const TREES = 64

/** A plain LCG. Any repeatable sequence would do; this one is short. */
function seeded(seed: number): () => number {
  let state = seed
  return () => {
    state = (state * 1103515245 + 12345) % 2147483648
    return state / 2147483648
  }
}

export interface Pine {
  key: number
  /** A `translate(...) scale(...)` for one `<use>` of the pine symbol. */
  transform: string
}

export const PINES: Pine[] = (() => {
  const next = seeded(7)
  const pines: Pine[] = []

  for (let i = 0; i < TREES; i++) {
    const t = i / (TREES - 1)
    // Overhang both edges so the line does not visibly start and stop.
    const x = t * 1460 - 10 + (next() * 14 - 7)
    const ridge = RIDGE[Math.min(RIDGE.length - 1, Math.round(t * (RIDGE.length - 1)))]!
    const y = ridge + 6 + next() * 10
    const scale = 0.55 + next() * 0.7

    pines.push({
      key: i,
      transform: `translate(${x.toFixed(1)} ${y.toFixed(1)}) scale(${scale.toFixed(2)})`,
    })
  }

  return pines
})()
