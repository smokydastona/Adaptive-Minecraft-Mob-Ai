# MCA AI Enhanced - Intelligent Villagers & Mobs
### Machine Learning Powered Behavior for Minecraft 1.20.1

An addon for **[MCA Reborn](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn)** that uses AI and machine learning to make mobs smarter and villagers more lifelike!

![Powered by AI](https://img.shields.io/badge/Powered%20by-AI%2FML-blue)
![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-green)
![Forge](https://img.shields.io/badge/Forge-47.2.0+-orange)

---

## 🤖 What Does This Mod Do?

This mod enhances Minecraft AI using machine learning:

### 🧟 **Smart Mob AI**
- Mobs learn and adapt their combat strategies
- Different attack patterns based on situation
- Tactical decisions (retreat, ambush, circle strafe)
- Learning system that improves over time

### 💬 **Intelligent Villager Dialogue** (MCA Reborn Required)
- AI-generated contextual responses
- Evolving personalities that change based on interactions
- Mood tracking (happy, sad, angry, excited, etc.)
- 40+ dialogue templates with endless variations
- Remembers your conversations and relationship

---

## 📦 Installation

### Requirements

1. **Minecraft 1.20.1**
2. **Forge 47.2.0+**
3. **[MCA Reborn](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn)** (for villager features)

### Steps

1. Install **Forge 1.20.1**
2. Install **MCA Reborn** (optional but recommended)
3. Place this mod in your `mods` folder
4. Launch and enjoy smarter mobs!

---

## 🎮 Features

### Adaptive Mob Combat AI

#### Zombie Behaviors
- **Straight Charge** - Direct aggressive attack
- **Circle Strafe** - Surround and confuse the player
- **Group Rush** - Coordinate with nearby zombies

#### Skeleton Tactics
- **Kite Backward** - Maintain distance while shooting
- **Find High Ground** - Seek tactical advantage
- **Strafe Shoot** - Move sideways while attacking
- **Retreat Reload** - Fall back when player gets close

#### Creeper Strategies
- **Ambush** - Wait for player to get close
- **Stealth Approach** - Sneak up quietly
- **Fake Retreat** - Pretend to run, then explode
- **Suicide Rush** - Aggressive kamikaze attack

#### Spider Abilities
- **Wall Climb Attack** - Use vertical mobility
- **Ceiling Drop** - Surprise from above
- **Leap Attack** - Jump at player
- **Web Trap** - Use webs strategically

### MCA Villager AI Features

#### Dynamic Dialogue
- Contextual responses based on:
  - Time of day
  - Relationship level  
  - Recent interactions
  - Gifts received
  - Current mood
  - Personality traits

#### Personality System
7 evolving personality traits:
- **Friendly** - Warm and welcoming
- **Shy** - Nervous and reserved
- **Witty** - Clever and sarcastic
- **Romantic** - Flirty and affectionate
- **Intellectual** - Thoughtful and deep
- **Cheerful** - Happy and energetic
- **Sarcastic** - Dry humor

#### Mood States
Villagers track their mood:
- 😊 Happy - After gifts or positive interactions
- 😢 Sad - When ignored or rejected
- 😠 Angry - After negative encounters
- 😐 Neutral - Default state
- 🎉 Excited - Special occasions
- 😴 Tired - Late at night

---

## 🎯 Commands

### Test Dialogue Generation
```
/mcaai test dialogue <type>
```
Types: `greeting`, `small_talk`, `gift_positive`, `flirt`, `request_help`

Example:
```
/mcaai test dialogue greeting
```

### View Mod Information
```
/mcaai info
```
Shows:
- Mob AI status
- MCA integration status
- Active features
- Available commands

### View Statistics
```
/mcaai stats
```
Displays:
- Active mob behaviors
- Dialogue templates count
- Personality traits
- Learning status

---

## ⚙️ Configuration

Edit `config/mca-ai-enhanced-common.toml`:

```toml
[general]
enableMobAI = true
enableVillagerDialogue = true
enableLearning = true
aiDifficulty = 1.0  # 0.5-3.0

[mob_behaviors]
enableZombieAI = true
enableSkeletonAI = true
enableCreeperAI = true
enableSpiderAI = true
aiUpdateInterval = 20  # ticks

[villager_dialogue]
dialogueVariations = 3
enablePersonalityLearning = true
enableMoodSystem = true
dialogueRandomness = 0.3

[advanced]
modelsPath = "models"
debugAI = false
experimentalFeatures = false
```

---

## 🧠 How It Works

### Mob AI System

1. **State Assessment** - Mob analyzes:
   - Health (self and target)
   - Distance to target
   - Environmental factors (terrain, time, biome)
   - Nearby allies

2. **Action Selection** - AI chooses best action using:
   - Rule-based decision trees
   - Success/failure tracking
   - Weighted probability

3. **Learning** - Mobs improve over time:
   - Track which actions succeed
   - Adjust behavior based on outcomes
   - Adapt to player strategies

### Villager Dialogue System

1. **Context Gathering**:
   - Player name and relationship
   - Current situation
   - Recent interactions
   - Time and location

2. **Personality Application**:
   - Select dominant trait
   - Apply mood modifiers
   - Choose appropriate templates

3. **Generation**:
   - Fill in context variables
   - Add personality flavor
   - Include mood indicators

4. **Learning**:
   - Track successful dialogue
   - Evolve personality over time
   - Remember preferences

---

## 🔧 Building from Source

```powershell
git clone https://github.com/BluShine/Minecraft-GAN-City-Generator.git
cd Minecraft-GAN-City-Generator
.\gradlew build
```

Find the JAR in `build/libs/`

### Run in Development
```powershell
.\gradlew runClient
```

---

## 🐛 Troubleshooting

### Mobs seem too difficult
- Lower `aiDifficulty` in config (try 0.7)
- Disable specific mob types
- Disable learning system

### Villagers not talking differently
- Make sure MCA Reborn is installed
- Check `/mcaai info` for MCA status
- Try `/mcaai test dialogue greeting`

### Performance issues
- Increase `aiUpdateInterval` (try 40)
- Disable learning
- Reduce number of active mobs

---

## 🚀 Roadmap

- [ ] **v1.1** - Pre-trained AI models included
- [ ] **v1.2** - GUI for testing dialogue
- [ ] **v1.3** - More mob types (Endermen, Pillagers)
- [ ] **v1.4** - Voice synthesis integration
- [ ] **v1.5** - Multiplayer learning (mobs learn from all players)
- [ ] **v2.0** - Full neural network integration
- [ ] Integration with MCA's GPT-3 chat feature
- [ ] Custom personality creator
- [ ] Dialogue history system
- [ ] Emotion-based animations

---

## 💡 Tips & Tricks

### For Players
1. **Talk to villagers often** - Their personality evolves based on interactions
2. **Give thoughtful gifts** - Affects mood and dialogue
3. **Adapt your combat** - Mobs learn, so vary your strategies
4. **Night is harder** - Mob AI is more aggressive at night

### For Server Owners
1. Adjust `aiDifficulty` based on player skill
2. Enable `debugAI` to monitor performance
3. Use `/mcaai stats` to track AI activity
4. Disable specific features if needed

---

## 📜 Credits & License

### Credits
- **Concept**: Inspired by the original GAN City project by BluShine
- **MCA Reborn**: Luke100000, jahx_senpoopie, and team
- **ML Framework**: [Deep Java Library (DJL)](https://djl.ai/) by Amazon
- **Community**: Thanks to all testers and contributors!

### License
MIT License - See [LICENSE](LICENSE) file

---

## 🔗 Links

- **MCA Reborn**: https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn
- **GitHub**: https://github.com/BluShine/Minecraft-GAN-City-Generator
- **Issues**: https://github.com/BluShine/Minecraft-GAN-City-Generator/issues
- **MCA Discord**: https://discord.gg/EjYwZUJbpf

---

## ❤️ Support

If you enjoy this mod:
- ⭐ Star the repository
- 🐛 Report bugs
- 💬 Share feedback
- 📢 Tell your friends!

---

**Experience Minecraft like never before - where mobs think and villagers feel!**

*Made with ❤️ and 🤖 for the Minecraft community*
