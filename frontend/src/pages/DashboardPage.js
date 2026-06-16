import React, { useState, useEffect, useCallback } from 'react';
import Navbar from '../components/Navbar';
import FintechChart from '../components/FintechChart';
import ALevelChart from '../components/ALevelChart';
import StatsPanel from '../components/StatsPanel';
import SentimentPanel from '../components/SentimentPanel';
import BacktestPanel from '../components/BacktestPanel';
import { getStockList, runAnalysis, getSentiment, getUser } from '../services/api';
import {
  MODE_FINTECH, MODE_ALEVEL,
  MARKET_NASDAQ, MARKET_FTSE,
  FORECAST_PRESETS, FORECAST_MIN_DAYS, FORECAST_MAX_DAYS
} from '../services/constants';

/**
 * DashboardPage — the main analysis interface.
 *
 * Layout:
 *  ┌─────────────────────────────────────────┐
 *  │  Navbar                                 │
 *  ├─────────────────────────────────────────┤
 *  │  Controls: stock picker, days, mode     │
 *  ├─────────────────────────────────────────┤
 *  │  Chart (Fintech or A-Level)             │
 *  ├──────────────────────┬──────────────────┤
 *  │  Stats Panel         │ Sentiment Panel  │
 *  └──────────────────────┴──────────────────┘
 */
