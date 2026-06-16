import React from 'react';

/**
 * SentimentPanel — displays scored news headlines with colour coding.
 *
 * Shows up to 10 headlines from Yahoo Finance RSS, Google News, Reddit.
 * Each headline has a coloured score bar and badge.
 * Green = bullish, Red = bearish, Amber = neutral.
 */
function SentimentPanel({ sentiment, loading }) {
  if (loading) {
    return (
      <div className="card">
        <div className="loading-overlay">
          <div className="spinner" />
          <span style={{ fontSize: 13 }}>Fetching news sentiment...</span>
        </div>
      </div>
    );
  }

  if (!sentiment) return null;

  const getScoreColor = (score) => {
    if (score > 0.05)  return 'var(--accent-green)';
    if (score < -0.05) return 'var(--accent-red)';
    return 'var(--accent-amber)';
  };

  const getScoreBg = (score) => {
    if (score > 0.05)  return 'rgba(16,185,129,0.15)';
    if (score < -0.05) return 'rgba(239,68,68,0.15)';
    return 'rgba(245,158,11,0.15)';
  };

  // Overall sentiment gauge — simple bar
  const gaugeWidth = Math.abs(sentiment.overallScore) * 50; // 0-50%
  const gaugeColor = getScoreColor(sentiment.overallScore);
  const gaugeLeft = sentiment.overallScore >= 0 ? '50%' : `${50 - gaugeWidth}%`;

  return (
    <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <div className="chart-title">📰 News Sentiment</div>
          <div className="chart-subtitle">
            {sentiment.headlineCount} headlines from {' '}
            Yahoo Finance · Google News · Reddit WSB
          </div>
        </div>
        <div style={{ textAlign: 'right' }}>
          <span className={`badge badge-${sentiment.overallLabel?.toLowerCase()}`}>
            {sentiment.overallLabel}
          </span>
          <div style={{
            fontFamily: 'var(--font-mono)',
            fontSize: 20,
            fontWeight: 700,
            color: getScoreColor(sentiment.overallScore),
            marginTop: 4
          }}>
            {sentiment.overallScore >= 0 ? '+' : ''}{Number(sentiment.overallScore).toFixed(3)}
          </div>
        </div>
      </div>

      {/* Sentiment Gauge */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between',
          fontSize: 11, color: 'var(--text-muted)', marginBottom: 6 }}>
          <span>← Bearish</span>
          <span>Neutral</span>
          <span>Bullish →</span>
        </div>
        <div style={{
          position: 'relative',
          height: 8,
          background: 'var(--bg-tertiary)',
          borderRadius: 4,
          overflow: 'hidden'
        }}>
          {/* Centre marker */}
          <div style={{
            position: 'absolute', left: '50%', top: 0, bottom: 0,
            width: 2, background: 'var(--border-light)', transform: 'translateX(-50%)'
          }} />
          {/* Score bar */}
          <div style={{
            position: 'absolute',
            left: gaugeLeft,
            width: `${gaugeWidth}%`,
            top: 0, bottom: 0,
            background: gaugeColor,
            borderRadius: 4,
            transition: 'all 0.5s ease'
          }} />
        </div>
      </div>

      {/* Headlines List */}
      <div>
        <div style={{ fontSize: 12, fontWeight: 600, textTransform: 'uppercase',
          letterSpacing: '0.08em', color: 'var(--text-muted)', marginBottom: 12 }}>
          Top Headlines
        </div>

        {sentiment.headlines && sentiment.headlines.length > 0 ? (
          sentiment.headlines.map((h, i) => (
            <div key={i} className="headline-item">
              {/* Colour bar on left */}
              <div className="headline-score-bar"
                style={{ background: getScoreColor(h.score) }} />

              {/* Headline content */}
              <div style={{ flex: 1 }}>
                <div className="headline-text">
                  {h.url ? (
                    <a href={h.url} target="_blank" rel="noopener noreferrer"
                      style={{ color: 'var(--text-primary)', textDecoration: 'none' }}
                      onMouseEnter={e => e.target.style.color = 'var(--accent-blue)'}
                      onMouseLeave={e => e.target.style.color = 'var(--text-primary)'}
                    >
                      {h.headline}
                    </a>
                  ) : h.headline}
                </div>
                <div className="headline-meta">
                  <span style={{
                    background: 'var(--bg-tertiary)',
                    padding: '1px 6px',
                    borderRadius: 3,
                    fontSize: 10
                  }}>
                    {h.source}
                  </span>
                  <span>{h.publishedAt?.substring(0, 16)}</span>
                </div>
              </div>

              {/* Score badge */}
              <div className="headline-score-badge" style={{
                background: getScoreBg(h.score),
                color: getScoreColor(h.score)
              }}>
                {h.score >= 0 ? '+' : ''}{Number(h.score).toFixed(2)}
              </div>
            </div>
          ))
        ) : (
          <div style={{ color: 'var(--text-muted)', fontSize: 13, textAlign: 'center', padding: 24 }}>
            No headlines found for this stock.
            <br />
            <span style={{ fontSize: 12 }}>RSS feeds may be temporarily unavailable.</span>
          </div>
        )}
      </div>
    </div>
  );
}

export default SentimentPanel;
