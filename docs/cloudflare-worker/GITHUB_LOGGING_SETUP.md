# GitHub Logging Setup Guide

## Overview

GitHub logging provides **observability only** - it's a flight recorder that logs federation activity without affecting the federation itself.

### Architecture Guarantee

```
Federation Logic (Durable Object)
         ↓
    Aggregation Completes
         ↓
    await logger.logRound()  ← Wrapped in try/catch
         ↓
    [GitHub Write]
         ↓
    Success or Failure → Federation continues regardless
```

**Key Properties:**
- GitHub writes happen AFTER aggregation completes
- All writes wrapped in `try/catch` - failures are silent
- GitHub is never read from (write-only)
- Federation truth lives in Durable Object only

---

## Setup Steps

### 1. Create GitHub Repository

Create a dedicated logging repository:

```bash
# Create new repo on GitHub
Repository name: adaptive-ai-federation-logs
Description: Federation observability logs (auto-generated)
Visibility: Public or Private (your choice)
Initialize: Empty (no README needed)
```

**Recommended Structure:**
```
adaptive-ai-federation-logs/
├── rounds/
│   ├── round-000001.json
│   ├── round-000002.json
│   └── ...
├── uploads/
│   ├── 2025-12-14.jsonl
│   ├── 2025-12-15.jsonl
│   └── ...
└── status/
    └── latest.json
```

### 2. Create GitHub Personal Access Token

1. Go to: https://github.com/settings/tokens
2. Click **"Generate new token (classic)"**
3. Configure:
   - **Note**: `Cloudflare Worker - Federation Logging`
   - **Expiration**: 90 days (or No expiration for production)
   - **Scopes**: Check **`repo`** (full control of private repositories)
4. Click **"Generate token"**
5. **COPY THE TOKEN** - you won't see it again!

**Security Note:** This token has write access to your logging repo only. Never commit it to git.

### 3. Add Token to Cloudflare

```bash
cd cloudflare-worker
wrangler secret put GITHUB_TOKEN
```

When prompted, paste your GitHub token.

**Verify:**
```bash
wrangler secret list
```

Should show:
```
GITHUB_TOKEN (set)
```

### 4. Configure Repository in wrangler.toml

Already configured in `wrangler.toml`:

```toml
[vars]
GITHUB_REPO = "smokydastona/adaptive-ai-federation-logs"
GITHUB_BRANCH = "main"
```

Update `GITHUB_REPO` if you used a different name.

### 5. Deploy Worker

```bash
wrangler deploy
```

### 6. Test GitHub Logging

```bash
# Test endpoint
curl -X POST https://mca-ai-tactics-api.mc-ai-datcol.workers.dev/admin/init-github

# Expected response:
{
  "success": true,
  "message": "GitHub logging test successful",
  "repo": "smokydastona/adaptive-ai-federation-logs",
  "file": "status/latest.json"
}
```

**Check GitHub:**
```
https://github.com/smokydastona/adaptive-ai-federation-logs/blob/main/status/latest.json
```

Should contain:
```json
{
  "test": true,
  "message": "GitHub logging initialized",
  "timestamp": "2025-12-14T...",
  "worker": "healthy",
  "version": "3.0.0"
}
```

---

## What Gets Logged

### Round Completion Logs

**File:** `rounds/round-NNNNNN.json`

```json
{
  "schema": {
    "name": "mca-ai-enhanced.federation.round",
    "version": 2
  },
  "round": 42,
  "timestamp": "2025-12-14T22:30:00.000Z",
  "contributors": {
    "servers": 3,
    "submissions": 5
  },
  "mobTypes": ["zombie", "skeleton", "creeper"],
  "modelStats": {
    "zombie": {
      "distinctActionsObserved": 8,
      "totalExperiences": 1234
    },
    "skeleton": {
      "distinctActionsObserved": 6,
      "totalExperiences": 987
    }
  },
  "metadata": {
    "loggedAt": "2025-12-14T22:30:01.234Z",
    "source": "federation-coordinator"
  }
}
```

**When:** After every successful aggregation.

### Upload Logs (Optional)

**File:** `uploads/YYYY-MM-DD.jsonl`

