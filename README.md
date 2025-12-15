# MCA AI Enhanced - Adaptive Mob AI

**Real federated learning for Minecraft mob behavior.**  
Mobs learn tactics across all servers in real-time. Drop-in, zero-config, production-ready.

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg)](https://minecraft.net)
[![Forge Version](https://img.shields.io/badge/Forge-47.2.0+-orange.svg)](https://files.minecraftforge.net)
[![Java Version](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org)

---

##  Quick Start

1. **Download**: Get `Adaptive-Mob-Ai-1.1.8.jar` from [releases](https://github.com/smokydastona/Adaptive-Minecraft-Mob-Ai/releases)
2. **Install**: Drop in your `mods/` folder
3. **Play**: Federation works automatically (zero config needed)

That''s it. Mobs start learning from all servers globally.

---

##  What It Does

### Federated Learning
- **Global AI**: All servers contribute to one shared model
- **Real-time**: New tactics distributed every 10 minutes
- **Zero config**: Works out-of-the-box, no credentials needed
- **Graceful degradation**: Offline mode if network unavailable

### Smart Mobs
- **Adaptive tactics**: Learn from combat outcomes
- **8 mob types**: Zombie, Skeleton, Creeper, Spider, Husk, Stray, Wither Skeleton, Enderman
- **Difficulty scaling**: 0.5 (easy) to 3.0 (very hard)

### Optional MCA Integration
- **Villager personalities**: Mood-based dialogue (requires MCA Reborn)
- **Dynamic interactions**: Evolving relationships

---

##  Federation Status

**Live**: https://mca-ai-tactics-api.mc-ai-datcol.workers.dev/status

**GitHub Logs**: https://github.com/smokydastona/adaptive-ai-federation-logs

**In-game**: `/mcaai federation`

---

##  Documentation

### Getting Started
- [Installation Guide](docs/INSTALLATION.md) - Setup and configuration
- [Features Overview](docs/FEATURES.md) - What the mod does
- [Supported Mobs](docs/SUPPORTED_MOBS.md) - Complete mob list
- [Architecture](docs/ARCHITECTURE.md) - How it works

### Deployment
- [Server Deployment](docs/SERVER_DEPLOYMENT.md) - Running on servers
- [Performance](docs/PERFORMANCE.md) - Optimization tips
- [Mod Compatibility](docs/MOD_COMPATIBILITY.md) - Works with other mods

### Federation
- [Federated Learning](docs/FEDERATED_LEARNING.md) - Global AI system
- [Setup Guide](docs/SETUP_FEDERATED_LEARNING.md) - Advanced configuration

### Cloudflare Worker
- [Deployment Guide](docs/cloudflare-worker/DEPLOYMENT.md) - Worker setup
- [GitHub Setup](docs/cloudflare-worker/GITHUB_SETUP.md) - Logging configuration
- [Advanced ML Guide](docs/cloudflare-worker/ADVANCED_ML_GUIDE.md) - ML internals

### Legacy
- [Old Documentation](docs/legacy/) - Historical references

---

##  Commands

```
/mcaai info          # Mod status and MCA detection
/mcaai stats         # AI statistics and active features
/mcaai federation    # Federation status (round, contributors)
/mcaai test dialogue # Test dialogue (requires MCA)
```

---

##  Configuration

Auto-generated at `config/adaptivemobai-common.toml`:

```toml
[mobai]
enableMobAI = true
aiDifficulty = 1.0  # 0.5 (easy) to 3.0 (very hard)

[federation]
enableFederatedLearning = true
cloudApiEndpoint = "https://mca-ai-tactics-api.mc-ai-datcol.workers.dev"

[mobs]
enableZombie = true
enableSkeleton = true
# ... per-mob toggles
```

---

##  Building from Source

```bash
git clone https://github.com/smokydastona/Adaptive-Minecraft-Mob-Ai.git
cd Adaptive-Minecraft-Mob-Ai
.\gradlew build

# Output: build/libs/Adaptive-Mob-Ai-1.1.8.jar
```

**Requirements**: Java 17, Forge MDK

---

##  How It Works

### Architecture
```
Minecraft Server (Mod)
        
Cloudflare Worker (API)
        
Durable Object (Coordinator)
        
Global Model (FedAvg)
        
GitHub (Flight Recorder)
```

### Federation Flow
1. **Server starts**  Downloads global model
2. **First combat**  Uploads tactics (bootstrap)
3. **Every 10 min**  Round closes, aggregates
4. **All servers**  Get updated tactics

### Key Features
- **Durable Object**: Single source of truth
- **Round Finality**: Max 10 contributors OR 10 minutes
- **Forced Traffic**: Startup pull + bootstrap upload guaranteed
- **GitHub Logging**: Optional async debug logs

---

##  Testing

### Verify Federation
```bash
# Check worker status
curl https://mca-ai-tactics-api.mc-ai-datcol.workers.dev/status

# Check GitHub logs
open https://github.com/smokydastona/adaptive-ai-federation-logs
```

### Server Logs
```
[INFO] [Federation] Connected (Round 15, 12 contributors, 3 mob types)
[INFO]  FIRST ENCOUNTER: zombie - uploading bootstrap data
[INFO]  Bootstrap upload successful for zombie
```

---

##  Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

**Latest (1.1.8)**:
-  Federated Learning v3.0.0 (complete rewrite)
-  Decisive round finality
-  Forced startup pull
-  GitHub flight recorder
-  Proof-of-life status endpoint

---

##  Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing`)
3. Commit changes (`git commit -am ''Add amazing feature'')
4. Push to branch (`git push origin feature/amazing`)
5. Open Pull Request

---

##  License

This project is licensed under the MIT License - see [LICENSE](LICENSE) file.

---

##  Credits

- **MCA Reborn** - Villager integration support
- **Cloudflare Workers** - Federation infrastructure
- **Forge** - Minecraft modding framework
- **DJL** - Machine learning inference

---

##  Links

- **Releases**: https://github.com/smokydastona/Adaptive-Minecraft-Mob-Ai/releases
- **Issues**: https://github.com/smokydastona/Adaptive-Minecraft-Mob-Ai/issues
- **Worker Status**: https://mca-ai-tactics-api.mc-ai-datcol.workers.dev/status
- **Federation Logs**: https://github.com/smokydastona/adaptive-ai-federation-logs

---

##  Support

- **Issues**: Open a GitHub issue with logs
- **Federation**: Check `/status` endpoint for health
- **Docs**: See `docs/` folder for guides

---

**Built with  for the Minecraft community**
