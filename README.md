# QuantView — Fintech Stock Analysis Platform

A full-stack fintech-grade stock analysis web app built with **Java Spring Boot** (backend) and **React** (frontend), deployable on GitHub Codespaces in minutes.

---

## 🧮 What's Inside — The Maths

| Model | Description | Academic Level |
|-------|-------------|----------------|
| **GBM Monte Carlo** | 500 simulated price paths using `dS = μS·dt + σS·dW` | MSc Quantitative Finance |
| **GARCH(1,1)** | Time-varying volatility: `σ²t = ω + α·ε²t-1 + β·σ²t-1` | MSc Financial Econometrics |
| **Value at Risk** | VaR(95%) and VaR(99%) from Monte Carlo distribution | CFA Level 2 / FRM |
| **Sharpe Ratio** | `(μ - rf) / σ × √252` annualised risk-adjusted return | CFA Level 1 |
| **Bollinger Bands** | `SMA ± 2σ` with OVERBOUGHT/OVERSOLD signals | Professional Trading |
| **Log-Linear Regression** | `ln(P) = a + bt` with ±1σ ±2σ confidence bands | A-Level / Year 1 Undergrad |

---

## 🚀 Deploy on GitHub Codespaces (Recommended)

### Step 1 — Create a GitHub Repository

1. Go to [github.com](https://github.com) → **New repository**
2. Name it `quantview-stock-analysis`
3. Set to **Public** (required for free Codespaces tier)
4. Click **Create repository**

### Step 2 — Upload the Project Files

**Option A — GitHub Web UI (easiest):**
1. Extract the `stockapp.zip` you downloaded
2. On your empty repo page, click **uploading an existing file**
3. Drag the entire `stockapp` folder contents into the upload area
4. Commit with message: `Initial commit — QuantView fintech app`

**Option B — Git command line:**
```bash
cd stockapp
git init
git remote add origin https://github.com/YOUR_USERNAME/quantview-stock-analysis.git
git add .
git commit -m "Initial commit — QuantView fintech app"
git push -u origin main
```

### Step 3 — Open in Codespaces

1. On your GitHub repo page, click the green **`<> Code`** button
2. Click the **Codespaces** tab
3. Click **Create codespace on main**
4. Wait 2-3 minutes for the environment to build
   - Java 17 + Maven installs automatically
   - Node 18 installs automatically
   - `mvn dependency:resolve` runs automatically
   - `npm install` runs automatically

### Step 4 — Start the Backend (Spring Boot)

In the Codespaces terminal:
```bash
cd backend
mvn spring-boot:run
```

Wait for this message:
```
========================================
  Stock Analysis Backend STARTED
  API:      http://localhost:8080/api
  H2 Console: http://localhost:8080/h2-console
========================================
```

Codespaces will show a notification: **"Port 8080 is available"** → click **Open in Browser** to verify the API is running.

### Step 5 — Start the Frontend (React)

Open a **second terminal** in Codespaces (click `+` in the terminal panel):
```bash
cd frontend
npm start
```

Wait for:
```
Compiled successfully!
Local:  http://localhost:3000
```

Codespaces will show: **"Port 3000 is available"** → click **Open in Browser**.

Your app is now live at a URL like:
```
https://YOUR_USERNAME-quantview-3000.app.github.dev
```

---

## 🔧 Troubleshooting

### Port 8080 already in use
```bash
kill $(lsof -t -i:8080)
cd backend && mvn spring-boot:run
```

### Port 3000 already in use
```bash
kill $(lsof -t -i:3000)
cd frontend && npm start
```

### Maven dependency download fails
```bash
cd backend
mvn dependency:resolve
mvn spring-boot:run
```

### React can't connect to backend (CORS error)
Make sure **both** ports are set to **Public** in Codespaces:
1. Click the **Ports** tab in the bottom panel
2. Right-click port 8080 → **Port Visibility** → **Public**
3. Right-click port 3000 → **Port Visibility** → **Public**

### Yahoo Finance returns no data
- Some symbols may be temporarily unavailable
- Try a different stock from the dropdown
- FTSE symbols require the `.L` suffix (already handled in the app)

---

## 🗂 Project Structure

```
stockapp/
├── .devcontainer/
│   └── devcontainer.json          ← Codespaces config
├── backend/
│   ├── pom.xml                    ← Maven dependencies
│   └── src/main/java/com/stockanalysis/
│       ├── StockAnalysisApplication.java
│       ├── config/
│       │   └── SecurityConfig.java        ← JWT + CORS config
│       ├── controller/
│       │   ├── AuthController.java        ← /api/auth/*
│       │   ├── StockController.java       ← /api/stocks/*
│       │   ├── AnalysisController.java    ← /api/analysis/*
│       │   └── SentimentController.java   ← /api/sentiment/*
│       ├── model/                         ← DTOs
│       ├── repository/                    ← JPA repositories
│       ├── security/                      ← JWT filter + utils
│       └── service/
│           ├── YahooFinanceService.java   ← Price data fetcher
│           ├── GBMMonteCarloService.java  ← Monte Carlo simulation
│           ├── GARCHService.java          ← GARCH(1,1) volatility
│           ├── RiskMetricsService.java    ← Sharpe + Bollinger
│           ├── ALevelMathsService.java    ← Regression + SMA
│           ├── AnalysisService.java       ← Orchestrates all models
│           ├── SentimentService.java      ← RSS + Reddit fetcher
│           └── SentimentScorerService.java← VADER-equivalent scorer
└── frontend/
    ├── package.json               ← React dependencies + proxy
    └── src/
        ├── App.js                 ← Router
        ├── index.css              ← Dark fintech theme
        ├── services/api.js        ← Axios + JWT interceptor
        ├── pages/
        │   ├── LoginPage.js
        │   ├── RegisterPage.js
        │   └── DashboardPage.js   ← Main dashboard
        └── components/
            ├── Navbar.js
            ├── FintechChart.js    ← GBM cone + Bollinger Bands
            ├── ALevelChart.js     ← Regression + confidence bands
            ├── StatsPanel.js      ← All metrics grid
            └── SentimentPanel.js  ← Headlines + gauge
```

---

## 🔌 API Endpoints

All endpoints except `/api/auth/*` require `Authorization: Bearer <token>` header.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Create user account |
| POST | `/api/auth/login` | Login, returns JWT token |
| GET | `/api/stocks/list/{market}` | NASDAQ or FTSE stock list |
| GET | `/api/stocks/history/{symbol}` | 2 years of daily prices |
| POST | `/api/analysis/run` | Full fintech analysis |
| GET | `/api/sentiment/{symbol}` | News sentiment analysis |

---

## 🏦 Fintech Context

The mathematics in this app is the same used by professional quant desks:

- **GBM + Monte Carlo** → Options pricing, VaR simulation at Goldman Sachs, JP Morgan
- **GARCH(1,1)** → Required volatility model in Basel III bank regulation
- **Value at Risk** → Legally required risk metric for all regulated banks (HSBC, Barclays)
- **Sharpe Ratio** → Standard performance metric at every fund (BlackRock, Vanguard)
- **Bollinger Bands** → Standard on Bloomberg Terminal

The difference from production systems: paid real-time data feeds (we use Yahoo Finance free tier) and GPU-scale simulations (we run 500 paths vs millions).

---

## ⚠️ Disclaimer

QuantView is for **educational purposes only**. Nothing in this application constitutes financial advice. Past performance does not guarantee future results. GBM assumes log-normally distributed returns, which is a simplification of real market dynamics.

---

*Built with Java 17 · Spring Boot 3.2 · React 18 · Recharts · H2 · JWT*
