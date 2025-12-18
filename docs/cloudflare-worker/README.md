# Cloudflare Worker Docs (Legacy)

The Cloudflare Worker implementation in this repo is now consolidated to a single v3 Durable Object worker:

- Canonical code: `cloudflare-worker/worker.js`
- Canonical deployment config: `cloudflare-worker/wrangler.toml`
- Canonical documentation: `cloudflare-worker/README.md`

The other documents in this folder were written for earlier iterations (v1/v2) and may reference endpoints that no longer exist (for example `/api/submit-tactics`, `/api/download-tactics`, `/api/process-pipeline`).

If you’re deploying today, start with `cloudflare-worker/README.md`.
