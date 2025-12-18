# GitHub Logging - Quick Start

## 5-Minute Setup

### 1. Create Token

```bash
# Go to: https://github.com/settings/tokens
# Generate new token (classic)
# Scopes: ✓ repo
# Copy the token
```

### 2. Add to Cloudflare

```bash
cd cloudflare-worker
wrangler secret put GITHUB_TOKEN
# Paste token when prompted
```

### 3. Deploy

```bash
wrangler deploy
```

### 4. Test

```bash
curl -X POST https://mca-ai-tactics-api.mc-ai-datcol.workers.dev/admin/init-github
```

Expected:
```json
{
  "success": true,
  "message": "GitHub logging test successful",
  "repo": "smokydastona/adaptive-ai-federation-logs"
}
```

### 5. Verify

Check: https://github.com/smokydastona/adaptive-ai-federation-logs/blob/main/status/latest.json

---

## What You Get

After federation rounds complete, logs appear in:

- **`rounds/round-NNNNNN.json`** - Round summaries
  ```json
  {
    "round": 42,
    "contributors": { "servers": 3, "submissions": 5 },
    "mobTypes": ["zombie", "skeleton"],
    "timestamp": "2025-12-14T22:30:00Z"
  }
  ```

- **`status/latest.json`** - Current status
  ```json
  {
    "currentRound": 43,
    "activeContributors": 8,
    "federationActive": true
  }
  ```

---

## Important Notes

✅ **Non-blocking** - Failures never affect federation  
✅ **Write-only** - GitHub is never read from  
✅ **Observability only** - Pure logging, no business logic  
✅ **Async** - Happens after aggregation completes  

❌ **Not required** - Federation works without it  
❌ **Not real-time** - Slight delay after rounds  
❌ **Not a database** - Just a flight recorder  

---

## Full Documentation

See: [GITHUB_LOGGING_SETUP.md](GITHUB_LOGGING_SETUP.md)

---

## Troubleshooting

**GitHub disabled?**
```bash
curl https://mca-ai-tactics-api.mc-ai-datcol.workers.dev/ | jq '.observability'
```

Should show:
```json
{
  "github": "enabled",
  "repo": "smokydastona/adaptive-ai-federation-logs"
}
```

If disabled:
1. Check: `wrangler secret list`
2. Re-add: `wrangler secret put GITHUB_TOKEN`
3. Redeploy: `wrangler deploy`
