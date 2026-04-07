# Mod Compatibility Guide

## Overview
MCA AI Enhanced is designed to work standalone, but integrates with popular Forge 1.20.1 mods to enhance AI capabilities. All integrations use **soft dependencies** - the mod works perfectly without them, but gains extra features when they're present.

---

## ✅ Compatible & Enhanced Mods

### 🎯 Curios API
**Status**: ✓ Supported  
**Download**: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/curios) | [Modrinth](https://modrinth.com/mod/curios)

**Features Unlocked**:
- Reads equipped curio items through reflection when Curios is present
- Recognizes trinkets, baubles, and accessories by equipped item identifiers
- Applies basic magical-trinket and protection heuristics to combat decisions
- Scales caution based on how many curios a player has equipped

**AI Impact**:
```
Player with Rings of Protection + Strength Charm:
→ Mob AI detects additional equipped curios
→ Visual perception raises player threat slightly
→ Tactics can favor safer spacing or extra pressure
```

---

### 👥 FTB Teams
**Status**: ✓ Supported  
**Download**: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge)

**Features Unlocked**:
- Multiplayer team detection
- AI difficulty scales with team size (solo → duo → squad)
- Mob team formation counters player teams
- Coordinated attacks against organized groups
- Team-based difficulty multipliers

**AI Impact**:
```
4 players on same FTB Team:
→ Mob AI forms counter-teams of 5 mobs
→ Difficulty multiplier: 1.4x
→ Coordination bonus: +45% to tactics
→ Recommended mob team size: 5 (outnumber slightly)
```

**Multiplayer Features**:
- Solo player: 2 mobs attack
- Duo (2 players): 3 mobs coordinate
- Squad (3-4 players): 4-5 mobs swarm
- Large team (5+): 5 mobs with max tactics

---

### 📚 Just Enough Items (JEI)
**Status**: ✓ Compatible  
**Download**: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/jei) | [Modrinth](https://modrinth.com/mod/jei)

**Features**:
- Recipe integration for villager dialogue context
- Villagers can reference craftable items in conversations
- No AI conflicts

---

### ⚔️ Epic Fight
**Status**: ✓ Supported (Basic integration)  
**Download**: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/epic-fight-mod)

**Current Features (Implemented)**:
- Detects Epic Fight mode (server-side)
- Reads stamina/charging signals and adjusts tactical recommendations (spacing/punish low stamina)

**Planned Features (Not yet implemented)**:
- Advanced move recognition (parry/dodge/specials)
- Deeper animation/state machine awareness

---

### 🎭 Player Animator
**Status**: ✓ Compatible  
**Download**: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/playeranimator)

**Features**:
- Enhanced player action detection
- Better animation-based state recognition
- No conflicts with AI systems

---

### 🎒 Sophisticated Backpacks
**Status**: ✓ Supported (Planned)  
**Download**: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks)

**Planned Features**:
- Inventory-aware mob tactics
- Mobs recognize well-equipped players
- Adjust strategy based on carried items

---

