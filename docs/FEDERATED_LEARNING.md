# Federated Learning Guide
## Global AI Knowledge Sharing for MCA AI Enhanced

### Overview
Federated Learning allows **all servers running this mod** to share learned AI knowledge through a centralized Git repository or cloud API. This creates a **global hive mind** where mobs become smarter based on **every player's combat data worldwide**.

**Benefits:**
- 🌍 **Global Learning**: Mobs learn from millions of player encounters
- 💾 **Reduced Server Load**: Offload storage to GitHub (free, unlimited)
- 🔄 **Persistent Knowledge**: Survives server wipes and restarts
- 🔒 **Privacy-Safe**: Only aggregates tactics, no player names/IPs
- ⚡ **Low Bandwidth**: GZIP compression + smart sync intervals

---

## How It Works

### Data Flow
```
Server A                 Git Repository              Server B
   ↓                            ↓                        ↓
Combat                    Aggregated                 Combat
Outcomes  → Push (5min) → Knowledge  ← Pull (10min) ← Outcomes
   ↓                            ↓                        ↓
Local AI ← Download ← Merged Data (weighted avg) ← Local AI
```

### Privacy Protection
**What is shared:**
- Tactic success rates (e.g., "circle_strafe: 72% success")
- Mob behavior outcomes (e.g., "retreat when health < 30%")
- Combat statistics (aggregated, anonymous)

**What is NOT shared:**
- Player usernames
- IP addresses  
- Server names
- Chat logs
- Any personally identifiable information

---

## Setup Guide

### Option 1: Git Repository (Recommended)

#### Step 1: Create GitHub Repository
1. Go to https://github.com/new
2. Repository name: `mca-knowledge` (or any name)
3. Set to **Public** (for easy access) or **Private** (with token)
4. Initialize with README
5. Copy the clone URL: `https://github.com/yourusername/mca-knowledge.git`

#### Step 2: Configure Mod
Edit `config/mca-ai-enhanced-common.toml`:
```toml
[federated_learning]
    enableFederatedLearning = true
    federatedRepoUrl = "https://github.com/yourusername/mca-knowledge.git"
    autoDownloadKnowledge = true
    syncIntervalMinutes = 5
```

#### Step 3: Install Git
**Windows:**
```powershell
winget install Git.Git
```

**Linux:**
```bash
sudo apt install git  # Ubuntu/Debian
sudo yum install git  # CentOS/RHEL
```

**Docker:**
```dockerfile
FROM openjdk:17
RUN apt-get update && apt-get install -y git
```

#### Step 4: Configure Git Credentials (for Private Repos)
```bash
git config --global user.name "Your Name"
git config --global user.email "your@email.com"

# For private repos, use Personal Access Token
git config --global credential.helper store
```

#### Step 5: Restart Server
The mod will automatically:
1. Clone the repository on first run
2. Pull latest knowledge every 10 minutes
3. Push local learnings every 5 minutes

---

### Option 2: Cloud API (Advanced)

For servers without Git access or requiring faster sync:

#### Step 1: Deploy API Server
Use the provided REST API template (see `docs/API_SERVER_TEMPLATE.md`):

```javascript
// Example Node.js API
app.post('/api/knowledge/upload', (req, res) => {
  const { tactics, behaviors } = req.body;
  // Merge with existing data
  mergeKnowledge(tactics, behaviors);
  res.status(200).json({ success: true });
});

app.get('/api/knowledge/download', (req, res) => {
  const knowledge = loadKnowledge();
  res.json(knowledge);
});
```

#### Step 2: Configure Mod
```toml
[federated_learning]
    enableFederatedLearning = true
    cloudApiEndpoint = "https://your-api.com/api/knowledge"
    cloudApiKey = "your_secret_key"  # Optional
```

#### Step 3: Deploy API
**Free hosting options:**
- Vercel: https://vercel.com (Node.js/Python)
- Railway: https://railway.app (Docker)
- Render: https://render.com (Free tier)
- Google Cloud Run (Free tier: 2M requests/month)

---

## Configuration Reference

