import React, { useMemo } from 'react';
import {
  ComposedChart, Line, Area,
  XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, ReferenceLine
} from 'recharts';

/**
 * ALevelChart — Recharts chart for A-Level comparison mode.
 *
 * Displays:
 *  - Historical price (green)
 *  - 20-day SMA (amber)
 *  - Log-linear regression forecast (blue dashed)
 *  - ±1σ confidence bands (light blue area)
 *  - ±2σ confidence bands (very light blue outer area)
 *  - Today marker
 */
function ALevelChart({ result }) {
  const chartData = useMemo(() => {
    if (!result) return [];
    const data = [];

    const prices     = result.historicalPrices    || [];
    const timestamps = result.historicalTimestamps || [];
    const sma20      = result.sma20               || [];

    // Historical section
    for (let i = 0; i < prices.length; i++) {
      const ts   = timestamps[i];
      const date = ts ? new Date(ts * 1000) : null;
      data.push({
        time:      date ? date.toLocaleDateString('en-GB', { month: 'short', day: 'numeric' }) : '',
        timestamp: ts,
        historical: parseFloat(prices[i]?.toFixed(2)),
        sma20:      sma20[i] != null ? parseFloat(sma20[i]?.toFixed(2)) : null,
        isToday:    i === prices.length - 1,
      });
    }

    // Forecast section
    const forecastTs   = result.forecastTimestamps || [];
    const regression   = result.regressionLine     || [];
    const upper1       = result.upperBand1Sigma    || [];
    const lower1       = result.lowerBand1Sigma    || [];
    const upper2       = result.upperBand2Sigma    || [];
    const lower2       = result.lowerBand2Sigma    || [];

    const step = forecastTs.length > 60 ? Math.ceil(forecastTs.length / 60) : 1;

    for (let i = 0; i < forecastTs.length; i += step) {
      const ts   = forecastTs[i];
      const date = ts ? new Date(ts * 1000) : null;
      data.push({
        time:       date ? date.toLocaleDateString('en-GB', { month: 'short', day: 'numeric' }) : '',
        timestamp:  ts,
        regression: regression[i] != null ? parseFloat(regression[i]?.toFixed(2)) : null,
        upper1:     upper1[i]     != null ? parseFloat(upper1[i]?.toFixed(2))     : null,
        lower1:     lower1[i]     != null ? parseFloat(lower1[i]?.toFixed(2))     : null,
        upper2:     upper2[i]     != null ? parseFloat(upper2[i]?.toFixed(2))     : null,
        lower2:     lower2[i]     != null ? parseFloat(lower2[i]?.toFixed(2))     : null,
        isForecast: true,
      });
    }

    return data;
  }, [result]);

  const todayIndex = chartData.findIndex(d => d.isToday);
  const todayTime  = todayIndex >= 0 ? chartData[todayIndex].time : null;

  const CustomTooltip = ({ active, payload, label }) => {
    if (!active || !payload || payload.length === 0) return null;
    return (
      <div style={{
        background: 'var(--bg-secondary)', border: '1px solid var(--border)',
        borderRadius: 8, padding: '10px 14px', fontSize: 12
      }}>
        <div style={{ fontWeight: 600, marginBottom: 8, color: 'var(--text-secondary)' }}>{label}</div>
        {payload.map((entry, i) => (
          entry.value != null && (
            <div key={i} style={{ display: 'flex', justifyContent: 'space-between',
              gap: 16, color: entry.color, marginBottom: 2 }}>
              <span>{entry.name}</span>
              <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
                {Number(entry.value).toFixed(2)}
              </span>
            </div>
          )
        ))}
      </div>
    );
  };

  if (!result || chartData.length === 0) return null;

  const currency = result.currency === 'GBp' ? 'p' :
                   result.currency === 'USD'  ? '$' : result.currency;

  return (
    <div className="chart-container">
      <div className="chart-title">
        {result.companyName} ({result.symbol}) — A-Level Mode
      </div>
      <div className="chart-subtitle">
        Log-linear regression · ±1σ and ±2σ confidence bands · 20-day SMA · R² = {Number(result.rSquared || 0).toFixed(4)}
      </div>

      {/* Legend */}
      <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap', marginBottom: 16, fontSize: 12 }}>
        {[
          { color: 'var(--accent-green)',  label: 'Historical Price' },
          { color: 'var(--accent-amber)',  label: '20-day SMA' },
          { color: 'var(--accent-blue)',   label: 'Regression Forecast', dashed: true },
          { color: 'rgba(59,130,246,0.3)', label: '±1σ Band' },
          { color: 'rgba(59,130,246,0.15)', label: '±2σ Band' },
        ].map((item, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{
              width: 16, height: 3,
              background: item.color, borderRadius: 2,
              border: item.dashed ? '1px dashed var(--accent-blue)' : 'none',
              backgroundColor: item.dashed ? 'transparent' : item.color
            }} />
            <span style={{ color: 'var(--text-secondary)' }}>{item.label}</span>
          </div>
        ))}
      </div>

      <ResponsiveContainer width="100%" height={420}>
        <ComposedChart data={chartData} margin={{ top: 5, right: 20, left: 20, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" opacity={0.5} />
          <XAxis dataKey="time" tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
            tickLine={false} axisLine={{ stroke: 'var(--border)' }}
            interval={Math.floor(chartData.length / 8)} />
          <YAxis tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
            tickLine={false} axisLine={false}
            tickFormatter={(v) => `${currency}${v}`} width={70} />
          <Tooltip content={<CustomTooltip />} />

          {todayTime && (
            <ReferenceLine x={todayTime} stroke="var(--text-muted)" strokeDasharray="4 4"
              label={{ value: 'Today', position: 'top', fill: 'var(--text-muted)', fontSize: 11 }} />
          )}

          {/* ±2σ outer band */}
          <Area dataKey="upper2" stroke="none" fill="rgba(59,130,246,0.08)"
            connectNulls={false} name="+2σ Upper" legendType="none" />
          <Area dataKey="lower2" stroke="none" fill="var(--bg-secondary)"
            connectNulls={false} name="-2σ Lower" legendType="none" />

          {/* ±1σ inner band */}
          <Area dataKey="upper1" stroke="rgba(59,130,246,0.4)" strokeWidth={1}
            fill="rgba(59,130,246,0.15)" strokeDasharray="3 3"
            connectNulls={false} name="+1σ Upper" />
          <Area dataKey="lower1" stroke="rgba(59,130,246,0.4)" strokeWidth={1}
            fill="var(--bg-secondary)" strokeDasharray="3 3"
            connectNulls={false} name="-1σ Lower" />

          {/* SMA20 */}
          <Line dataKey="sma20" stroke="var(--accent-amber)" strokeWidth={1.5}
            dot={false} connectNulls={false} name="20-day SMA" />

          {/* Historical price */}
          <Line dataKey="historical" stroke="var(--accent-green)" strokeWidth={2}
            dot={false} connectNulls={false} name="Historical Price" />

          {/* Regression forecast */}
          <Line dataKey="regression" stroke="var(--accent-blue)" strokeWidth={2}
            strokeDasharray="6 3" dot={false} connectNulls={false} name="Regression" />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

export default ALevelChart;
