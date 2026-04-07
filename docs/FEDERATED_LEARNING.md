# Federated Learning Guide

## Overview
Adaptive Mob AI federates learned tactics through the public Cloudflare Worker endpoint configured in `config/adaptivemobai-common.toml`. There is no Git-based sync workflow in normal operation anymore.

What is shared:
- Aggregated mob tactic outcomes
- Anonymous success and reward statistics
- Server-generated model snapshots keyed by mob type

What is not shared:
- Player names
- Chat or world data
- IP addresses
- Raw combat logs

## Default Behavior
With the default config, the mod will:
- Download global tactics shortly after server startup
- Submit local tactic models on a recurring interval
- Continue in local-only mode if the endpoint is unavailable

The default status endpoint is:

```text
https://mca-ai-tactics-api.mc-ai-datcol.workers.dev/status
```

## Configuration
Edit `config/adaptivemobai-common.toml` and use the real `federated_learning` keys:

```toml
[federated_learning]
enableFederatedLearning = true
cloudApiEndpoint = "https://mca-ai-tactics-api.mc-ai-datcol.workers.dev"
cloudApiKey = ""
minDataPointsToContribute = 10
autoDownloadKnowledge = true
autoUploadKnowledge = true
syncIntervalMinutes = 5
```

Notes:
- `cloudApiKey` can stay empty for the public endpoint.
- `federatedRepoUrl` is a legacy fallback key and should be treated as deprecated.
- Raising `syncIntervalMinutes` lowers network activity.

## Monitoring
The in-game command surface is:

```text
/amai status
```

Use it to check whether federation is enabled and whether the Cloudflare endpoint is reachable.

You can also inspect the live service directly:

```text
https://mca-ai-tactics-api.mc-ai-datcol.workers.dev/status
```

## Troubleshooting
If the endpoint is offline or rate-limited:
- The mod falls back to local learning automatically.
- Check `/amai status` for the current federation state.
- Review `logs/latest.log` for Cloudflare API warnings.
- If needed, temporarily disable federation by setting `enableFederatedLearning = false`.

If you want to host your own endpoint, follow the Cloudflare worker setup guide in `docs/SETUP_FEDERATED_LEARNING.md` and point `cloudApiEndpoint` at your deployment.
