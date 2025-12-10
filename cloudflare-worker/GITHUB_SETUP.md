# GitHub Backup Setup Guide

## Quick Start (5 minutes)

### 1. Create GitHub Personal Access Token

1. Go to https://github.com/settings/tokens
2. Click **"Generate new token (classic)"**
3. Name it: `MCA AI Federated Learning`
4. Select scope: **`repo`** (Full control of private repositories)
5. Click **"Generate token"**
6. **Copy the token** (starts with `ghp_` - you won't see it again!)

### 2. Set Token in Cloudflare

```bash
cd cloudflare-worker
wrangler secret put GITHUB_TOKEN
```

When prompted, paste your token and press Enter.

### 3. Deploy Worker

```bash
npm run deploy
```

Done! The worker will now automatically backup tactics to:
https://github.com/smokydastona/Adaptive-Minecraft-Mob-Ai/tree/main/federated-data

---

## How It Works

### Automatic Backups

**Every 100 submissions per mob type:**

```
Player 1 submits zombie tactic (#97)
Player 2 submits zombie tactic (#98)  
Player 3 submits zombie tactic (#99)
Player 4 submits zombie tactic (#100) → 🔄 GitHub backup triggered!

Worker commits to GitHub:
  federated-data/zombie-tactics.json
  "Federated learning: Update zombie tactics (100 submissions)"
```

### Manual Backups

Trigger all mob types at once:

```bash
curl https://your-worker.workers.dev/api/process-pipeline
```

Returns:
```json
{
  "stages": {
    "github": {
      "status": "success",
      "results": {
        "zombie": {
          "status": "success",
          "url": "https://github.com/.../zombie-tactics.json",
          "submissions": 1500
        }
      }
    }
  }
}
```

---

## What Gets Backed Up

### File Structure

```
Adaptive-Minecraft-Mob-Ai/
└── federated-data/
    ├── zombie-tactics.json          (100 submissions)
    ├── skeleton-tactics.json        (200 submissions)
    ├── creeper-tactics.json         (50 submissions)
    ├── spider-tactics.json          (150 submissions)
    ├── husk-tactics.json            (25 submissions)
    ├── stray-tactics.json           (30 submissions)
    ├── wither_skeleton-tactics.json (10 submissions)
    └── enderman-tactics.json        (40 submissions)
```

### File Content Example

`zombie-tactics.json`:
```json
{
  "mobType": "zombie",
  "submissions": 1500,
  "lastUpdate": 1702156800000,
  "syncedAt": 1702156900000,
  "tactics": [
    {
      "action": "circle_strafe",
      "avgReward": 8.5,
      "count": 450,
      "successRate": 0.87,
      "successCount": 391,
      "failureCount": 59,
      "lastUpdate": 1702156800000
    },
    {
      "action": "kite_backward",
      "avgReward": 7.2,
      "count": 380,
      "successRate": 0.72,
      "successCount": 274,
      "failureCount": 106,
      "lastUpdate": 1702156750000
    }
  ]
}
```

---

## Viewing Backups

### GitHub Web Interface

Visit: https://github.com/smokydastona/Adaptive-Minecraft-Mob-Ai/tree/main/federated-data

Click any file to see:
- Current tactics data
- Commit history
- When it was last updated
- How many submissions

### Commit History

Each backup creates a commit:
```
Federated learning: Update zombie tactics (1500 submissions)
Federated learning: Update skeleton tactics (1200 submissions)
Federated learning: Update creeper tactics (800 submissions)
```

Click "History" to see evolution over time.

### Raw Data

Get raw JSON:
```bash
curl https://raw.githubusercontent.com/smokydastona/Adaptive-Minecraft-Mob-Ai/main/federated-data/zombie-tactics.json
```

---

## Benefits

### 1. Disaster Recovery

If Cloudflare KV storage is lost:
```bash
# Download from GitHub
curl -O https://raw.githubusercontent.com/.../zombie-tactics.json

# Re-upload to new KV namespace
wrangler kv:key put "tactics:zombie" --path zombie-tactics.json
```

### 2. Version History

See how tactics evolved:
```bash
# View commits
git log federated-data/zombie-tactics.json

# Compare versions
git diff HEAD~5 HEAD -- federated-data/zombie-tactics.json
```

### 3. Research & Analysis

Data available for:
- Academic papers
- Blog posts
- YouTube videos
- Community analysis

### 4. Transparency

Anyone can verify:
- What the AI learned
- Which tactics are most successful
- How strategies evolved over time

---

## Troubleshooting

### "GitHub sync failed: 401"

**Problem:** Invalid or expired token

**Solution:**
```bash
# Create new token at https://github.com/settings/tokens
wrangler secret put GITHUB_TOKEN
# Paste new token
```

### "GitHub sync failed: 403"

**Problem:** Token doesn't have `repo` scope

**Solution:**
1. Go to https://github.com/settings/tokens
2. Edit your token
3. Check the `repo` scope box
4. Click "Update token"
5. Copy the token (it changes!)
6. `wrangler secret put GITHUB_TOKEN`

### "GitHub sync failed: 404"

**Problem:** Repository doesn't exist or is private

**Solution:**
- Make sure `Adaptive-Minecraft-Mob-Ai` repository exists
- Check it's owned by `smokydastona`
- If private, ensure token has access

### Sync Not Triggering

**Check if token is set:**
```bash
wrangler secret list
```

Should show:
```
GITHUB_TOKEN | secret | <encrypted>
```

**Test manually:**
```bash
curl https://your-worker.workers.dev/api/process-pipeline
```

Check response for GitHub stage status.

---

## Security

### Token Safety

✅ **Stored as Cloudflare secret** (encrypted)  
✅ **Never logged or exposed** in responses  
✅ **Only used for GitHub API calls**  
✅ **Scoped to repo access only** (not account-wide)

### Revoking Access

If token is compromised:
1. Go to https://github.com/settings/tokens
2. Find "MCA AI Federated Learning"
3. Click "Delete"
4. Worker will skip GitHub sync (no errors)

### Best Practices

- **Use classic tokens** (not fine-grained)
- **Set expiration** (e.g., 90 days, then renew)
- **Minimal scope** (only `repo`)
- **Descriptive name** (so you remember what it's for)

---

## Optional: Disable GitHub Backup

Don't want GitHub backup? Simply don't set `GITHUB_TOKEN`.

The worker will:
- ✅ Continue functioning normally
- ✅ Store tactics in KV
- ✅ Serve downloads to mods
- ✅ Run AI analysis
- ⏭️ Skip GitHub sync (no errors)

To disable after enabling:
```bash
wrangler secret delete GITHUB_TOKEN
```

---

## Rate Limits

**GitHub API (with authentication):**
- 5,000 requests/hour
- Backups count as 2 requests each (GET + PUT)
- 100 submissions = 1 backup = 2 API calls
- **5,000 submissions/hour = well within limits**

**Exceeded rate limit?**
Worker will log error but continue functioning. GitHub sync will resume next hour.

---

## Advanced: Custom Repository

Want to backup to a different repo?

Edit `cloudflare-worker/worker.js`:
```javascript
const owner = 'YOUR_GITHUB_USERNAME';
const repo = 'YOUR_REPO_NAME';
const path = `federated-data/${mobType}-tactics.json`;
```

Re-deploy:
```bash
npm run deploy
```

---

## Verification

After setup, verify it's working:

1. **Check worker logs:**
```bash
wrangler tail
```

2. **Submit 100 tactics** (or call pipeline endpoint)

3. **Check GitHub:**
https://github.com/smokydastona/Adaptive-Minecraft-Mob-Ai/commits/main

You should see:
```
Federated learning: Update zombie tactics (100 submissions)
Committed by mca-ai-federated-learning
```

4. **View file:**
https://github.com/smokydastona/Adaptive-Minecraft-Mob-Ai/blob/main/federated-data/zombie-tactics.json

---

## Summary

**Setup:** 5 minutes  
**Cost:** Free (GitHub API is free)  
**Benefit:** Complete backup + version history + public dataset  
**Maintenance:** None (fully automatic)  
**Optional:** Yes (worker works without it)

Enable GitHub backup for peace of mind and transparency! 🚀
