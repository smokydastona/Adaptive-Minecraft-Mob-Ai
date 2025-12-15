# MCA AI Enhanced - System Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        MINECRAFT GAME WORLD                              │
│  ┌──────────────┐         ┌──────────────┐        ┌──────────────┐     │
│  │   Player     │ ◄─────► │  Mob Entity  │ ◄────► │ MCA Villager │     │
│  │  (Target)    │         │  (Learning)  │        │  (Dialogue)  │     │
│  └──────────────┘         └──────┬───────┘        └──────────────┘     │
└─────────────────────────────────┼──────────────────────────────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │    MobAIEnhancementMixin     │
                    │  (Inject Custom AI Goals)    │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │      MobBehaviorAI.java      │
                    │   (Central AI Coordinator)   │
                    └──┬──────────────────────────┬┘
                       │                          │
        ┌──────────────▼────────┐     ┌──────────▼───────────┐
        │   Rule-Based System   │     │  ML Systems Manager  │
        │  (Fallback/Baseline)  │     │   (6 ML Techniques)  │
        └───────────────────────┘     └──────────┬───────────┘
                                                  │
       ┌──────────────────────────────────────────┼──────────────────────────┐
       │                                          │                          │
   ┌───▼────┐ ┌──────────┐ ┌──────────┐ ┌────────▼──┐ ┌──────────┐ ┌───────▼──┐
   │ Double │ │ Priority │ │  Multi-  │ │Curriculum │ │  Visual  │ │ Genetic  │
   │  DQN   │ │  Replay  │ │  Agent   │ │ Learning  │ │Perception│ │Evolution │
   └────────┘ └──────────┘ └──────────┘ └───────────┘ └──────────┘ └──────────┘
```

---

## Combat Decision Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         COMBAT INITIATED                                │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                ┌──────────────▼──────────────┐
                │  Gather Environmental Data   │
                │  - Mob health, position      │
                │  - Player health, distance   │
                │  - Nearby allies, biome      │
                └──────────────┬──────────────┘
                               │
              ┌────────────────┴────────────────┐
              │                                  │
     ┌────────▼────────┐              ┌─────────▼─────────┐
     │ Visual Perception│              │  State Features   │
     │ Analyze Player:  │              │  Convert to       │
     │ - Armor tier     │              │  10D vector       │
     │ - Weapon type    │              └─────────┬─────────┘
     │ - Shield/stance  │                        │
     └────────┬─────────┘                        │
              │                                  │
              └────────────────┬─────────────────┘
                               │
                   ┌───────────▼───────────┐
                   │  Combine Features:    │
                   │  State(10) + Visual(7)│
                   │  + Genome(3) = 20D    │
                   └───────────┬───────────┘
                               │
                ┌──────────────▼──────────────┐
                │  Curriculum Learning Filter │
                │  Restrict to stage-allowed  │
                │  actions (3/6/9/10)         │
                └──────────────┬──────────────┘
                               │
                ┌──────────────▼──────────────┐
                │   Multi-Agent Check         │
                │   Form team if allies near  │
                │   Share teammate experiences│
                └──────────────┬──────────────┘
                               │
                ┌──────────────▼──────────────┐
                │    Double DQN Forward Pass  │
                │    - Policy network selects │
                │    - Genetic weights modify │
                │    - Visual boosts applied  │
                └──────────────┬──────────────┘
                               │
                         ┌─────▼─────┐
                         │  ACTION   │
                         │  SELECTED │
                         └─────┬─────┘
                               │
                    ┌──────────▼──────────┐
                    │  Execute in Mixin:  │
                    │  - circle_strafe    │
                    │  - group_rush       │
                    │  - ambush, etc      │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  Combat Continues   │
                    │  Track damage dealt │
                    │  and taken          │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Combat Ends       │
                    │   Record Outcome    │
                    └──────────┬──────────┘
                               │
                               ▼
                        [LEARNING PHASE]
```

---

