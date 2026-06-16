import axios from 'axios';

/**
 * api.js — centralised axios instance for all backend calls.
 *
 * Base URL: uses React's proxy (package.json "proxy": "http://localhost:8080")
 * In Codespaces: the proxy handles routing from port 3000 → port 8080.
 *
 * JWT token is automatically attached to every request via the
 * axios request interceptor — no need to pass it manually each time.
 */

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000, // 30s — Monte Carlo simulation can take a few seconds
});

// ── REQUEST INTERCEPTOR ───────────────────────────────────────────────────────
// Attach JWT token to every request automatically
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ── RESPONSE INTERCEPTOR ──────────────────────────────────────────────────────
// Handle 401 Unauthorized globally — redirect to login
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ── AUTH ENDPOINTS ────────────────────────────────────────────────────────────

/** POST /api/auth/login — returns JWT token + user info */
export const login = (username, password) =>
  api.post('/auth/login', { username, password });

/** POST /api/auth/register — create new account */
export const register = (username, email, password, preferredMarket) =>
  api.post('/auth/register', { username, email, password, preferredMarket });

// ── STOCK DATA ENDPOINTS ──────────────────────────────────────────────────────

/** GET /api/stocks/list/{market} — get NASDAQ or FTSE stock list */
export const getStockList = (market) =>
  api.get(`/stocks/list/${market}`);

/** GET /api/stocks/history/{symbol} — get 2 years of price history */
export const getPriceHistory = (symbol) =>
  api.get(`/stocks/history/${symbol}`);

// ── ANALYSIS ENDPOINT ─────────────────────────────────────────────────────────

/**
 * POST /api/analysis/run — trigger full fintech analysis
 * Runs GBM Monte Carlo, GARCH, VaR, Sharpe, Bollinger Bands, A-Level regression
 */
export const runAnalysis = (symbol, daysAhead, mode) =>
  api.post('/analysis/run', { symbol, daysAhead, mode });

// ── SENTIMENT ENDPOINT ────────────────────────────────────────────────────────

/** GET /api/sentiment/{symbol} — fetch and score news headlines */
export const getSentiment = (symbol) =>
  api.get(`/sentiment/${symbol}`);

// ── BACKTEST ENDPOINT ─────────────────────────────────────────────────────────

/**
 * POST /api/backtest/run — validate GBM model accuracy against history
 * Runs 20 historical test windows and reports calibration score
 */
export const runBacktest = (symbol, forecastDays) =>
  api.post('/backtest/run', { symbol, forecastDays }, { timeout: 60000 });

// ── AUTH HELPERS ──────────────────────────────────────────────────────────────

/** Save user session to localStorage after login */
export const saveSession = (token, user) => {
  localStorage.setItem('token', token);
  localStorage.setItem('user', JSON.stringify(user));
};

/** Get current user from localStorage */
export const getUser = () => {
  const user = localStorage.getItem('user');
  return user ? JSON.parse(user) : null;
};

/** Check if user is logged in */
export const isAuthenticated = () => !!localStorage.getItem('token');

/** Log out — clear localStorage */
export const logout = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
};

export default api;
