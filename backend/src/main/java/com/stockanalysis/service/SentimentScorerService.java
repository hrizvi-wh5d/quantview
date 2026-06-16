package com.stockanalysis.service;

import com.stockanalysis.model.SentimentDtos.SentimentHeadline;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SentimentScorerService — VADER-equivalent sentiment analysis in Java.
 *
 * ─── WHAT IS VADER? ──────────────────────────────────────────────────────────
 *
 * VADER (Valence Aware Dictionary and sEntiment Reasoner) is a lexicon and
 * rule-based sentiment analysis tool specifically designed for social media text.
 * Developed by Hutto & Gilbert (2014) at Georgia Tech.
 *
 * We implement a simplified version appropriate for financial news headlines:
 *  - Hardcoded positive and negative finance-specific word lists
 *  - Score: +0.1 per positive word, -0.1 per negative word
 *  - Clamped to [-1.0, +1.0]
 *  - Case-insensitive matching
 *
 * ─── WHY NOT USE THE REAL VADER? ─────────────────────────────────────────────
 *
 * VADER is a Python library. We want a pure Java implementation with:
 *  1. No additional dependencies (keeps pom.xml clean)
 *  2. Finance-domain specific vocabulary (generic VADER misses finance terms)
 *  3. Full control over the word lists
 *  4. Sub-millisecond performance per headline
 *
 * ─── WORD LIST DESIGN ────────────────────────────────────────────────────────
 *
 * Positive words: financial outperformance, price rises, analyst upgrades,
 *                 strong fundamentals, market optimism
 *
 * Negative words: financial underperformance, price falls, analyst downgrades,
 *                 weak fundamentals, market pessimism, regulatory issues
 *
 * The lists are conservative — only words with clear directional sentiment.
 * Ambiguous words (e.g. "change", "move") are excluded.
 *
 * ─── SCORING FORMULA ─────────────────────────────────────────────────────────
 *
 * score = clamp(positiveMatches × 0.1 - negativeMatches × 0.1, -1.0, +1.0)
 *
 * Label thresholds (matching VADER convention):
 *   score > +0.05  → "Bullish"
 *   score < -0.05  → "Bearish"
 *   otherwise      → "Neutral"
 *
 * ─── LIMITATIONS ─────────────────────────────────────────────────────────────
 *
 * This approach does NOT handle:
 *  - Negation: "not bullish" scores as 0 (misses the negative)
 *  - Context: "earnings miss" requires two words to be negative
 *  - Sarcasm: common in Reddit posts
 *  - Intensifiers: "very bullish" scores same as "bullish"
 *
 * For production: upgrade to FinBERT (a BERT model fine-tuned on financial text)
 * or integrate a paid sentiment API (Bloomberg, RavenPack).
 * For this project: the word list approach is transparent, fast, and free.
 */
@Service
public class SentimentScorerService {

    // Score per matched word (positive or negative)
    private static final double WORD_SCORE = 0.1;

    // Thresholds for label assignment
    private static final double BULLISH_THRESHOLD = 0.05;
    private static final double BEARISH_THRESHOLD = -0.05;

    /**
     * Finance-positive words — associated with price rises and good news.
     * All lowercase for case-insensitive comparison.
     */
    private static final Set<String> POSITIVE_WORDS = new HashSet<>(Arrays.asList(
        // Price action
        "surge", "surged", "surges", "surging",
        "rally", "rallied", "rallies", "rallying",
        "rise", "rises", "rose", "rising", "soar", "soared", "soaring",
        "jump", "jumped", "jumping", "gain", "gained", "gains",
        "climb", "climbed", "climbing", "spike", "spiked",
        "rebound", "rebounded", "recovery", "recover",

        // Fundamentals
        "profit", "profits", "profitable", "profitability",
        "record", "records", "beat", "beats", "exceeded", "exceeds",
        "growth", "grew", "growing", "expand", "expanded", "expansion",
        "revenue", "earnings", "strong", "stronger", "strongest",
        "outperform", "outperformed", "outperforming",
        "dividend", "dividends", "buyback", "buybacks",

        // Analyst sentiment
        "upgrade", "upgraded", "upgrades", "buy", "overweight",
        "outperform", "positive", "bullish", "optimistic",
        "target", "raised", "raise", "upside",

        // Market conditions
        "boom", "booming", "breakthrough", "innovative", "innovation",
        "partnership", "deal", "contract", "win", "won", "winning",
        "approval", "approved", "launch", "launched",
        "momentum", "opportunity", "opportunities", "promising",
        "high", "highs", "peak", "peaks", "robust", "solid"
    ));

