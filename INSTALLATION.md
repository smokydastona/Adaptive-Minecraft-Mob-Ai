# MCA AI Enhanced - Installation Guide

## Download Files

Every release includes **TWO JAR files**:

1. **ai-overhaul-experiment-1.0.X.jar** (~1-2 MB) - **REQUIRED**
   - Main mod with all features
   - Works standalone with rule-based tactical AI

2. **ai-overhaul-experiment-1.0.X-libs.jar** (~50 MB) - **OPTIONAL**
   - Machine learning libraries (DJL + PyTorch)
   - Enables neural network learning systems

## Installation Options

### Option 1: Basic Installation (Recommended for Most Users)
Install **ONLY** the main JAR:
```
mods/
└── ai-overhaul-experiment-1.0.X.jar
```

**What you get:**
- ✅ Rule-based tactical AI (circle strafe, retreat, coordinated attacks)
- ✅ Federated learning (global knowledge sharing via Git)
- ✅ Tactic knowledge base (RAG-style learning)
- ✅ All 12 AI systems in rule-based mode
- ✅ MCA villager dialogue AI
- ✅ Config system
- ⚠️ No neural network learning (DQN disabled)

### Option 2: Full ML Installation (Advanced Users)
Install **BOTH** JAR files:
```
mods/
├── ai-overhaul-experiment-1.0.X.jar
└── ai-overhaul-experiment-1.0.X-libs.jar
```

**What you get:**
- ✅ Everything from Option 1
- ✅ Double DQN neural network learning
- ✅ Prioritized experience replay
- ✅ Curriculum learning (progressive difficulty)
- ✅ Visual perception system
- ✅ Genetic behavior evolution
- ✅ All 12 AI systems fully enabled

## How It Works

The main mod checks if DJL libraries are available at runtime:

```
[Server Start] → Check for DJL classes
              ↓
         Found? ━━━━━━━━━━┓
         ↓ YES         ↓ NO
    Enable ML      Use Rules
    (Full AI)      (Fallback)
```

**No configuration needed** - it auto-detects the libs JAR!

## Performance Comparison

| Mode | RAM Usage | CPU Usage | Features |
|------|-----------|-----------|----------|
| **Rule-based** (1 JAR) | ~50 MB | <3% | Tactical AI + Knowledge Sharing |
| **Full ML** (2 JARs) | ~200 MB | <8% | Neural Networks + Everything |

## Troubleshooting

### "ML systems disabled" in logs
- **Expected** if you only installed the main JAR
- **Install libs JAR** if you want neural network learning
- Not an error - mod works perfectly without it!

### Crash on startup with both JARs
- **Remove the libs JAR** - your system may not support PyTorch
- Rule-based AI is just as effective for most gameplay

### Which option should I choose?
- **Single player / small server**: Use both JARs for full ML
- **Large server / low RAM**: Use only main JAR for performance
- **Unsure**: Start with main JAR only, add libs later if desired

## Version Compatibility

**IMPORTANT:** Both JARs must have the **same version number**!

✅ Correct:
```
ai-overhaul-experiment-1.0.25.jar
ai-overhaul-experiment-1.0.25-libs.jar
```

❌ Wrong:
```
ai-overhaul-experiment-1.0.25.jar
ai-overhaul-experiment-1.0.22-libs.jar  ← Different version!
```

## Uninstallation

Remove **both JARs** from your mods folder. Config files in `config/` can be deleted if desired.

---

**Need help?** Check logs in `logs/latest.log` for startup messages about ML system status.