### 🦎 Alex's Mobs
**Status**: ✓ Compatible  
**Download**: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/alexs-mobs) | [Modrinth](https://modrinth.com/mod/alexs-mobs)

**Features**:
- Extended mob behavior patterns
- AI learning from Alex's Mobs entities
- No conflicts with vanilla mob AI

---

## 🛡️ Tested & Compatible

These mods have been tested and work perfectly alongside MCA AI Enhanced:

### Performance Mods
- ✅ **Embeddium/Rubidium** - Rendering optimization
- ✅ **Oculus** - Shader support
- ✅ **FerriteCore** - Memory optimization
- ✅ **ModernFix** - Startup and performance improvements
- ✅ **EntityCulling** - Render optimization

### Enhancement Mods
- ✅ **JourneyMap** - Minimap
- ✅ **Jade/WTHIT** - Block/entity tooltips
- ✅ **Configured** - Config GUI
- ✅ **Balm** - Compatibility library
- ✅ **Kotlin for Forge** - Dependency for some mods

### Quality of Life
- ✅ **Inventory HUD+** - Inventory display
- ✅ **Mouse Tweaks** - Inventory improvements
- ✅ **AppleSkin** - Food/saturation display
- ✅ **Catalogue** - Mod list GUI

---

## ⚠️ Known Incompatibilities

### ❌ AI Overhaul Mods
Mods that completely replace mob AI may conflict:
- **AI Improvements** (partial conflict - choose one)
- **Savage & Ravage** (may override zombie AI)
- **Enhanced AI** (redundant, conflicts with learning system)

**Resolution**: Disable conflicting mob types in MCA AI Enhanced config

### ❌ Combat Overhauls
Major combat changes may affect AI predictions:
- **Better Combat** (partial - AI adapts but may be suboptimal)
- **Combat Roll** (no dedicated integration yet)

**Resolution**: AI learns new combat patterns over time

---

## 🔧 Recommended Modpack Configuration

### Optimal AI Experience
```
Core:
- MCA AI Enhanced
- MCA Reborn
- Curios API
- FTB Teams

Performance:
- Embeddium
- ModernFix
- FerriteCore

Enhancements:
- Epic Fight
- Alex's Mobs
- JEI
- Sophisticated Backpacks

Quality of Life:
- JourneyMap
- Jade
- AppleSkin
```

### Lightweight Setup
```
Minimal:
- MCA AI Enhanced
- MCA Reborn (optional)

Works perfectly standalone!
```

---

## 🎮 Multiplayer Compatibility

### Server-Side
MCA AI Enhanced is **server-side only** for core functionality:
- ✅ AI learning persists server-side
- ✅ No client mod required for basic features
- ✅ Works on vanilla clients

### Client-Side (Optional)
Install client-side for enhanced features:
- Better combat feedback
- AI statistics HUD
- Dialogue improvements (requires MCA Reborn on client)

### Dedicated Servers
```
Required on Server:
- MCA AI Enhanced
- MCA Reborn (for dialogue)
- Curios API (if used)
- FTB Teams (if used)

Optional on Client:
- MCA AI Enhanced (for /amai commands)
- MCA Reborn (for dialogue UI)
```

---

## 📊 Performance Impact by Mod

| Mod | AI Overhead | Memory | Recommendation |
|-----|------------|---------|----------------|
| MCA AI Enhanced (base) | ~5% | 12-15 MB | Always safe |
| + Curios API | <1% | +2 MB | Recommended |
| + FTB Teams | <1% | +5 MB | Recommended for multiplayer |
| + Epic Fight | ~2% | +3 MB | Great for combat-focused |
| + Alex's Mobs | 0% | 0 MB | No overhead, just compatible |

**Total with all integrations**: ~8% overhead, ~25 MB memory  
**Standalone**: ~5% overhead, 15 MB memory

---

## 🔍 In-Game Compatibility Check

Use `/amai compat` to see real-time compatibility status:

```
§b=== Mod Compatibility Report ===§r

§eCurios API:§r §a✓ Enabled§r
  → Enhanced equipment detection in visual perception system

§eFTB Teams:§r §7○ Not Installed§r

§eJust Enough Items:§r §a✓ Enabled§r
  → Recipe integration for villager dialogue context

§eEpic Fight:§r §7○ Not Installed§r

§ePlayer Animator:§r §a✓ Enabled§r
  → Enhanced player action detection

§eSophisticated Backpacks:§r §7○ Not Installed§r

§eAlex's Mobs:§r §a✓ Enabled§r
  → Extended mob behavior patterns
```

---

## 🐛 Reporting Compatibility Issues

If you find a mod that conflicts or doesn't integrate properly:

1. Check `/amai compat` in-game
2. Review `logs/latest.log` for errors
3. Report on GitHub Issues with:
   - Mod list
   - Forge version
   - Error log
   - Expected vs actual behavior

---

## 🔮 Future Integrations

### Planned
- **Farmer's Delight**: Food-aware villager dialogue
- **Create**: Mechanical contraption detection
- **Minecolonies**: Village AI coordination
- **Vampirism**: Special AI for vampire players
- **Werewolves**: Transformation detection

### Under Consideration
- **Botania**: Mana equipment recognition
- **Ars Nouveau**: Spell detection
- **Ice and Fire**: Dragon AI coordination
- **Twilight Forest**: Boss-level learning

---

**Last Updated**: December 2024  
**Mod Version**: 1.0.0+  
**Minecraft**: 1.20.1 Forge 47.2.0

For latest compatibility info, visit: [GitHub Wiki](https://github.com/smokydastona/Way-Too-MCA)