```jsonl
{"timestamp":"2025-12-14T10:15:30.000Z","mobType":"zombie","round":42,"bootstrap":true}
{"timestamp":"2025-12-14T10:20:15.000Z","mobType":"skeleton","round":42,"bootstrap":false}
```

**When:** After every upload accepted (can be disabled for less verbosity).

### Status Snapshots

**File:** `status/latest.json`

```json
{
  "currentRound": 43,
  "activeContributors": 8,
  "totalUploads": 156,
  "lastAggregation": "2025-12-14T22:30:00.000Z",
  "federationActive": true,
  "lastUpdated": "2025-12-14T22:35:00.000Z"
}
```

**When:** Periodically (can be triggered via cron or manually).

---

## Monitoring & Usage

### View Logs

```bash
# Clone logging repo
git clone https://github.com/smokydastona/adaptive-ai-federation-logs.git
cd adaptive-ai-federation-logs

# View recent rounds
ls rounds/ | tail -10

# Analyze round data
cat rounds/round-000042.json | jq '.contributors'
```

### API Endpoints

**Get current status:**
```bash
curl https://mca-ai-tactics-api.mc-ai-datcol.workers.dev/status
```

**Check GitHub config:**
```bash
curl https://mca-ai-tactics-api.mc-ai-datcol.workers.dev/ | jq '.observability'
```

Expected:
```json
{
  "github": "enabled",
  "repo": "smokydastona/adaptive-ai-federation-logs"
}
```

---

## Troubleshooting

### GitHub Logging Disabled

**Symptom:**
```json
{
  "observability": {
    "github": "disabled"
  }
}
```

**Fix:**
1. Check secret exists: `wrangler secret list`
2. Re-add if missing: `wrangler secret put GITHUB_TOKEN`
3. Verify `GITHUB_REPO` in `wrangler.toml`
4. Redeploy: `wrangler deploy`

### Permission Denied

**Symptom:**
```
GitHub API error: 403 - Resource not accessible by integration
```

**Fix:**
1. Regenerate token with `repo` scope
2. Update secret: `wrangler secret put GITHUB_TOKEN`
3. Verify repo exists and token has write access

### Rate Limiting

**Symptom:**
```
GitHub API error: 429 - API rate limit exceeded
```

**Current Rate:**
- ~12 writes/hour (one per round)
- Well under GitHub's 5,000 req/hour limit

**If needed:**
- Disable upload logging (keep only round logs)
- Increase round aggregation time

### Logs Not Appearing

**Check:**
1. Test endpoint: `curl -X POST .../admin/init-github`
2. Check worker logs: `wrangler tail`
3. Verify GitHub repo permissions
4. Ensure token is active (not expired)

---

## Disabling GitHub Logging

### Temporary

```bash
# Remove secret
wrangler secret delete GITHUB_TOKEN

# Redeploy
wrangler deploy
```

Worker will continue working, logs just won't be written.

### Permanent

Comment out in `wrangler.toml`:
```toml
[vars]
# GITHUB_REPO = "..."
```

Remove logging code from `FederationCoordinator.js` (optional).

---

## Security Best Practices

1. **Use dedicated logging repo** - Don't mix with code
2. **Scope token narrowly** - `repo` access to logging repo only
3. **Rotate tokens** - Set 90-day expiration, add calendar reminder
4. **Monitor usage** - Check GitHub API rate limits occasionally
5. **Never commit tokens** - Use `wrangler secret` only

---

## What This Is NOT

❌ **Not a database** - GitHub is not queried by federation logic  
❌ **Not a cache** - Durable Object is the only source of truth  
❌ **Not required** - Federation works without it (observability only)  
❌ **Not real-time** - Logs written after aggregation (slight delay)

## What This IS

✅ **Flight recorder** - Audit trail of what happened  
✅ **Debugging tool** - Analyze federation patterns  
✅ **Historical archive** - Permanent record of rounds  
✅ **Visualization source** - Can build dashboards from logs  
✅ **Non-blocking** - Failures never affect federation

---

## Next Steps

1. ✅ Set up GitHub repo
2. ✅ Create token
3. ✅ Add to Cloudflare
4. ✅ Deploy worker
5. ✅ Test `/admin/init-github`
6. 🎯 Monitor logs after first aggregation
7. 📊 Build visualization dashboard (optional)

**Support:** If logs aren't appearing, check `wrangler tail` for errors.
