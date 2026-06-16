/**
 * constants.js — application-wide constants.
 *
 * Centralising magic strings here means:
 *  - One place to change a value
 *  - No typos from repeating strings across files
 *  - Self-documenting code
 */

// ── ANALYSIS MODES ────────────────────────────────────────────────────────────
export const MODE_FINTECH = 'FINTECH';
export const MODE_ALEVEL  = 'ALEVEL';

// ── MARKETS ───────────────────────────────────────────────────────────────────
export const MARKET_NASDAQ = 'NASDAQ';
export const MARKET_FTSE   = 'FTSE';

// ── SENTIMENT LABELS ──────────────────────────────────────────────────────────
export const SENTIMENT_BULLISH = 'Bullish';
export const SENTIMENT_BEARISH = 'Bearish';
export const SENTIMENT_NEUTRAL = 'Neutral';

// ── BOLLINGER SIGNALS ─────────────────────────────────────────────────────────
export const SIGNAL_OVERBOUGHT = 'OVERBOUGHT';
export const SIGNAL_OVERSOLD   = 'OVERSOLD';
export const SIGNAL_NEUTRAL    = 'NEUTRAL';

// ── FORECAST PRESETS (trading days) ──────────────────────────────────────────
export const FORECAST_PRESETS = [
  { label: '1W', days: 5   },
  { label: '1M', days: 21  },
  { label: '3M', days: 63  },
  { label: '6M', days: 126 },
  { label: '1Y', days: 252 },
];

export const FORECAST_MIN_DAYS = 1;
export const FORECAST_MAX_DAYS = 504; // ~2 trading years

// ── CHART COLOURS ─────────────────────────────────────────────────────────────
// Matching CSS variables defined in index.css
export const COLOUR_GREEN  = 'var(--accent-green)';
export const COLOUR_BLUE   = 'var(--accent-blue)';
export const COLOUR_RED    = 'var(--accent-red)';
export const COLOUR_AMBER  = 'var(--accent-amber)';
export const COLOUR_PURPLE = 'var(--accent-purple)';
export const COLOUR_MUTED  = 'var(--text-muted)';
export const COLOUR_BORDER = 'var(--border)';