## Learning Phase Detail

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      COMBAT OUTCOME RECORDED                            │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │  Calculate Reward:          │
                    │  +10 if player died         │
                    │  -5 if mob died             │
                    │  +0.5 per damage dealt      │
                    │  -0.3 per damage taken      │
                    └──────────────┬──────────────┘
                                   │
          ┌────────────────────────┼────────────────────────┐
          │                        │                        │
  ┌───────▼────────┐   ┌──────────▼───────┐   ┌───────────▼──────────┐
  │ Priority Replay│   │  Double DQN      │   │ Curriculum Learning  │
  │ - Add experience│   │  - Compute       │   │ - Increment counter  │
  │   with priority │   │    TD-error      │   │ - Check stage        │
  │ - Sample batch  │   │  - Train policy  │   │   advancement        │
  │ - Update        │   │  - Update target │   └──────────────────────┘
  │   priorities    │   │    every 100     │
  └────────┬────────┘   └──────────┬───────┘
           │                       │
           └───────────┬───────────┘
                       │
          ┌────────────▼────────────┐
          │    Multi-Agent Update   │
          │  - Share with team      │
          │  - Apply cooperation    │
          │    bonus if team won    │
          └────────────┬────────────┘
                       │
          ┌────────────▼────────────┐
          │  Genetic Evolution      │
          │  - Update genome fitness│
          │  - If 50 combats done:  │
          │    * Sort by fitness    │
          │    * Keep elite (4)     │
          │    * Breed offspring    │
          │    * Mutate genes (15%) │
          └─────────────────────────┘
```

---

## Data Flow Diagram

```
┌────────────┐
│   Player   │
│  Equipment │────┐
└────────────┘    │
                  │
