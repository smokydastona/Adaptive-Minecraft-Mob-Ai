# MCA AI Enhanced - Machine Learning Mob Behavior

A Minecraft 1.20.1 Forge mod that uses **actual machine learning** to make mobs progressively harder over time. Mobs learn from combat encounters using a Deep Q-Network and adapt their tactics to your playstyle.

## Features

### 🧠 Real Machine Learning
- **Deep Q-Network** with experience replay
- **Progressive difficulty** - mobs genuinely learn and improve
- **Online training** - neural network trains during gameplay
- **Model persistence** - learned behavior saves across sessions
- **Player-adaptive** - discovers tactics that work against YOU

### 🎮 Enhanced Mob AI
- **4 mob types**: Zombie, Skeleton, Creeper, Spider
- **10 tactical behaviors**: Circle strafe, retreat, ambush, kiting, etc.
- **State-aware decisions**: Considers health, distance, positioning, time, biome
- **Coordinated attacks**: Learns to work with nearby allies

### 💬 MCA Reborn Integration (Optional)
- AI-powered villager dialogue generation
- Evolving personalities and moods
- 40+ context-aware dialogue templates
- Soft dependency - mob AI works without MCA

## Quick Start

### Requirements
- Minecraft 1.20.1
- Forge 47.2.0+
- Java 17

### Installation
1. Download latest JAR from releases
2. Place in `mods/` folder
3. Launch Minecraft with Forge

### Testing
```
/mcaai stats    - View ML training progress
/mcaai info     - Show mod features
```

## How It Works

Mobs use a **neural network** trained via **reinforcement learning**:

1. **Observe** combat state (health, distance, positioning)
2. **Predict** Q-values for each action using neural network
3. **Execute** highest-value action (90% of time) or explore (10%)
4. **Learn** from outcome: rewards for damage, kills; penalties for death
5. **Train** network every 4 combats using experience replay
6. **Improve** over time as network discovers optimal tactics

### Progression
- **Early game** (0-100 combats): Random exploration, learning basics
- **Mid game** (100-500): Tactical patterns emerge, 50% competent
- **Late game** (500+): Optimized behavior, exploits player weaknesses

## Documentation

- **[ML_IMPLEMENTATION.md](ML_IMPLEMENTATION.md)** - Deep dive into neural network architecture
- **[QUICK_START_ML.md](QUICK_START_ML.md)** - Setup and testing guide
- **[AI_MOD_README.md](AI_MOD_README.md)** - User-facing features
- **[.github/copilot-instructions.md](.github/copilot-instructions.md)** - Developer guide

## Building from Source

### Prerequisites
```bash
# If gradlew is missing, copy from another Forge 1.20.1 project
# or generate with: gradle wrapper --gradle-version=8.1.1
```

### Build
```bash
.\gradlew build          # Output: build/libs/mca-ai-enhanced-1.0.0.jar
.\gradlew runClient      # Test in development environment
```

## Configuration

Edit `config/mca-ai-enhanced-common.toml`:

```toml
# AI difficulty multiplier (affects learning speed and exploration)
aiDifficulty = 1.0  # Range: 0.5 (easy) to 3.0 (very hard)

# Enable/disable mob AI
enableMobAI = true

# Per-mob toggles
zombieAI = true
skeletonAI = true
creeperAI = true
spiderAI = true
```

## Technical Details

### Architecture
- **Framework**: Deep Java Library (DJL) with PyTorch
- **Algorithm**: Deep Q-Learning with experience replay
- **Network**: 10 inputs → 64 hidden → 64 hidden → 10 Q-values
- **Training**: Adam optimizer, batch size 32, learning rate 0.001
- **Exploration**: Epsilon-greedy (1.0 → 0.1 decay)

### Performance
- **Memory**: ~15MB per world (replay buffer + network)
- **CPU**: <1ms per mob action selection, ~20ms per training step
- **Storage**: ~200KB saved model file

## Credits

**Algorithm inspiration:**
- DeepMind's DQN (Deep Q-Network)
- Sutton & Barto - Reinforcement Learning textbook
- OpenAI Gym environment patterns

**Dependencies:**
- Deep Java Library (DJL) 0.25.0
- PyTorch engine
- MCA Reborn (optional, for villager dialogue)

## License

See [LICENSE](LICENSE) file.

## Support

For issues, questions, or suggestions, please open a GitHub issue.

---

**This is REAL machine learning.** Mobs use gradient descent to optimize a neural network through reinforcement learning, not scripted difficulty scaling.