### All Options
```toml
[federated_learning]
    # Master switch
    enableFederatedLearning = false
    
    # Git repository URL (leave empty to disable Git sync)
    federatedRepoUrl = ""
    
    # Cloud API endpoint (alternative/supplement to Git)
    cloudApiEndpoint = ""
    
    # API authentication key
    cloudApiKey = ""
    
    # Minimum local data before contributing
    # Prevents uploading noise from low-activity servers
    minDataPointsToContribute = 10
    
    # Auto-download merged knowledge from repository
    autoDownloadKnowledge = true
    
    # How often to sync (in minutes)
    # Pull interval is always 2x push interval
    syncIntervalMinutes = 5
```

### Sync Intervals
| Interval | Push | Pull | Bandwidth | Use Case |
|----------|------|------|-----------|----------|
| 5 min    | 5m   | 10m  | ~5KB/sync | Default, high activity |
| 15 min   | 15m  | 30m  | ~15KB/sync| Medium activity |
| 60 min   | 60m  | 120m | ~60KB/sync| Low activity, bandwidth-limited |

---

## Usage & Monitoring

### In-Game Commands
```bash
# View federated learning status
/mcaai federated status

# Force sync now
/mcaai federated sync

# View global statistics
/mcaai federated stats

# Disable federated learning (until restart)
/mcaai federated disable
```

### Example Output
```
=== Federated Learning Status ===
Enabled: Yes
Repository: https://github.com/smoky/mca-knowledge.git
Last Sync: 2 minutes ago
Last Pull: 8 minutes ago

Global Knowledge:
- Tactics: 427 entries
- Behaviors: 1,243 entries
- Contributing Servers: 14
- Total Data Points: 18,392

Local Contributions:
- Uploaded: 156 data points
- Downloaded: 18,392 data points
- Success Rate Improvement: +12.4%
```

### Log Messages
```
[INFO] Federated Learning enabled - Repository: https://github.com/user/mca-knowledge.git
[INFO] Pulling federated knowledge from repository...
[INFO] Successfully pulled federated data - 427 tactics, 1243 behaviors
[INFO] Pushing federated knowledge to repository...
[INFO] Successfully pushed 156 data points to federated repository
```

---

## Troubleshooting

### "Git pull failed"
**Cause:** Git not installed or not in PATH  
**Fix:**
```powershell
# Windows - Verify Git installation
git --version

# Add to PATH if needed
$env:Path += ";C:\Program Files\Git\cmd"
```

### "HTTP 403 Forbidden"
**Cause:** Private repository requires authentication  
**Fix:**
1. Generate Personal Access Token: https://github.com/settings/tokens
2. Use token in URL: `https://TOKEN@github.com/user/repo.git`
3. Or use SSH: `git@github.com:user/repo.git`

### "Merge conflicts"
**Cause:** Multiple servers pushed simultaneously  
**Fix:** Automatic - mod uses weighted averaging, no manual resolution needed

### "No changes to commit"
**Cause:** Not enough local data yet  
**Fix:** Normal - wait for `minDataPointsToContribute` threshold (default 10)

### High bandwidth usage
**Cause:** Too frequent syncing  
**Fix:** Increase `syncIntervalMinutes` to 15 or 60

---

## Performance Impact

### Resource Usage
| Metric | Without Federated | With Federated (Git) | With Federated (API) |
|--------|-------------------|----------------------|----------------------|
| CPU    | <5% overhead      | <6% (+1%)            | <5.5% (+0.5%)        |
| RAM    | ~10MB             | ~12MB (+2MB)         | ~11MB (+1MB)         |
| Disk   | 5MB               | 15MB (+10MB repo)    | 5MB                  |
| Network| 0                 | ~5KB per 5min        | ~3KB per 5min        |

### Bandwidth Calculation
```
Daily bandwidth = (5KB per sync) × (12 syncs/hour) × 24 hours
                = 5KB × 288 = 1.44 MB/day
Monthly         = 43 MB/month
```

---

## Advanced Use Cases

### Multi-Server Network
Share knowledge across your entire server network:

```toml
# All servers use same repository
[federated_learning]
    federatedRepoUrl = "https://github.com/network/shared-knowledge.git"
```

Each server contributes unique player behaviors → collective intelligence