┌────────────┐    │        ┌──────────────┐
│    Mob     │    │        │   Genetic    │
│   State    │───────┐     │   Genome     │
└────────────┘    │   │    │  (selected)  │
                  │   │    └──────┬───────┘
                  │   │           │
              ┌───▼───▼───────────▼─────┐
              │  Feature Combiner       │
              │  [20D vector]           │
              └───────────┬─────────────┘
                          │
                          │
              ┌───────────▼─────────────┐
              │  Curriculum Filter      │
              │  Remove locked actions  │
              └───────────┬─────────────┘
                          │
                          │
              ┌───────────▼─────────────┐
              │  Double DQN             │
              │  Policy Net → Q-values  │
              └───────────┬─────────────┘
                          │
                          │
              ┌───────────▼─────────────┐
              │  Action Selection       │
              │  (argmax or epsilon)    │
              └───────────┬─────────────┘
                          │
                          │
              ┌───────────▼─────────────┐
              │  Execute Action         │
              │  (Mixin injects goal)   │
              └───────────┬─────────────┘
                          │
                          │
              ┌───────────▼─────────────┐
              │  Store Transition       │
              │  (s, a, r, s', done)    │
              └───────────┬─────────────┘
                          │
                          │
              ┌───────────▼─────────────┐
              │  Prioritized Replay     │
              │  Add with high priority │
              └───────────┬─────────────┘
                          │
                          │
              ┌───────────▼─────────────┐
              │  Training Loop          │
              │  Sample → Train → Update│
              └─────────────────────────┘
```

---

## File Structure

```
src/main/java/com/minecraft/gancity/
│
├── ai/
│   ├── MobBehaviorAI.java          ← Central coordinator
│   └── VillagerDialogueAI.java     ← Dialogue system
│
├── ml/
│   ├── DoubleDQN.java              ← Neural network (policy + target)
│   ├── PrioritizedReplayBuffer.java← Smart memory
│   ├── MultiAgentLearning.java     ← Team coordination
│   ├── CurriculumLearning.java     ← Progressive difficulty
│   ├── VisualPerception.java       ← Equipment recognition
│   ├── GeneticBehaviorEvolution.java← Evolutionary algorithms
│   └── MobLearningModel.java       ← (Legacy, deprecated)
│
├── mixin/
│   └── MobAIEnhancementMixin.java  ← Inject into Mob.registerGoals()
│
├── mca/
│   └── MCAIntegration.java         ← Optional MCA Reborn link
│
├── command/
│   └── MCAICommand.java            ← /mcaai commands
│
└── GANCityMod.java                 ← Main mod class
```

---

## Memory Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    HEAP MEMORY (~15MB total)                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Double DQN (~10MB)                                            │
│  ┌─────────────────────────────────────────────────┐          │
│  │ Policy Network:                                 │          │
│  │   Input Layer (20) → Hidden (128) → Output (10)│          │
│  │   Weights: ~5MB                                 │          │
│  └─────────────────────────────────────────────────┘          │
│  ┌─────────────────────────────────────────────────┐          │
│  │ Target Network:                                 │          │
│  │   Input Layer (20) → Hidden (128) → Output (10)│          │
│  │   Weights: ~5MB                                 │          │
│  └─────────────────────────────────────────────────┘          │
│                                                                 │
│  Prioritized Replay Buffer (~800KB)                            │
│  ┌─────────────────────────────────────────────────┐          │
│  │ 10,000 transitions × 80 bytes each              │          │
│  │ - state[20]: 80 bytes                           │          │
│  │ - action: 4 bytes                               │          │
│  │ - reward: 4 bytes                               │          │
│  │ - nextState[20]: 80 bytes                       │          │
│  │ - done: 1 byte                                  │          │
│  │ - priority: 4 bytes                             │          │
│  └─────────────────────────────────────────────────┘          │
│                                                                 │
│  Genetic Population (~50KB)                                    │
│  ┌─────────────────────────────────────────────────┐          │
│  │ 20 genomes × 2.5KB each                         │          │
│  │ - actionWeights: 10 × 4 bytes = 40 bytes        │          │
│  │ - traits: 3 × 4 bytes = 12 bytes                │          │
│  │ - fitness tracking: ~2KB                        │          │
│  └─────────────────────────────────────────────────┘          │
│                                                                 │
│  Other Systems (~100KB)                                        │
│  ┌─────────────────────────────────────────────────┐          │
│  │ - Multi-Agent teams: ~5KB per active team       │          │
│  │ - Curriculum counters: ~1KB                     │          │
│  │ - Visual perception profiles: ~100 bytes/player │          │
│  │ - Cache maps (states, actions): ~50KB           │          │
│  └─────────────────────────────────────────────────┘          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Thread Model

```
┌─────────────────────────────────────────────────────────┐
│                   SERVER MAIN THREAD                    │
│  (All game logic, mob ticking, AI decisions)            │
└───────────────────────┬─────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
  ┌─────▼─────┐  ┌──────▼──────┐  ┌────▼────┐
  │ Mob Tick  │  │ Combat Event│  │ Learning│
  │ 20Hz      │  │ Variable    │  │ Async   │
  └─────┬─────┘  └──────┬──────┘  └────┬────┘
        │               │              │
  ┌─────▼─────┐  ┌──────▼──────┐  ┌────▼────┐
  │ Select    │  │ Record      │  │ Training│
  │ Action    │  │ Outcome     │  │ Step    │
  │ ~2ms      │  │ <1ms        │  │ ~50ms   │
  └───────────┘  └─────────────┘  └─────────┘
                                       │
                              ┌────────▼────────┐
                              │  Optional:      │
                              │  Execute async  │
                              │  on fork-join   │
                              │  pool if heavy  │
                              └─────────────────┘
```

**Note**: Currently all ML runs on server thread. Future optimization: offload training to separate thread pool.

---

## Configuration Flow

```
┌────────────────────────────────────────────────────┐
│  mca-ai-enhanced-common.toml                       │
│  (config/mca-ai-enhanced-common.toml)              │
└────────────────┬───────────────────────────────────┘
                 │
                 │ Read at mod init
                 │
      ┌──────────▼──────────┐
      │ GANCityMod.java     │
      │ commonSetup()       │
      └──────────┬──────────┘
                 │
                 │ Initialize systems
                 │
      ┌──────────▼──────────────────────┐
      │ MobBehaviorAI.java              │
      │ initializeAdvancedMLSystems()   │
      └──────────┬──────────────────────┘
                 │
    ┌────────────┼────────────┐
    │            │            │
┌───▼───┐  ┌─────▼─────┐  ┌──▼──┐
│DoubleDQN│ │PriorityReplay MultiAgent
└───┬───┘  └─────┬─────┘  └──┬──┘
    │            │            │
    └────────────┼────────────┘
                 │
          ┌──────▼──────┐
          │  ML Ready   │
          │  (mlEnabled)│
          └─────────────┘
```

---

## Inter-System Communication

```
MobBehaviorAI (Coordinator)
      │
      ├──► DoubleDQN.selectAction(state) → actionIndex
      │
      ├──► Curriculum.filterActions(actions) → allowed[]
      │
      ├──► MultiAgent.getTeam(mobId) → teammateIds[]
      │    │
      │    └──► MultiAgent.shareExperience(id, transition)
      │
      ├──► VisualPerception.analyzePlayer(player) → VisualState
      │    │
      │    └──► VisualPerception.getRecommendedActions(visual) → actions[]
      │
      ├──► GeneticEvolution.selectGenome() → BehaviorGenome
      │    │
      │    └──► GeneticEvolution.recordCombat(genome, outcome)
      │
      └──► PriorityReplay.add(transition, priority)
           │
           ├──► PriorityReplay.sample(batch_size) → batch
           │
           └──► PriorityReplay.updatePriorities(indices, td_errors)
                │
                └──► DoubleDQN.train(batch) → td_errors[]
```

---

## Event Timeline (Single Combat)

```
Time 0s: Combat starts
  └─► Mob.registerGoals() called
      └─► MobAIEnhancementMixin injects AIEnhancedMeleeGoal

Time 0.05s: First tick
  └─► AIEnhancedMeleeGoal.tick()
      ├─► Build MobState
      ├─► VisualPerception.analyzePlayer()
      ├─► GeneticEvolution.selectGenome()
      ├─► Curriculum.filterActions()
      ├─► MultiAgent.formTeam() (if allies nearby)
      ├─► DoubleDQN.selectAction()
      └─► executeAction(selected_action)

Time 0.05s - 5s: Combat continues
  └─► Tick every 50ms (20Hz)
      └─► Same decision loop
          └─► Actions: circle_strafe, hit, retreat, etc.

Time 5s: Combat ends (player or mob dies)
  └─► MobBehaviorAI.recordCombatOutcome()
      ├─► Calculate reward
      ├─► PriorityReplay.add(transition)
      ├─► If buffer > 32:
      │   ├─► PriorityReplay.sample(32)
      │   ├─► DoubleDQN.train(batch)
      │   └─► PriorityReplay.updatePriorities()
      ├─► Curriculum.recordExperience()
      ├─► MultiAgent.shareExperience() (if team)
      └─► GeneticEvolution.recordCombat()

Time 5s + 50 combats: Generation complete
  └─► GeneticEvolution.evolveGeneration()
      ├─► Sort by fitness
      ├─► Keep elite (4)
      ├─► Crossover + mutate (16)
      └─► New generation starts
```

---

## Future Architecture (Planned)

```
                        ┌───────────────────┐
                        │   GPU Acceleration│
                        │   (DJL CUDA)      │
                        └─────────┬─────────┘
                                  │
┌─────────────────────────────────▼─────────────────────────────┐
│                       Enhanced ML Layer                        │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐              │
│  │ Transformer│  │  World     │  │ Hierarchical│              │
│  │ Dialogue   │  │  Model     │  │    RL      │              │
│  │ (GPT-2)    │  │ (Predict)  │  │ (Strategy)  │              │
│  └────────────┘  └────────────┘  └────────────┘              │
│                                                                │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐              │
│  │  Transfer  │  │ Attention  │  │   Model    │              │
│  │  Learning  │  │ Mechanism  │  │Compression │              │
│  │ (Pretrain) │  │ (Focus)    │  │ (Quantize) │              │
│  └────────────┘  └────────────┘  └────────────┘              │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

**Last Updated**: December 2024  
**Mod Version**: 1.0.0+  
**Minecraft**: 1.20.1 Forge 47.2.0