function DashboardPage() {
  const user = getUser();

  // ── STATE ──────────────────────────────────────────────────────────────────
  const [market, setMarket]       = useState(user?.preferredMarket || MARKET_NASDAQ);
  const [stocks, setStocks]       = useState([]);
  const [symbol, setSymbol]       = useState('');
  const [daysAhead, setDaysAhead] = useState(21);
  const [mode, setMode]           = useState(MODE_FINTECH);

  const [result, setResult]           = useState(null);
  const [sentiment, setSentiment]     = useState(null);

  const [analysisLoading, setAnalysisLoading] = useState(false);
  const [sentimentLoading, setSentimentLoading] = useState(false);
  const [error, setError]             = useState('');

  // ── LOAD STOCK LIST ────────────────────────────────────────────────────────
  useEffect(() => {
    const loadStocks = async () => {
      try {
        const res = await getStockList(market);
        const stockList = res.data.stocks || [];
        setStocks(stockList);
        if (stockList.length > 0) {
          setSymbol(stockList[0].symbol);
        }
      } catch (e) {
        setError('Failed to load stock list. Please refresh.');
      }
    };
    loadStocks();
  }, [market]);

  // ── RUN ANALYSIS ───────────────────────────────────────────────────────────
  // requestIdRef tracks the latest request so a slow, stale response can
  // never overwrite a result from a newer request (e.g. user double-clicked
  // Analyse, or switched stock mid-request).
  const requestIdRef = React.useRef(0);

  const handleAnalyse = useCallback(async () => {
    if (!symbol || analysisLoading) return; // guard against double-clicks

    const thisRequestId = ++requestIdRef.current;

    setError('');
    setResult(null);
    setSentiment(null);
    setAnalysisLoading(true);
    setSentimentLoading(true);

    // Run analysis and sentiment in parallel
    const analysisPromise = runAnalysis(symbol, daysAhead, mode)
      .then(res => {
        if (requestIdRef.current !== thisRequestId) return; // stale, ignore
        setResult(res.data);
      })
      .catch(e => {
        if (requestIdRef.current !== thisRequestId) return; // stale, ignore
        setError(e.response?.data || 'Analysis failed. Please try again.');
      })
      .finally(() => {
        if (requestIdRef.current === thisRequestId) setAnalysisLoading(false);
      });

    const sentimentPromise = getSentiment(symbol)
      .then(res => {
        if (requestIdRef.current !== thisRequestId) return;
        setSentiment(res.data);
      })
      .catch(() => {
        if (requestIdRef.current !== thisRequestId) return;
        setSentiment(null);
      })
      .finally(() => {
        if (requestIdRef.current === thisRequestId) setSentimentLoading(false);
      });

    await Promise.allSettled([analysisPromise, sentimentPromise]);
  }, [symbol, daysAhead, mode, analysisLoading]);

  // Auto-run when symbol changes (if we already have results showing)
  const handleSymbolChange = (e) => {
    setSymbol(e.target.value);
    setResult(null);
    setSentiment(null);
    setError('');
  };

  // ── DAYS PRESETS ───────────────────────────────────────────────────────────
  const dayPresets = FORECAST_PRESETS;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
      <Navbar />

      <div className="dashboard">

        {/* ── CONTROLS ROW ────────────────────────────────────────────────── */}
        <div className="controls-row">

          {/* Market selector */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">Market</label>
            <select
              className="form-select"
              value={market}
              onChange={e => { setMarket(e.target.value); setResult(null); setSentiment(null); }}
              style={{ minWidth: 120 }}
            >
              <option value={MARKET_NASDAQ}>🇺🇸 NASDAQ</option>
              <option value={MARKET_FTSE}>🇬🇧 FTSE 100</option>
            </select>
          </div>

          {/* Stock picker */}
          <div className="form-group" style={{ marginBottom: 0, flex: 1, minWidth: 200 }}>
            <label className="form-label">Stock</label>
            <select
              className="form-select"
              value={symbol}
              onChange={handleSymbolChange}
            >
              {stocks.map(s => (
                <option key={s.symbol} value={s.symbol}>
                  {s.symbol} — {s.name}
                </option>
              ))}
            </select>
          </div>

          {/* Forecast period */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">Forecast Period</label>
            <div style={{ display: 'flex', gap: 4 }}>
              {dayPresets.map(p => (
                <button
                  key={p.label}
                  className={`btn btn-sm ${daysAhead === p.days ? 'btn-primary' : 'btn-secondary'}`}
                  onClick={() => setDaysAhead(p.days)}
                >
                  {p.label}
                </button>
              ))}
              <input
                type="number"
                className="form-input"
                value={daysAhead}
                onChange={e => setDaysAhead(Math.max(FORECAST_MIN_DAYS, Math.min(FORECAST_MAX_DAYS, parseInt(e.target.value) || 21)))}
                style={{ width: 70, marginLeft: 4 }}
                min="1" max="504"
                title="Custom days"
              />
            </div>
          </div>

          {/* Mode toggle */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">Analysis Mode</label>
            <div className="toggle-wrapper">
              <span className={`toggle-label ${mode === MODE_ALEVEL ? 'active' : ''}`}>
                A-Level
              </span>
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={mode === MODE_FINTECH}
                  onChange={e => setMode(e.target.checked ? MODE_FINTECH : MODE_ALEVEL)}
                />
                <span className="toggle-slider" />
              </label>
              <span className={`toggle-label ${mode === MODE_FINTECH ? 'active' : ''}`}>
                Fintech
              </span>
            </div>
          </div>

          {/* Analyse button */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">&nbsp;</label>
            <button
              className="btn btn-primary"
              onClick={handleAnalyse}
              disabled={analysisLoading || !symbol}
              style={{ minWidth: 120, height: 42 }}
            >
              {analysisLoading ? (
                <><div className="spinner" style={{ width: 16, height: 16, borderWidth: 2 }} /> Running...</>
              ) : (
                '▶ Analyse'
              )}
            </button>
          </div>
        </div>

        {/* ── MODE BADGE ──────────────────────────────────────────────────── */}
        {mode && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span className={`badge ${mode === MODE_FINTECH ? 'badge-blue' : 'badge-purple'}`}>
              {mode === MODE_FINTECH ? '📊 Fintech Mode' : '📐 A-Level Mode'}
            </span>
            {mode === MODE_FINTECH && (
              <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                GBM Monte Carlo · GARCH(1,1) · VaR · Sharpe Ratio · Bollinger Bands
              </span>
            )}
            {mode === MODE_ALEVEL && (
              <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                Log-linear Regression · ±1σ ±2σ Bands · 20-day SMA · R²
              </span>
            )}
          </div>
        )}

        {/* ── ERROR ───────────────────────────────────────────────────────── */}
        {error && <div className="error-box">{error}</div>}

        {/* ── LOADING STATE ────────────────────────────────────────────────  */}
        {analysisLoading && !result && (
          <div className="card">
            <div className="loading-overlay">
              <div className="spinner" style={{ width: 40, height: 40, borderWidth: 4 }} />
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 15, fontWeight: 500, marginBottom: 4 }}>
                  Running analysis for {symbol}...
                </div>
                <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
                  {mode === MODE_FINTECH
                    ? 'Running 500 Monte Carlo paths · Fitting GARCH(1,1) · Computing VaR & Sharpe...'
                    : 'Fitting log-linear regression · Computing confidence bands...'}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ── EMPTY STATE ─────────────────────────────────────────────────── */}
        {!analysisLoading && !result && !error && (
          <div className="card">
            <div className="loading-overlay" style={{ padding: '80px 60px' }}>
              <div style={{ fontSize: 48 }}>📈</div>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 18, fontWeight: 600, marginBottom: 8 }}>
                  Select a stock and click Analyse
                </div>
                <div style={{ fontSize: 14, color: 'var(--text-muted)', maxWidth: 400 }}>
                  {mode === MODE_FINTECH
                    ? 'Fintech mode runs GBM Monte Carlo simulation, GARCH volatility modelling, VaR, Sharpe Ratio, and Bollinger Bands — the same techniques used by professional quant desks.'
                    : 'A-Level mode runs log-linear regression with ±1σ and ±2σ confidence bands — a simpler interpretable approach for comparison.'}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ── CHART ───────────────────────────────────────────────────────── */}
        {result && (
          mode === MODE_FINTECH
            ? <FintechChart result={result} />
            : <ALevelChart result={result} />
        )}

        {/* ── STATS + SENTIMENT ROW ────────────────────────────────────────── */}
        {result && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 400px', gap: 24, alignItems: 'start' }}>
            <StatsPanel result={result} sentiment={sentiment} mode={mode} />
            {mode === MODE_FINTECH && (
              <SentimentPanel sentiment={sentiment} loading={sentimentLoading} />
            )}
          </div>
        )}

        {/* ── BACKTEST / MODEL VALIDATION ──────────────────────────────────── */}
        {/* Only shown in Fintech mode — backtesting validates the GBM model */}
        {result && mode === MODE_FINTECH && (
          <BacktestPanel symbol={symbol} />
        )}

        {/* ── FOOTER NOTE ─────────────────────────────────────────────────── */}
        <div style={{
          textAlign: 'center',
          fontSize: 12,
          color: 'var(--text-muted)',
          paddingBottom: 24,
          borderTop: '1px solid var(--border)',
          paddingTop: 16
        }}>
          ⚠️ QuantView is for educational purposes only. Not financial advice.
          Past performance does not guarantee future results. GBM assumes log-normal returns.
        </div>

      </div>
    </div>
  );
}

export default DashboardPage;