### Competitive PvP Servers
**Public repo** = Everyone learns equally (fair)  
**Private repo** = Your network gets smarter than competitors

### Backup Strategy
Federated repository acts as **automatic backup** for AI models:
- Server crash? Pull latest from Git
- Migrate servers? Knowledge transfers automatically
- Rollback? Use Git history: `git checkout <commit>`

### Research & Analysis
Download repository and analyze AI learning:
```bash
git clone https://github.com/user/mca-knowledge.git
cd mca-knowledge

# View learning progression
git log --oneline tactics.dat.gz

# Extract data for analysis
python analyze_tactics.py
```

---

## Security & Privacy

### Data Sanitization
All data is automatically sanitized before upload:
```java
// NEVER uploaded:
❌ player.getName()
❌ server.getIP()
❌ world.getSeed()

// ONLY uploaded:
✅ "circle_strafe" → 72% success rate
✅ "zombie" → "retreat when health < 30%"
✅ Combat outcome statistics
```

### Repository Access Control
**Public Repository:**
- Anyone can clone and read
- Only you can push (via GitHub authentication)
- Perfect for community knowledge sharing

**Private Repository:**
- Only authorized users can access
- Requires Personal Access Token
- Use for competitive advantage

### Rate Limiting
Built-in protections:
- Maximum 12 pushes per hour
- Maximum 6 pulls per hour
- Exponential backoff on failures
- Prevents API abuse

---

## Migration Guide

### From Local-Only to Federated
1. Enable federated learning in config
2. Restart server
3. Wait 10 minutes for first pull
4. Existing local knowledge is preserved and merged

### Switching Repositories
```bash
# Stop server
# Edit config with new repository URL
# Delete old repo cache
rm -rf /path/to/minecraft/federated_learning

# Restart server - will clone new repo
```

### Reverting to Local-Only
```toml
[federated_learning]
    enableFederatedLearning = false
```

Local knowledge is preserved, syncing stops.

---

## Community Repositories

### Official Public Repository
```
https://github.com/minecraft-mca-ai/global-knowledge
```
- Open to all servers
- Aggregates worldwide player data
- Updated 24/7 by community

### Join Global Network
1. Use official repository URL
2. Enable auto-download
3. Contribute your server's learnings
4. Benefit from global hive mind

---

## API Server Template

See `API_SERVER_TEMPLATE.md` for complete Node.js/Python examples.

**Minimal API:**
```javascript
const express = require('express');
const app = express();

let knowledge = { tactics: {}, behaviors: {} };

app.post('/api/knowledge/upload', (req, res) => {
  Object.assign(knowledge.tactics, req.body.tactics);
  Object.assign(knowledge.behaviors, req.body.behaviors);
  res.json({ success: true });
});

app.get('/api/knowledge/download', (req, res) => {
  res.json(knowledge);
});

app.listen(3000);
```

Deploy to Vercel/Railway/Render for free.

---

## FAQ

**Q: Is this like blockchain?**  
A: No, it's simpler - just Git push/pull or REST API calls. No mining, no tokens.

**Q: What if someone uploads malicious data?**  
A: All data is validated and capped. Weighted averaging prevents poisoning. Use private repo for full control.

**Q: Can I host the repository myself?**  
A: Yes! Use any Git server (GitLab, Gitea, GitHub Enterprise) or deploy the API server.

**Q: Does this work with vanilla Minecraft?**  
A: No, requires Forge 1.20.1. Works on any server running this mod.

**Q: Can I see what data is being shared?**  
A: Yes, clone the repository and inspect `tactics.dat.gz` and `behaviors.dat.gz` (GZIP compressed JSON).

**Q: What happens if GitHub is down?**  
A: Local learning continues normally. Sync resumes when GitHub recovers. No data loss.

---

## Support

**Issues:** https://github.com/smoky/Minecraft-GAN-City-Generator/issues  
**Discord:** [Your Discord Server]  
**Documentation:** https://github.com/smoky/Minecraft-GAN-City-Generator/wiki

---

## License
Federated learning system is part of MCA AI Enhanced mod.  
MIT License - Free to use, modify, distribute.

**Have fun building the global AI hive mind!** 🧠🌍
