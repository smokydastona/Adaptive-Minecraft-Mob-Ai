# MCA AI Enhanced - Federated Learning Server

Cloudflare Workers deployment for global mob tactics aggregation and distribution.

## Features Fixed in v1.3.0

✅ **Success Rate Tracking** - Now properly tracks and returns success/failure outcomes  
✅ **Timestamp Storage** - Stores and returns `lastUpdate` timestamps for each tactic  
✅ **Complete Data Model** - Returns `successRate`, `successCount`, `failureCount`, `lastUpdate`  
✅ **Extended Mob Support** - Added husk, stray, wither_skeleton, enderman  
✅ **Enhanced Stats** - Returns top tactic for each mob type in stats endpoint

## Data Flow

### Mod → Cloudflare (Submit)
```json
{
  "mobType": "zombie",
  "action": "circle_strafe",
  "reward": 8.5,
  "outcome": "success",
  "timestamp": 1702156800000
}
```

### Cloudflare → Mod (Download)
```json
{
  "version": "1.0.0",
  "timestamp": 1702156800000,
  "tactics": {
    "zombie": {
      "submissions": 150,
      "lastUpdate": 1702156800000,
      "tactics": [
        {
          "action": "circle_strafe",
          "avgReward": 8.5,
          "count": 45,
          "successRate": 0.87,
          "successCount": 39,
          "failureCount": 6,
          "lastUpdate": 1702156800000
        }
      ]
    }
  }
}
```

## Deployment

### Prerequisites
- Cloudflare account
- Node.js and npm installed
- Wrangler CLI: `npm install -g wrangler`

### Setup Steps

1. **Login to Cloudflare**
```bash
wrangler login
```

2. **Create KV Namespace**
```bash
wrangler kv:namespace create "TACTICS_KV"
```

Copy the namespace ID and update `wrangler.toml`:
```toml
[[kv_namespaces]]
binding = "TACTICS_KV"
id = "YOUR_KV_NAMESPACE_ID_HERE"
```

3. **Deploy Worker**
```bash
npm run deploy
```

4. **Get Your Worker URL**
After deployment, you'll see:
```
Published mca-ai-federated-learning
  https://mca-ai-federated-learning.YOUR_SUBDOMAIN.workers.dev
```

5. **Update Mod Configuration**
Edit `config/mca-ai-enhanced-common.toml`:
```toml
federatedLearningURL = "https://mca-ai-federated-learning.YOUR_SUBDOMAIN.workers.dev/"
```

## API Endpoints

### `POST /api/submit-tactics`
Submit learned tactics from gameplay

**Request:**
```json
{
  "mobType": "zombie",
  "action": "circle_strafe", 
  "reward": 8.5,
  "outcome": "success",
  "timestamp": 1702156800000
}
```

**Response:**
```json
{
  "success": true,
  "mobType": "zombie",
  "totalSubmissions": 151,
  "message": "Tactics received and aggregated",
  "tacticStats": {
    "action": "circle_strafe",
    "avgReward": 8.5,
    "successRate": 0.87,
    "count": 45
  }
}
```

### `GET /api/download-tactics`
Download global aggregated tactics

**Response:** See "Cloudflare → Mod" example above

### `GET /api/stats`
View global statistics

**Response:**
```json
{
  "global": {
    "totalSubmissions": 5420,
    "startTime": 1702100000000
  },
  "perMob": {
    "zombie": {
      "submissions": 1850,
      "uniqueTactics": 12,
      "lastUpdate": 1702156800000,
      "topTactic": {
        "action": "circle_strafe",
        "avgReward": 8.5,
        "successRate": 0.87
      }
    }
  }
}
```

### `GET /api/analyze-tactics`
AI-powered analysis using Cloudflare Workers AI (Llama 3.1)

**Response:**
```json
{
  "timestamp": 1702156800000,
  "analyses": {
    "zombie": {
      "submissions": 1850,
      "topTactics": [...],
      "aiInsights": "Circle strafing proves most effective with 87% success rate..."
    }
  },
  "model": "Llama 3.1 8B Instruct"
}
```

### `GET /api/process-pipeline`
Multi-stage AI processing with HuggingFace cross-validation

## Development

### Local Testing
```bash
npm run dev
```

Worker runs at `http://localhost:8787`

