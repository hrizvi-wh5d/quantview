import React from 'react';
import { MODE_FINTECH, SIGNAL_OVERBOUGHT, SIGNAL_OVERSOLD } from '../services/constants';

/**
 * StatsPanel — displays all computed fintech metrics in a grid.
 *
 * Shows: current price, GBM predictions, VaR, Sharpe Ratio,
 * GARCH volatility, Bollinger signal, sentiment score.
 */
function StatsPanel({ result, sentiment, mode }) {
  if (!result) return null;

  const fmt = (n, decimals = 2) =>
    n != null ? Number(n).toFixed(decimals) : '—';

  const fmtPct = (n) =>
    n != null ? `${(Number(n) * 100).toFixed(2)}%` : '—';

  const fmtCcy = (n, currency) => {
    if (n == null) return '—';
    const sym = currency === 'GBp' ? 'p' : currency === 'USD' ? '$' : currency === 'GBP' ? '£' : '';
    return `${sym}${Math.abs(Number(n)).toFixed(2)}`;
  };

  const currency = result.currency;
  const isFintech = mode === MODE_FINTECH;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

      {/* ── PRICE SUMMARY ─────────────────────────────────────────── */}
      <div className="card-sm">
        <div style={{ fontSize: 12, fontWeight: 600, textTransform: 'uppercase',
          letterSpacing: '0.08em', color: 'var(--text-muted)', marginBottom: 12 }}>
          Price Summary
        </div>
        <div className="stat-grid">
          <div className="stat-card">
            <div className="stat-label">Current Price</div>
            <div className="stat-value blue">{fmtCcy(result.currentPrice, currency)}</div>
            <div className="stat-sub">{result.symbol}</div>
          </div>

          {isFintech && (
            <>
              <div className="stat-card">
                <div className="stat-label">GBM Median</div>
                <div className="stat-value">{fmtCcy(result.gbmMedianPrice, currency)}</div>
                <div className="stat-sub">{result.daysAhead}d forecast</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Best Case (95th)</div>
                <div className="stat-value positive">{fmtCcy(result.gbmUpperPrice, currency)}</div>
                <div className="stat-sub">95th percentile</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Worst Case (5th)</div>
                <div className="stat-value negative">{fmtCcy(result.gbmLowerPrice, currency)}</div>
                <div className="stat-sub">5th percentile</div>
              </div>
            </>
          )}

          {!isFintech && (
            <div className="stat-card">
              <div className="stat-label">Regression Forecast</div>
              <div className="stat-value">{fmtCcy(result.regressionPrediction, currency)}</div>
              <div className="stat-sub">{result.daysAhead}d ahead</div>
            </div>
          )}
        </div>
      </div>

      {/* ── FINTECH METRICS ───────────────────────────────────────── */}
      {isFintech && (
        <>
          {/* Risk Metrics */}
          <div className="card-sm">
            <div style={{ fontSize: 12, fontWeight: 600, textTransform: 'uppercase',
              letterSpacing: '0.08em', color: 'var(--text-muted)', marginBottom: 12 }}>
              Risk Metrics
            </div>
            <div className="stat-grid">
              <div className="stat-card">
                <div className="stat-label">VaR 95%</div>
                <div className="stat-value negative">{fmtPct(result.var95Pct)}</div>
                <div className="stat-sub">{fmtCcy(result.var95Abs, currency)} max loss</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">VaR 99%</div>
                <div className="stat-value negative">{fmtPct(result.var99Pct)}</div>
                <div className="stat-sub">{fmtCcy(result.var99Abs, currency)} max loss</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Sharpe Ratio</div>
                <div className={`stat-value ${result.sharpeRatio > 1 ? 'positive' : 'negative'}`}>
                  {fmt(result.sharpeRatio)}
                </div>
                <div className="stat-sub">{result.sharpeLabel}</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">GARCH Volatility</div>
                <div className="stat-value purple">
                  {fmtPct(result.garchAnnualisedVolatility)}
                </div>
                <div className="stat-sub">Annualised σ</div>
              </div>
            </div>
          </div>

          {/* Model Parameters */}
          <div className="card-sm">
            <div style={{ fontSize: 12, fontWeight: 600, textTransform: 'uppercase',
              letterSpacing: '0.08em', color: 'var(--text-muted)', marginBottom: 12 }}>
              GBM Parameters
            </div>
            <div className="stat-grid">
              <div className="stat-card">
                <div className="stat-label">Drift (μ)</div>
                <div className={`stat-value ${result.drift > 0 ? 'positive' : 'negative'}`}>
                  {fmtPct(result.drift ? result.drift : 0)}
                </div>
                <div className="stat-sub">Annual drift</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Volatility (σ)</div>
                <div className="stat-value purple">{fmtPct(result.volatility)}</div>
                <div className="stat-sub">Annual vol</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Bollinger Signal</div>
                <div className={`stat-value ${
                  result.bollingerSignal === SIGNAL_OVERBOUGHT ? 'negative' :
                  result.bollingerSignal === SIGNAL_OVERSOLD   ? 'positive' : ''
                }`}>
                  {result.bollingerSignal || 'NEUTRAL'}
                </div>
                <div className="stat-sub">±2σ bands</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Data Points</div>
                <div className="stat-value">{result.historicalPrices?.length || '—'}</div>
                <div className="stat-sub">Trading days</div>
              </div>
            </div>
          </div>

          {/* Sentiment */}
          {sentiment && (
            <div className="card-sm">
              <div style={{ fontSize: 12, fontWeight: 600, textTransform: 'uppercase',
                letterSpacing: '0.08em', color: 'var(--text-muted)', marginBottom: 12 }}>
                Sentiment Analysis
              </div>
              <div className="stat-grid">
                <div className="stat-card">
                  <div className="stat-label">Sentiment Score</div>
                  <div className={`stat-value ${
                    sentiment.overallScore > 0.05 ? 'positive' :
                    sentiment.overallScore < -0.05 ? 'negative' : ''
                  }`}>
                    {fmt(sentiment.overallScore)}
                  </div>
                  <div className="stat-sub">{sentiment.overallLabel}</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Adj. Prediction</div>
                  <div className="stat-value blue">
                    {fmtCcy(result.sentimentAdjustedPrice || 
                      result.gbmMedianPrice * (1 + 0.05 * (sentiment.overallScore || 0)),
                      currency
                    )}
                  </div>
                  <div className="stat-sub">Sentiment-adjusted</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Headlines</div>
                  <div className="stat-value">{sentiment.headlineCount || 0}</div>
                  <div className="stat-sub">Analysed</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Sources</div>
                  <div style={{ fontSize: 11, marginTop: 4, color: 'var(--text-secondary)' }}>
                    <div>Yahoo: {sentiment.yahooCount || 0}</div>
                    <div>Google: {sentiment.googleCount || 0}</div>
                    <div>Reddit: {sentiment.redditCount || 0}</div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {/* ── A-LEVEL METRICS ──────────────────────────────────────── */}
      {!isFintech && (
        <div className="card-sm">
          <div style={{ fontSize: 12, fontWeight: 600, textTransform: 'uppercase',
            letterSpacing: '0.08em', color: 'var(--text-muted)', marginBottom: 12 }}>
            Regression Statistics
          </div>
          <div className="stat-grid">
            <div className="stat-card">
              <div className="stat-label">R² Coefficient</div>
              <div className={`stat-value ${result.rSquared > 0.7 ? 'positive' : result.rSquared > 0.4 ? '' : 'negative'}`}>
                {fmt(result.rSquared)}
              </div>
              <div className="stat-sub">{
                result.rSquared > 0.8 ? 'Strong fit' :
                result.rSquared > 0.5 ? 'Moderate fit' : 'Weak fit'
              }</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">+1σ Upper</div>
              <div className="stat-value positive">
                {fmtCcy(result.upperBand1Sigma?.[result.upperBand1Sigma.length - 1], currency)}
              </div>
              <div className="stat-sub">~68% confidence</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">+2σ Upper</div>
              <div className="stat-value">
                {fmtCcy(result.upperBand2Sigma?.[result.upperBand2Sigma.length - 1], currency)}
              </div>
              <div className="stat-sub">~95% confidence</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">-2σ Lower</div>
              <div className="stat-value negative">
                {fmtCcy(result.lowerBand2Sigma?.[result.lowerBand2Sigma.length - 1], currency)}
              </div>
              <div className="stat-sub">~95% confidence</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default StatsPanel;