    /**
     * Finance-negative words — associated with price falls and bad news.
     * All lowercase for case-insensitive comparison.
     */
    private static final Set<String> NEGATIVE_WORDS = new HashSet<>(Arrays.asList(
        // Price action
        "crash", "crashed", "crashing", "fall", "falls", "fell", "falling",
        "drop", "dropped", "dropping", "decline", "declined", "declining",
        "plunge", "plunged", "plunging", "tumble", "tumbled", "tumbling",
        "sink", "sank", "sinking", "slump", "slumped", "slumping",
        "collapse", "collapsed", "collapsing",

        // Fundamentals
        "loss", "losses", "miss", "missed", "misses", "disappoint",
        "disappointed", "disappointing", "disappoints",
        "weak", "weaker", "weakest", "poor", "worse", "worst",
        "decline", "shrink", "shrinking", "contraction",
        "deficit", "debt", "default", "bankruptcy", "bankrupt",

        // Analyst sentiment
        "downgrade", "downgraded", "downgrades", "sell", "underweight",
        "underperform", "negative", "bearish", "pessimistic",
        "cut", "cuts", "lowered", "lower", "downside",

        // Market conditions / risks
        "warning", "warned", "warns", "risk", "risks", "risky",
        "concern", "concerns", "concerned", "fear", "fears",
        "investigation", "lawsuit", "fine", "penalty", "recall",
        "layoffs", "layoff", "restructuring", "writedown", "writeoff",
        "inflation", "recession", "slowdown", "uncertainty",
        "low", "lows", "bottom", "bottoms", "volatile", "volatility",
        "fraud", "scandal", "probe", "breach", "hack", "cyberattack"
    ));

    /**
     * Score a single headline using the word list approach.
     *
     * Process:
     *  1. Lowercase and tokenise headline into words
     *  2. For each word, check positive and negative lists
     *  3. Score = (positive_count - negative_count) × 0.1
     *  4. Clamp to [-1.0, +1.0]
     *  5. Assign label based on thresholds
     *
     * @param headline raw headline text
     * @param source   news source name
     * @param publishedAt publication date string
     * @param url article URL
     * @return SentimentHeadline with score and label
     */
    public SentimentHeadline scoreHeadline(
            String headline,
            String source,
            String publishedAt,
            String url
    ) {
        if (headline == null || headline.trim().isEmpty()) {
            return buildHeadline(headline, source, 0.0, publishedAt, url);
        }

        // Tokenise: split on spaces and punctuation, lowercase
        String[] words = headline.toLowerCase()
                .replaceAll("[^a-z\\s]", " ") // remove non-alpha chars
                .trim()
                .split("\\s+");

        int positiveCount = 0;
        int negativeCount = 0;

        for (String word : words) {
            if (word.isEmpty()) continue;
            if (POSITIVE_WORDS.contains(word)) positiveCount++;
            if (NEGATIVE_WORDS.contains(word)) negativeCount++;
        }

        // Raw score: each matched word contributes ±0.1
        double rawScore = (positiveCount - negativeCount) * WORD_SCORE;

        // Clamp to [-1.0, +1.0]
        double score = Math.max(-1.0, Math.min(1.0, rawScore));

        return buildHeadline(headline, source, score, publishedAt, url);
    }

    /**
     * Assign a label based on the sentiment score.
     *
     * @param score sentiment score in [-1.0, +1.0]
     * @return "Bullish", "Bearish", or "Neutral"
     */
    public String getLabel(double score) {
        if (score > BULLISH_THRESHOLD)  return "Bullish";
        if (score < BEARISH_THRESHOLD)  return "Bearish";
        return "Neutral";
    }

    /**
     * Compute the overall sentiment score as the average of all headline scores.
     *
     * @param scores list of individual headline scores
     * @return average score, clamped to [-1.0, +1.0]
     */
    public double computeOverallScore(java.util.List<Double> scores) {
        if (scores == null || scores.isEmpty()) return 0.0;
        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return Math.max(-1.0, Math.min(1.0, avg));
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    private SentimentHeadline buildHeadline(
            String headline,
            String source,
            double score,
            String publishedAt,
            String url
    ) {
        return SentimentHeadline.builder()
                .headline(headline)
                .source(source)
                .score(score)
                .label(getLabel(score))
                .publishedAt(publishedAt)
                .url(url)
                .build();
    }
}