### View Logs
```bash
npm run tail
```

### Test Submit Endpoint
```bash
curl -X POST http://localhost:8787/api/submit-tactics \
  -H "Content-Type: application/json" \
  -d '{
    "mobType": "zombie",
    "action": "circle_strafe",
    "reward": 8.5,
    "outcome": "success",
    "timestamp": 1702156800000
  }'
```

### Test Download Endpoint
```bash
curl http://localhost:8787/api/download-tactics
```

## Compatibility with Mod

### What the Mod Sends
- `mobType`: String (zombie, skeleton, creeper, spider, etc.)
- `action`: String (tactic name like "circle_strafe")
- `reward`: Float (combat reward, typically 0-20)
- `outcome`: String ("success" or "failure")
- `timestamp`: Long (milliseconds since epoch)

### What the Mod Expects Back
- `version`: String (data format version)
- `timestamp`: Long (when data was retrieved)
- `tactics`: Object mapping mob types to tactic arrays
  - Each mob has `submissions`, `lastUpdate`, and `tactics` array
  - Each tactic has `action`, `avgReward`, `count`, `successRate`, etc.

### Breaking Changes from v1.2.0
- ✅ Added `successRate`, `successCount`, `failureCount` fields
- ✅ Added `lastUpdate` timestamp to each tactic
- ✅ Properly handles `outcome` field from submissions
- ✅ Returns complete data structure mod expects

## Monitoring

### Check Worker Status
```bash
wrangler deployments list
```

### View KV Data
```bash
wrangler kv:key list --namespace-id=YOUR_KV_NAMESPACE_ID
wrangler kv:key get --namespace-id=YOUR_KV_NAMESPACE_ID "tactics:zombie"
```

### Clear All Data (Reset)
```bash
wrangler kv:key delete --namespace-id=YOUR_KV_NAMESPACE_ID "tactics:zombie"
wrangler kv:key delete --namespace-id=YOUR_KV_NAMESPACE_ID "tactics:skeleton"
wrangler kv:key delete --namespace-id=YOUR_KV_NAMESPACE_ID "tactics:creeper"
wrangler kv:key delete --namespace-id=YOUR_KV_NAMESPACE_ID "tactics:spider"
wrangler kv:key delete --namespace-id=YOUR_KV_NAMESPACE_ID "global:stats"
```

## Cost Estimation

**Cloudflare Workers Free Tier:**
- 100,000 requests/day
- 10ms CPU time per request
- Workers KV: 100,000 reads/day, 1,000 writes/day

**Expected Usage (1000 active players):**
- ~50,000 submissions/day (50 writes/player)
- ~10,000 downloads/day (10 downloads/player)
- **Well within free tier limits**

**Paid Plan ($5/month):**
- 10 million requests/month
- Unlimited CPU time
- Workers KV: Unlimited reads, 1 million writes/month

## Troubleshooting

### "KV namespace not found"
Run: `wrangler kv:namespace create "TACTICS_KV"`
Update `id` in `wrangler.toml`

### "AI binding not available"
Workers AI requires Cloudflare paid plan for `/api/analyze-tactics` and `/api/process-pipeline`
Core federated learning (`/api/submit-tactics`, `/api/download-tactics`) works on free tier

### "CORS errors in browser"
Worker includes proper CORS headers. Mod uses `HttpURLConnection` (no CORS issues)

### "No data in download endpoint"
Submit some tactics first, or check KV namespace has data:
```bash
wrangler kv:key get --namespace-id=YOUR_KV_NAMESPACE_ID "tactics:zombie"
```

## Security Considerations

- ✅ CORS enabled for mod compatibility
- ✅ Input validation on all endpoints
- ✅ Mob type whitelist prevents malicious submissions
- ✅ Rate limiting via Cloudflare's built-in DDoS protection
- ⚠️ No authentication (public API by design for federated learning)
- ⚠️ Consider adding rate limits in production

## Version History

### v1.3.0 (Current)
- Fixed success rate tracking
- Fixed timestamp storage
- Added complete data model
- Extended mob support
- Enhanced statistics

### v1.2.0
- Multi-stage AI pipeline
- HuggingFace cross-validation
- AI analysis endpoints

### v1.0.0
- Initial release
- Basic submit/download
- KV storage
