# MCA AI Enhanced - Federated Learning Server (Cloudflare Worker)

This folder contains the single Cloudflare Worker that powers global federated learning for the mod.

## One Worker File (Canonical)

- Canonical entrypoint: `worker.js`
- Deployed by Wrangler via `wrangler.toml` (`main = "worker.js"`)

## Architecture (v3 Durable Object Coordinator)

Federation state is coordinated by a Durable Object to provide:

- Single source of truth for rounds
- Atomic aggregation (FedAvg)
- Duplicate/late submission prevention

Optional extras:

- GitHub observability logging (write-only)
- Deterministic analysis endpoint with optional Workers AI summarization

## API Endpoints

- `GET /health`
- `GET /status`
- `POST /api/upload`
- `GET /api/global`
- `POST /api/heartbeat`
- `POST /api/episodes`
- `GET /api/tactical-weights`
- `GET /api/tactical-stats`
- `GET /api/analyze-tactics`
- `POST /admin/init-github`
- `POST /admin/reset-round`

## Deployment

### Prerequisites

- Cloudflare account
- Node.js and npm installed
- Wrangler CLI (this project uses Wrangler v3)

### Steps

1. `wrangler login`
2. Ensure `wrangler.toml` is configured (Durable Object + KV binding)
3. `npm run deploy`

### Mod config

If you arent using the bundled default endpoint, set this in `config/adaptivemobai-common.toml`:

```toml
[federated_learning]
enableFederatedLearning = true
cloudApiEndpoint = "https://YOUR_WORKER_SUBDOMAIN.workers.dev"
```

## Local Development

- `npm run dev` (serves at `http://localhost:8787`)
- `wrangler tail` (stream logs)

## Privacy

- No player identifiers are accepted or returned.
- No IP logging is performed.
- `/api/analyze-tactics` returns aggregate-only insights.
