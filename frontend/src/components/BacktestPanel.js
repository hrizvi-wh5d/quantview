import React, { useState, useMemo } from 'react';
import {
  ComposedChart, Line, Scatter,
  XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, ReferenceLine
} from 'recharts';
import { runBacktest } from '../services/api';

/**
 * BacktestPanel — model validation tool.
 *
 * Answers: "If I had trusted this model historically, how often would
 * it have been right?"
 *
 * Runs 20 historical test windows: at each as-of date, GBM forecasts
 * forward using ONLY data available at that point, then we check the
 * prediction against what actually happened (since it's now history).
 *
 * The calibration score is the headline number: a well-calibrated 90%
 * confidence cone should contain the real outcome ~90% of the time.
 */
function BacktestPanel({ symbol }) {
  const [forecastDays, setForecastDays] = useState(30);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleRunBacktest = async () => {
    if (!symbol) return;
    setLoading(true);
    setError('');
    setResult(null);
    try {
      const res = await runBacktest(symbol, forecastDays);
      setResult(res.data);
    } catch (e) {
      setError(e.response?.data || 'Backtest failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // Build chart data: each point is one historical window,
  // showing predicted cone vs what actually happened
  const chartData = useMemo(() => {
    if (!result) return [];
    return result.windows.map((w, i) => {
      const date = w.asOfTimestamp
        ? new Date(w.asOfTimestamp * 1000).toLocaleDateString('en-GB', { month: 'short', year: '2-digit' })
        : `#${i + 1}`;
      return {
        index: i + 1,
        date,
        predictedMedian: parseFloat(w.predictedMedian.toFixed(2)),
        predictedLower: parseFloat(w.predictedLower.toFixed(2)),
        predictedUpper: parseFloat(w.predictedUpper.toFixed(2)),
        actualPrice: parseFloat(w.actualPrice.toFixed(2)),
        withinCone: w.withinCone,
      };
    });
  }, [result]);

  const getVerdictColor = (verdict) => {
    if (verdict === 'Well Calibrated') return 'var(--accent-green)';
    if (verdict === 'Overconfident') return 'var(--accent-red)';
    return 'var(--accent-amber)'; // Underconfident
  };

  const CustomTooltip = ({ active, payload, label }) => {
    if (!active || !payload || payload.length === 0) return null;
    const data = payload[0]?.payload;
    if (!data) return null;
    return (
      <div style={{
        background: 'var(--bg-secondary)', border: '1px solid var(--border)',
        borderRadius: 8, padding: '10px 14px', fontSize: 12
      }}>
        <div style={{ fontWeight: 600, marginBottom: 6, color: 'var(--text-secondary)' }}>
          Window {data.index} ({data.date})
        </div>
        <div style={{ color: 'var(--accent-blue)' }}>Predicted median: {data.predictedMedian}</div>
        <div style={{ color: 'var(--text-muted)' }}>
          Cone: {data.predictedLower} – {data.predictedUpper}
        </div>
        <div style={{ color: data.withinCone ? 'var(--accent-green)' : 'var(--accent-red)', fontWeight: 600 }}>
          Actual: {data.actualPrice} {data.withinCone ? '✓ in cone' : '✗ outside cone'}
        </div>
      </div>
    );
  };

  return (
    <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Header */}
      <div>
        <div className="chart-title">🎯 Model Backtest — Validation</div>
        <div className="chart-subtitle">
          Tests GBM against 20 historical windows for {symbol || 'this stock'}.
          Each window forecasts using only data available at that past date,
          then checks against what actually happened.
        </div>
      </div>

      {/* Controls */}
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16, flexWrap: 'wrap' }}>
        <div className="form-group" style={{ marginBottom: 0 }}>
          <label className="form-label">Forecast Horizon</label>
          <select
            className="form-select"
            value={forecastDays}
            onChange={e => setForecastDays(parseInt(e.target.value))}
            style={{ minWidth: 140 }}
          >
            <option value={10}>10 trading days</option>
            <option value={21}>21 days (1 month)</option>
            <option value={30}>30 days</option>
            <option value={63}>63 days (3 months)</option>
            <option value={90}>90 days</option>
          </select>
        </div>
        <button
          className="btn btn-primary"
          onClick={handleRunBacktest}
          disabled={loading || !symbol}
        >
          {loading ? (
            <><div className="spinner" style={{ width: 16, height: 16, borderWidth: 2 }} /> Running 20 windows...</>
          ) : (
            '▶ Run Backtest'
          )}
        </button>
      </div>

      {error && <div className="error-box">{error}</div>}

      {loading && (
        <div className="loading-overlay">
          <div className="spinner" />
          <span style={{ fontSize: 13 }}>
            Re-running GBM at 20 historical points — this takes longer than a live forecast
          </span>
        </div>
      )}

      {/* Results */}
      {result && !loading && (
        <>
          {/* Headline stats */}
          <div className="stat-grid">
            <div className="stat-card">
              <div className="stat-label">Calibration Score</div>
              <div className="stat-value" style={{ color: getVerdictColor(result.verdict) }}>
                {result.calibrationScorePct.toFixed(1)}%
              </div>
              <div className="stat-sub">Target: ~90%</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Verdict</div>
              <div className="stat-value" style={{ color: getVerdictColor(result.verdict), fontSize: 16 }}>
                {result.verdict}
              </div>
              <div className="stat-sub">{result.totalWindows} windows tested</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Mean Abs. Error</div>
              <div className="stat-value">{result.meanAbsErrorPct.toFixed(2)}%</div>
              <div className="stat-sub">Median prediction vs actual</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Forecast Horizon</div>
              <div className="stat-value">{result.forecastDays}d</div>
              <div className="stat-sub">Trading days ahead</div>
            </div>
          </div>

          {/* Explanation of verdict */}
          <div style={{
            fontSize: 13, color: 'var(--text-secondary)',
            background: 'var(--bg-tertiary)', borderRadius: 8, padding: '12px 16px'
          }}>
            {result.verdict === 'Well Calibrated' && (
              <>✅ The model's 90% confidence cone contained the real price in {result.calibrationScorePct.toFixed(0)}% of historical tests — close to the expected 90%. This suggests the uncertainty range is realistic.</>
            )}
            {result.verdict === 'Overconfident' && (
              <>⚠️ The real price only fell inside the cone {result.calibrationScorePct.toFixed(0)}% of the time, well below the target 90%. The model is underestimating real-world volatility — treat the cone as narrower than reality.</>
            )}
            {result.verdict === 'Underconfident' && (
              <>ℹ️ The real price fell inside the cone {result.calibrationScorePct.toFixed(0)}% of the time, above the target 90%. The model's uncertainty range may be wider than necessary for this stock.</>
            )}
          </div>

          {/* Chart: predicted cone vs actual across all windows */}
          <ResponsiveContainer width="100%" height={320}>
            <ComposedChart data={chartData} margin={{ top: 5, right: 20, left: 20, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" opacity={0.5} />
              <XAxis dataKey="date" tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                tickLine={false} axisLine={{ stroke: 'var(--border)' }} />
              <YAxis tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                tickLine={false} axisLine={false} width={60} />
              <Tooltip content={<CustomTooltip />} />

              {/* Predicted cone */}
              <Line dataKey="predictedUpper" stroke="rgba(59,130,246,0.4)" strokeWidth={1}
                strokeDasharray="3 3" dot={false} name="95th Percentile" />
              <Line dataKey="predictedLower" stroke="rgba(59,130,246,0.4)" strokeWidth={1}
                strokeDasharray="3 3" dot={false} name="5th Percentile" />
              <Line dataKey="predictedMedian" stroke="var(--accent-blue)" strokeWidth={2}
                dot={false} name="Predicted Median" />

              {/* Actual outcomes, colour-coded by whether they landed in the cone */}
              <Scatter
                dataKey="actualPrice"
                name="Actual Price"
                fill="var(--accent-green)"
                shape={(props) => {
                  const { cx, cy, payload } = props;
                  const color = payload.withinCone ? 'var(--accent-green)' : 'var(--accent-red)';
                  return <circle cx={cx} cy={cy} r={5} fill={color} stroke="var(--bg-primary)" strokeWidth={1.5} />;
                }}
              />
            </ComposedChart>
          </ResponsiveContainer>

          <div style={{ display: 'flex', gap: 20, fontSize: 12, color: 'var(--text-secondary)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <div style={{ width: 10, height: 10, borderRadius: '50%', background: 'var(--accent-green)' }} />
              Actual price within predicted cone
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <div style={{ width: 10, height: 10, borderRadius: '50%', background: 'var(--accent-red)' }} />
              Actual price outside predicted cone
            </div>
          </div>
        </>
      )}

      {!result && !loading && !error && (
        <div style={{ color: 'var(--text-muted)', fontSize: 13, textAlign: 'center', padding: 24 }}>
          Run a backtest to see how accurately GBM would have predicted {symbol || 'this stock'}'s
          price at 20 different points in its 2-year history.
        </div>
      )}
    </div>
  );
}

export default BacktestPanel;
