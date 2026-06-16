import React, { useMemo } from 'react';
import {
  ComposedChart, Line, Area, ReferenceLine,
  XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer
} from 'recharts';

/**
 * FintechChart — Recharts ComposedChart for Fintech Mode.
 *
 * Displays:
 *  - Historical price (green line)
 *  - GBM forecast cone: median (blue), 5th/95th percentile (shaded area)
 *  - Bollinger Bands (upper/lower dashed lines, middle dotted)
 *  - Today marker (vertical reference line)
 *
 * Data is merged into a single array where historical and forecast
 * data occupy different time ranges on the x-axis.
 */
function FintechChart({ result }) {
  /**
   * Merge historical and forecast data into one chart dataset.
   * Each point has: timestamp, historical, median, upper, lower, bbUpper, bbLower, bbMiddle
   */
  const chartData = useMemo(() => {
    if (!result) return [];

    const data = [];

    // ── HISTORICAL SECTION ──────────────────────────────────────────────────
    const prices    = result.historicalPrices    || [];
    const timestamps = result.historicalTimestamps || [];
    const bbMiddle  = result.bollingerMiddle     || [];
    const bbUpper   = result.bollingerUpper      || [];
    const bbLower   = result.bollingerLower      || [];

    for (let i = 0; i < prices.length; i++) {
      const ts = timestamps[i];
      const date = ts ? new Date(ts * 1000) : null;

      data.push({
        time: date ? date.toLocaleDateString('en-GB', { month: 'short', day: 'numeric' }) : '',
        timestamp: ts,
        historical: parseFloat(prices[i]?.toFixed(2)),
        bbMiddle:   bbMiddle[i] != null ? parseFloat(bbMiddle[i]?.toFixed(2)) : null,
        bbUpper:    bbUpper[i]  != null ? parseFloat(bbUpper[i]?.toFixed(2))  : null,
        bbLower:    bbLower[i]  != null ? parseFloat(bbLower[i]?.toFixed(2))  : null,
        isToday: false,
      });
    }

    // Mark the last historical point as "today"
    if (data.length > 0) {
      data[data.length - 1].isToday = true;
    }

    // ── FORECAST SECTION ────────────────────────────────────────────────────
    const forecastTs  = result.forecastTimestamps || [];
    const medianPath  = result.medianPath         || [];
    const upperPath   = result.upperBandPath      || [];
    const lowerPath   = result.lowerBandPath      || [];

    // Only show every Nth forecast point to reduce chart density
    // For short forecasts (<30 days), show all. For longer, sample.
    const step = forecastTs.length > 60 ? Math.ceil(forecastTs.length / 60) : 1;

    for (let i = 0; i < forecastTs.length; i += step) {
      const ts   = forecastTs[i];
      const date = ts ? new Date(ts * 1000) : null;

      data.push({
        time: date ? date.toLocaleDateString('en-GB', { month: 'short', day: 'numeric' }) : '',
        timestamp: ts,
        median: medianPath[i]   != null ? parseFloat(medianPath[i]?.toFixed(2))  : null,
        upper:  upperPath[i]    != null ? parseFloat(upperPath[i]?.toFixed(2))   : null,
        lower:  lowerPath[i]    != null ? parseFloat(lowerPath[i]?.toFixed(2))   : null,
        isForecast: true,
      });
    }

    return data;
  }, [result]);

  // Find the index where forecast starts (for the "Today" reference line)
  const todayIndex = chartData.findIndex(d => d.isToday);
  const todayTime  = todayIndex >= 0 ? chartData[todayIndex].time : null;

  // Custom tooltip
  const CustomTooltip = ({ active, payload, label }) => {
    if (!active || !payload || payload.length === 0) return null;
    return (
      <div style={{
        background: 'var(--bg-secondary)',
        border: '1px solid var(--border)',
        borderRadius: 8,
        padding: '10px 14px',
        fontSize: 12,
        minWidth: 160
      }}>
        <div style={{ fontWeight: 600, marginBottom: 8, color: 'var(--text-secondary)' }}>
          {label}
        </div>
        {payload.map((entry, i) => (
          entry.value != null && (
            <div key={i} style={{
              display: 'flex', justifyContent: 'space-between',
              gap: 16, color: entry.color, marginBottom: 2
            }}>
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
        {result.companyName} ({result.symbol}) — Fintech Mode
      </div>
      <div className="chart-subtitle">
        GBM Monte Carlo forecast cone · Bollinger Bands · 500 simulated paths
      </div>

      {/* Legend */}
      <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap', marginBottom: 16, fontSize: 12 }}>
        {[
          { color: 'var(--accent-green)',  label: 'Historical Price' },
          { color: 'var(--accent-blue)',   label: 'GBM Median Forecast' },
          { color: 'rgba(59,130,246,0.3)', label: '5th–95th Percentile Cone', dashed: false },
          { color: 'var(--accent-amber)',  label: 'Bollinger Upper/Lower', dashed: true },
          { color: 'var(--text-muted)',    label: 'Bollinger Middle SMA', dashed: true },
        ].map((item, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{
              width: item.dashed ? 20 : 16,
              height: item.dashed ? 2 : 3,
              background: item.color,
              borderRadius: 2,
              borderTop: item.dashed ? `2px dashed ${item.color}` : 'none',
              backgroundColor: item.dashed ? 'transparent' : item.color
            }} />
            <span style={{ color: 'var(--text-secondary)' }}>{item.label}</span>
          </div>
        ))}
      </div>

      <ResponsiveContainer width="100%" height={420}>
        <ComposedChart data={chartData} margin={{ top: 5, right: 20, left: 20, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" opacity={0.5} />

          <XAxis
            dataKey="time"
            tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
            tickLine={false}
            axisLine={{ stroke: 'var(--border)' }}
            interval={Math.floor(chartData.length / 8)}
          />

          <YAxis
            tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
            tickLine={false}
            axisLine={false}
            tickFormatter={(v) => `${currency}${v}`}
            width={70}
          />

          <Tooltip content={<CustomTooltip />} />

          {/* Today marker */}
          {todayTime && (
            <ReferenceLine
              x={todayTime}
              stroke="var(--text-muted)"
              strokeDasharray="4 4"
              label={{
                value: 'Today',
                position: 'top',
                fill: 'var(--text-muted)',
                fontSize: 11
              }}
            />
          )}

          {/* GBM Cone: 5th–95th percentile shaded area */}
          <Area
            dataKey="upper"
            data={chartData}
            stroke="none"
            fill="rgba(59,130,246,0.15)"
            connectNulls={false}
            name="95th Percentile"
            legendType="none"
          />
          <Area
            dataKey="lower"
            data={chartData}
            stroke="none"
            fill="var(--bg-secondary)"
            connectNulls={false}
            name="5th Percentile"
            legendType="none"
          />

          {/* Bollinger Bands */}
          <Line
            dataKey="bbUpper"
            stroke="var(--accent-amber)"
            strokeWidth={1}
            strokeDasharray="4 4"
            dot={false}
            connectNulls={false}
            name="BB Upper"
          />
          <Line
            dataKey="bbLower"
            stroke="var(--accent-amber)"
            strokeWidth={1}
            strokeDasharray="4 4"
            dot={false}
            connectNulls={false}
            name="BB Lower"
          />
          <Line
            dataKey="bbMiddle"
            stroke="var(--text-muted)"
            strokeWidth={1}
            strokeDasharray="2 4"
            dot={false}
            connectNulls={false}
            name="BB Middle (SMA20)"
          />

          {/* Historical price */}
          <Line
            dataKey="historical"
            stroke="var(--accent-green)"
            strokeWidth={2}
            dot={false}
            connectNulls={false}
            name="Historical Price"
          />

          {/* GBM Median forecast line */}
          <Line
            dataKey="median"
            stroke="var(--accent-blue)"
            strokeWidth={2.5}
            dot={false}
            connectNulls={false}
            name="GBM Median"
            strokeDasharray="6 3"
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

export default FintechChart;
