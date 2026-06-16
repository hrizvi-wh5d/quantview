package com.stockanalysis.service;

import com.stockanalysis.model.StockDtos.*;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * StockListService — provides curated lists of NASDAQ and FTSE 100 stocks.
 *
 * These are hardcoded because:
 *  1. Yahoo Finance doesn't have an index constituent API in the free tier
 *  2. These represent the most liquid, commonly traded stocks
 *  3. All symbols are verified to work with Yahoo Finance v8 API
 *
 * FTSE symbols use ".L" suffix (London Stock Exchange convention).
 * e.g. Barclays = "BARC.L", not "BARC"
 */
@Service
public class StockListService {

    /**
     * Get stock list for a given market.
     *
     * @param market "NASDAQ" or "FTSE"
     * @return StockListResponse with all available stocks
     */
    public StockListResponse getStockList(String market) {
        List<StockInfo> stocks = market.equalsIgnoreCase("FTSE")
                ? getFtseStocks()
                : getNasdaqStocks();

        return StockListResponse.builder()
                .market(market.toUpperCase())
                .stocks(stocks)
                .count(stocks.size())
                .build();
    }

    /**
     * NASDAQ stocks — top 30 by market cap and trading volume.
     * All symbols work directly with Yahoo Finance v8 API.
     */
    private List<StockInfo> getNasdaqStocks() {
        return Arrays.asList(
            stock("AAPL",  "Apple Inc.",                    "NASDAQ", "Technology"),
            stock("MSFT",  "Microsoft Corporation",         "NASDAQ", "Technology"),
            stock("GOOGL", "Alphabet Inc.",                 "NASDAQ", "Technology"),
            stock("AMZN",  "Amazon.com Inc.",               "NASDAQ", "Consumer Cyclical"),
            stock("NVDA",  "NVIDIA Corporation",            "NASDAQ", "Technology"),
            stock("META",  "Meta Platforms Inc.",           "NASDAQ", "Technology"),
            stock("TSLA",  "Tesla Inc.",                    "NASDAQ", "Consumer Cyclical"),
            stock("AVGO",  "Broadcom Inc.",                 "NASDAQ", "Technology"),
            stock("ASML",  "ASML Holding N.V.",             "NASDAQ", "Technology"),
            stock("COST",  "Costco Wholesale Corporation",  "NASDAQ", "Consumer Defensive"),
            stock("NFLX",  "Netflix Inc.",                  "NASDAQ", "Communication Services"),
            stock("AMD",   "Advanced Micro Devices Inc.",   "NASDAQ", "Technology"),
            stock("ADBE",  "Adobe Inc.",                    "NASDAQ", "Technology"),
            stock("QCOM",  "Qualcomm Incorporated",         "NASDAQ", "Technology"),
            stock("INTC",  "Intel Corporation",             "NASDAQ", "Technology"),
            stock("PYPL",  "PayPal Holdings Inc.",          "NASDAQ", "Financial Services"),
            stock("INTU",  "Intuit Inc.",                   "NASDAQ", "Technology"),
            stock("AMAT",  "Applied Materials Inc.",        "NASDAQ", "Technology"),
            stock("MU",    "Micron Technology Inc.",        "NASDAQ", "Technology"),
            stock("SBUX",  "Starbucks Corporation",         "NASDAQ", "Consumer Cyclical"),
            stock("GILD",  "Gilead Sciences Inc.",          "NASDAQ", "Healthcare"),
            stock("MDLZ",  "Mondelez International Inc.",   "NASDAQ", "Consumer Defensive"),
            stock("REGN",  "Regeneron Pharmaceuticals",     "NASDAQ", "Healthcare"),
            stock("LRCX",  "Lam Research Corporation",      "NASDAQ", "Technology"),
            stock("PANW",  "Palo Alto Networks Inc.",       "NASDAQ", "Technology"),
            stock("SNPS",  "Synopsys Inc.",                 "NASDAQ", "Technology"),
            stock("MRVL",  "Marvell Technology Inc.",       "NASDAQ", "Technology"),
            stock("CRWD",  "CrowdStrike Holdings Inc.",     "NASDAQ", "Technology"),
            stock("KLAC",  "KLA Corporation",               "NASDAQ", "Technology"),
            stock("ORLY",  "O'Reilly Automotive Inc.",      "NASDAQ", "Consumer Cyclical")
        );
    }

