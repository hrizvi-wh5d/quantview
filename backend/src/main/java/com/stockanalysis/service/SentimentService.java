package com.stockanalysis.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.stockanalysis.model.SentimentDtos.SentimentHeadline;
import com.stockanalysis.model.SentimentDtos.SentimentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SentimentService — fetches and scores news headlines from 3 free sources.
 *
 * ─── DATA SOURCES ────────────────────────────────────────────────────────────
 *
 * 1. Yahoo Finance RSS
 *    URL: https://finance.yahoo.com/rss/headline?s={symbol}
 *    Format: RSS/XML (parsed with Rome library)
 *    Rate limit: generous, no API key needed
 *    Coverage: financial news, earnings, analyst notes
 *
 * 2. Google News RSS
 *    URL: https://news.google.com/rss/search?q={symbol}+stock&hl=en-US&gl=US&ceid=US:en
 *    Format: RSS/XML (parsed with Rome library)
 *    Rate limit: generous, no API key needed
 *    Coverage: broad news coverage including mainstream media
 *
 * 3. Reddit r/wallstreetbets JSON API
 *    URL: https://www.reddit.com/r/wallstreetbets/search.json?q={symbol}&sort=new&limit=10
 *    Format: JSON (parsed with RestTemplate + Map)
 *    Rate limit: requires User-Agent header or returns 429
 *    Coverage: retail investor sentiment, often leading indicator
 *
 * ─── TIMEOUT POLICY ──────────────────────────────────────────────────────────
 *
 * Each source has a 5-second timeout.
 * If a source fails or times out, we log the error and continue with
 * the results from the other sources.
 * This ensures the API always responds even if one source is down.
 *
 * ─── REDDIT REQUIREMENT ──────────────────────────────────────────────────────
 *
 * Reddit requires a User-Agent header — without it returns 429 Too Many Requests.
 * Format: "platform:app_name:version (by /u/username)"
 * We use a generic bot user agent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SentimentService {

    private final SentimentScorerService scorer;
    private final RestTemplate restTemplate;

    @Value("${sentiment.fetch-timeout-ms:5000}")
    private int fetchTimeoutMs;

    @Value("${sentiment.max-headlines:10}")
    private int maxHeadlines;

    // RSS feed URLs
    private static final String YAHOO_RSS_URL =
        "https://finance.yahoo.com/rss/headline?s=";

    private static final String GOOGLE_NEWS_RSS_URL =
        "https://news.google.com/rss/search?q=%s+stock&hl=en-US&gl=US&ceid=US:en";

    private static final String REDDIT_JSON_URL =
        "https://www.reddit.com/r/wallstreetbets/search.json?q=%s&sort=new&limit=10&restrict_sr=1";

    // Reddit requires a descriptive User-Agent or returns 429
    private static final String REDDIT_USER_AGENT =
        "java:com.stockanalysis.sentiment:v1.0 (financial analysis app)";

    /**
     * Fetch and score headlines for a stock symbol from all 3 sources.
     *
     * Each source is fetched independently — failure in one doesn't
     * block the others (graceful degradation).
     *
     * @param symbol stock ticker e.g. "AAPL", "BATS.L"
     * @return SentimentResult with scored headlines and overall score
     */
    public SentimentResult fetchAndScoreSentiment(String symbol) {
        log.info("Fetching sentiment for: {}", symbol);

        // Fetch from each source independently
        List<SentimentHeadline> yahooHeadlines  = fetchYahooFinanceRSS(symbol);
        List<SentimentHeadline> googleHeadlines = fetchGoogleNewsRSS(symbol);
        List<SentimentHeadline> redditHeadlines = fetchRedditJSON(symbol);

        log.info("Headlines fetched: Yahoo={}, Google={}, Reddit={}",
            yahooHeadlines.size(), googleHeadlines.size(), redditHeadlines.size());

        // Combine all headlines
        List<SentimentHeadline> allHeadlines = new ArrayList<>();
        allHeadlines.addAll(yahooHeadlines);
        allHeadlines.addAll(googleHeadlines);
        allHeadlines.addAll(redditHeadlines);

        // Sort by score magnitude (most opinionated first) then limit to max
        allHeadlines.sort((a, b) ->
            Double.compare(Math.abs(b.getScore()), Math.abs(a.getScore()))
        );

        List<SentimentHeadline> topHeadlines = allHeadlines.stream()
                .limit(maxHeadlines)
                .collect(Collectors.toList());

        // Compute overall score: average of all scores (not just top 10)
        List<Double> allScores = allHeadlines.stream()
                .map(SentimentHeadline::getScore)
                .collect(Collectors.toList());

        double overallScore = scorer.computeOverallScore(allScores);
        String overallLabel = scorer.getLabel(overallScore);

        log.info("Sentiment for {}: score={}, label={}, total headlines={}",
            symbol, String.format("%.3f", overallScore), overallLabel, allHeadlines.size());

        return SentimentResult.builder()
                .symbol(symbol)
                .overallScore(overallScore)
                .overallLabel(overallLabel)
                .headlines(topHeadlines)
                .headlineCount(allHeadlines.size())
                .yahooCount(yahooHeadlines.size())
                .googleCount(googleHeadlines.size())
                .redditCount(redditHeadlines.size())
                .build();
    }

    // ── SOURCE 1: YAHOO FINANCE RSS ───────────────────────────────────────────

    /**
     * Fetch headlines from Yahoo Finance RSS feed.
     * Parsed using the Rome RSS/Atom library (pure Java, no external calls).
     *
     * @param symbol stock ticker
     * @return list of scored headlines (empty list if fetch fails)
     */
    private List<SentimentHeadline> fetchYahooFinanceRSS(String symbol) {
        String url = YAHOO_RSS_URL + symbol;
        try {
            return parseRSSFeed(url, "Yahoo Finance");
        } catch (Exception e) {
            log.warn("Yahoo Finance RSS fetch failed for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── SOURCE 2: GOOGLE NEWS RSS ─────────────────────────────────────────────

    /**
     * Fetch headlines from Google News RSS feed.
     *
     * @param symbol stock ticker
     * @return list of scored headlines (empty list if fetch fails)
     */
    private List<SentimentHeadline> fetchGoogleNewsRSS(String symbol) {
        // For FTSE stocks, strip the ".L" suffix for better Google News results
        String searchSymbol = symbol.replace(".L", "").replace(".l", "");
        String url = String.format(GOOGLE_NEWS_RSS_URL, searchSymbol);
        try {
            return parseRSSFeed(url, "Google News");
        } catch (Exception e) {
            log.warn("Google News RSS fetch failed for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Parse an RSS feed URL and score each entry.
     * Uses Rome library (already in pom.xml as 'rome' dependency).
     *
     * @param url RSS feed URL
     * @param source source name for labelling
     * @return list of scored headlines
     */
    private List<SentimentHeadline> parseRSSFeed(String url, String source) throws Exception {
        List<SentimentHeadline> headlines = new ArrayList<>();

        // Open connection with timeout
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(fetchTimeoutMs);
        connection.setReadTimeout(fetchTimeoutMs);
        // Some RSS feeds also need User-Agent
        connection.setRequestProperty("User-Agent",
            "Mozilla/5.0 (compatible; StockAnalysisBot/1.0)");

        // Rome library parses RSS/Atom feeds
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(connection));

        for (SyndEntry entry : feed.getEntries()) {
            String title = entry.getTitle();
            if (title == null || title.trim().isEmpty()) continue;

            String publishedAt = entry.getPublishedDate() != null
                    ? entry.getPublishedDate().toString()
                    : "Unknown";

            String entryUrl = entry.getLink() != null ? entry.getLink() : "";

            SentimentHeadline headline = scorer.scoreHeadline(
                title, source, publishedAt, entryUrl
            );
            headlines.add(headline);
        }

        log.debug("Parsed {} headlines from {}", headlines.size(), source);
        return headlines;
    }

    // ── SOURCE 3: REDDIT JSON API ─────────────────────────────────────────────

    /**
     * Fetch posts from Reddit r/wallstreetbets using the public JSON API.
     *
     * Reddit's JSON API works WITHOUT authentication for read-only access.
     * Critical: MUST set User-Agent header — Reddit returns 429 without it.
     *
     * Response structure:
     * {
     *   "data": {
     *     "children": [
     *       { "data": { "title": "AAPL to the moon!", "score": 1234 } },
     *       ...
     *     ]
     *   }
     * }
     *
     * @param symbol stock ticker
     * @return list of scored headlines from Reddit posts
     */
    @SuppressWarnings("unchecked")
    private List<SentimentHeadline> fetchRedditJSON(String symbol) {
        // Strip market suffix for Reddit search (e.g. "BATS.L" → "BATS")
        String searchSymbol = symbol.replace(".L", "").replace(".l", "");
        String url = String.format(REDDIT_JSON_URL, searchSymbol);

        List<SentimentHeadline> headlines = new ArrayList<>();

        try {
            // CRITICAL: Reddit requires User-Agent or returns 429
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", REDDIT_USER_AGENT);
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Use injected RestTemplate — not a new instance per request
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity,
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("Reddit API returned: {}", response.getStatusCode());
                return Collections.emptyList();
            }

            // Navigate the Reddit JSON structure
            Map<String, Object> body = response.getBody();
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) return Collections.emptyList();

            List<Object> children = (List<Object>) data.get("children");
            if (children == null) return Collections.emptyList();

            for (Object child : children) {
                Map<String, Object> childMap = (Map<String, Object>) child;
                Map<String, Object> postData = (Map<String, Object>) childMap.get("data");
                if (postData == null) continue;

                String title = (String) postData.get("title");
                if (title == null || title.trim().isEmpty()) continue;

                String permalink = (String) postData.getOrDefault("permalink", "");
                String postUrl = permalink.isEmpty() ? "" : "https://reddit.com" + permalink;

                Object createdUtc = postData.get("created_utc");
                String publishedAt = createdUtc != null
                        ? new Date(((Number) createdUtc).longValue() * 1000).toString()
                        : "Unknown";

                headlines.add(scorer.scoreHeadline(title, "Reddit WSB", publishedAt, postUrl));
            }

            log.debug("Parsed {} posts from Reddit r/wallstreetbets", headlines.size());

        } catch (Exception e) {
            log.warn("Reddit fetch failed for {}: {}", symbol, e.getMessage());
        }

        return headlines;
    }
}
