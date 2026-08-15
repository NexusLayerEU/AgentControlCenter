export const THEMES = [
  { id: 'dev', name: 'DevTheme', hint: 'instrument panel · phosphor lime' },
  { id: 'cyber', name: 'Blackwire', hint: 'cyberpunk · CRT + neon' },
]

const STORAGE_KEY = 'acc-theme'
const DEFAULT = 'dev'

const isKnown = (id) => THEMES.some((t) => t.id === id)

export function readStoredTheme() {
  // ?theme=cyber wins over the stored choice, so a deck can be linked or
  // screenshotted without touching local state. It then becomes the stored one.
  try {
    const requested = new URLSearchParams(location.search).get('theme')
    if (isKnown(requested)) {
      return applyTheme(requested)
    }
  } catch {
    // Malformed URL — ignore and fall through to the stored value.
  }
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return isKnown(stored) ? stored : DEFAULT
  } catch {
    // Private mode or a locked-down browser — fall back rather than crash.
    return DEFAULT
  }
}

export function applyTheme(id) {
  const theme = isKnown(id) ? id : DEFAULT
  document.documentElement.setAttribute('data-theme', theme)
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Not persisting is survivable; the theme still applies for this session.
  }
  return theme
}

export function nextTheme(current) {
  const index = THEMES.findIndex((t) => t.id === current)
  return THEMES[(index + 1) % THEMES.length].id
}