    /**
     * FTSE 100 stocks — top 30 by market cap.
     *
     * CRITICAL: FTSE symbols MUST have ".L" suffix for Yahoo Finance.
     * e.g. Barclays = "BARC.L"
     *
     * Yahoo Finance returns FTSE prices in GBp (pence), not GBP (pounds).
     * To convert to pounds: divide by 100.
     * The frontend should handle this display conversion.
     */
    private List<StockInfo> getFtseStocks() {
        return Arrays.asList(
            stock("SHEL.L",  "Shell plc",                      "FTSE", "Energy"),
            stock("AZN.L",   "AstraZeneca PLC",                "FTSE", "Healthcare"),
            stock("HSBA.L",  "HSBC Holdings plc",              "FTSE", "Financial Services"),
            stock("ULVR.L",  "Unilever PLC",                   "FTSE", "Consumer Defensive"),
            stock("BP.L",    "BP p.l.c.",                      "FTSE", "Energy"),
            stock("RIO.L",   "Rio Tinto Group",                "FTSE", "Basic Materials"),
            stock("GSK.L",   "GSK plc",                        "FTSE", "Healthcare"),
            stock("BATS.L",  "British American Tobacco p.l.c.","FTSE", "Consumer Defensive"),
            stock("GLEN.L",  "Glencore PLC",                   "FTSE", "Basic Materials"),
            stock("DGE.L",   "Diageo plc",                     "FTSE", "Consumer Defensive"),
            stock("REL.L",   "RELX PLC",                       "FTSE", "Communication Services"),
            stock("NG.L",    "National Grid plc",              "FTSE", "Utilities"),
            stock("BHP.L",   "BHP Group Limited",              "FTSE", "Basic Materials"),
            stock("LLOY.L",  "Lloyds Banking Group plc",       "FTSE", "Financial Services"),
            stock("VOD.L",   "Vodafone Group Plc",             "FTSE", "Communication Services"),
            stock("BARC.L",  "Barclays PLC",                   "FTSE", "Financial Services"),
            stock("RKT.L",   "Reckitt Benckiser Group plc",    "FTSE", "Consumer Defensive"),
            stock("EXPN.L",  "Experian plc",                   "FTSE", "Financial Services"),
            stock("NWG.L",   "NatWest Group plc",              "FTSE", "Financial Services"),
            stock("PRU.L",   "Prudential plc",                 "FTSE", "Financial Services"),
            stock("AAL.L",   "Anglo American plc",             "FTSE", "Basic Materials"),
            stock("ABF.L",   "Associated British Foods plc",   "FTSE", "Consumer Defensive"),
            stock("WPP.L",   "WPP plc",                        "FTSE", "Communication Services"),
            stock("SDR.L",   "Schroders plc",                  "FTSE", "Financial Services"),
            stock("IMB.L",   "Imperial Brands PLC",            "FTSE", "Consumer Defensive"),
            stock("LAND.L",  "Land Securities Group plc",      "FTSE", "Real Estate"),
            stock("MNG.L",   "M&G plc",                        "FTSE", "Financial Services"),
            stock("SGRO.L",  "Segro plc",                      "FTSE", "Real Estate"),
            stock("IHG.L",   "InterContinental Hotels Group",  "FTSE", "Consumer Cyclical"),
            stock("CRH.L",   "CRH plc",                        "FTSE", "Basic Materials")
        );
    }

    /** Helper to build a StockInfo object cleanly */
    private StockInfo stock(String symbol, String name, String market, String sector) {
        return StockInfo.builder()
                .symbol(symbol)
                .name(name)
                .market(market)
                .sector(sector)
                .build();
    }
}
