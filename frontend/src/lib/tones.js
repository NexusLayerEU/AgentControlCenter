import { useEffect, useState } from 'react'

import { useStore } from './store'

/**
 * Role slot -> CSS custom property.
 *
 * Canvas-style consumers (React Flow, xterm) need literal colour strings, not
 * class names, so they read the live tokens instead of carrying their own
 * palette. That way a theme is defined in exactly one place — the stylesheet.
 */
const TONE_VARS = {
  ink: '--color-ink',
  'ink-dim': '--color-ink-dim',
  'ink-faint': '--color-ink-faint',
  live: '--color-live',
  amber: '--color-amber',
  coral: '--color-coral',
  cyan: '--color-cyan',
  violet: '--color-violet',
}

const SURFACE_VARS = {
  void: '--color-void',
  deck: '--color-deck',
  deck3: '--color-deck-3',
  rule: '--color-rule',
  ruleHot: '--color-rule-hot',
}

function read(vars) {
  const style = getComputedStyle(document.documentElement)
  return Object.fromEntries(
    Object.entries(vars).map(([key, prop]) => [key, style.getPropertyValue(prop).trim()]),
  )
}

export function readTones() {
  return read(TONE_VARS)
}

export function readSurfaces() {
  return read(SURFACE_VARS)
}

/** Live tone + surface colours, recomputed whenever the theme changes. */
export function usePalette() {
  const theme = useStore((s) => s.theme)
  const [palette, setPalette] = useState(() => ({
    tones: readTones(),
    surfaces: readSurfaces(),
  }))

  useEffect(() => {
    // The data-theme attribute is set synchronously, but computed styles are
    // only guaranteed current after the next frame.
    const frame = requestAnimationFrame(() =>
      setPalette({ tones: readTones(), surfaces: readSurfaces() }),
    )
    return () => cancelAnimationFrame(frame)
  }, [theme])

  return palette
}

/** The active theme's mono stack, so the terminal matches the rest of the deck. */
export function terminalFont() {
  const stack = getComputedStyle(document.documentElement)
    .getPropertyValue('--font-mono')
    .trim()
  return stack || "ui-monospace, 'SF Mono', monospace"
}

/** xterm palette derived from the active theme's tokens. */
export function terminalTheme(tones, surfaces) {
  return {
    background: surfaces.void,
    foreground: tones.ink,
    cursor: tones.live,
    cursorAccent: surfaces.void,
    selectionBackground: surfaces.ruleHot,
    black: surfaces.deck,
    red: tones.coral,
    green: tones.live,
    yellow: tones.amber,
    blue: tones.cyan,
    magenta: tones.violet,
    cyan: tones.cyan,
    white: tones.ink,
    brightBlack: tones['ink-faint'],
    brightRed: tones.coral,
    brightGreen: tones.live,
    brightYellow: tones.amber,
    brightBlue: tones.cyan,
    brightMagenta: tones.violet,
    brightCyan: tones.cyan,
    brightWhite: '#ffffff',
  }
}
