# Match-3 Game with Meta-Progression & Tower Defense

A native Android match-3 puzzle game with multiple game modes, persistent economy, and tower defense mechanics.

## Features

### Core Match-3 Mechanics
- **4 Base Colors**: 🟥 🟦 🟩 🟨
- **Gravity-based** block falling
- **Deterministic gameplay** with seeded RNG

### Special Blocks
| Pattern | Creates | Effect |
|---------|---------|--------|
| 4 in a row | 🚀 Rocket | Clears entire row/column |
| 5 in a row | 🪩 Disco Ball | Clears one color from board |
| 2×2 Square | 🌀 Propeller | Flies to target, destroys cross |
| T-Shape | 💣 Bomb | 3×3 explosion |

### Special Combinations
- **🚀+🚀**: Cross pattern (row + column)
- **🚀+💣**: 3-wide cross
- **🚀+🌀**: Propeller carries rocket
- **🚀+🪩**: All cleared blocks become rockets
- **💣+💣**: 5×5 explosion
- **💣+🌀**: Propeller carries bomb
- **💣+🪩**: All cleared blocks become bombs
- **🌀+🪩**: All cleared blocks become propellers
- **🪩+🪩**: Clears entire board

### Game Modes

#### Mode 1: Score Accumulation 🎯
- Reach target score within turn limit
- Score multiplier increases every 10 points
- Excess score converts to wallet currency

#### Mode 2: Clear Special Cells ❄️
- Remove all obstacles within turn limit
- **Frozen Cells** (❄️): 1-4 durability
- **Frozen Zones**: Shared counter threshold
- **Boxes** (📦): Creates frozen cells when destroyed
- **Color Boxes** (🎨): Only matching color damages

#### Mode 3: Tower Defense ⚔️
- **Player Field**: 8×9 board
- **Enemy Field**: 4×9 grid
- **Gates**: Protect from enemies
- Each destroyed block fires a projectile upward
- Enemy types: Basic 👾, Controller 🤖, Spawner 🥚, Boss 👹

### Meta-Progression

#### Wallet 💰
- Earned from excess score and remaining turns
- Persistent across levels

#### Gate System 🚪
- Materials: Wood → Stone → Iron → Steel → Diamond
- Damage is permanent until repaired
- Upgrades increase max durability

#### Perks
- 🚀 Extra Rocket
- 💣 Extra Bomb
- 🌀 Extra Propeller
- 🪩 Extra Disco Ball
- ⚔️ Double Damage
- 🛡️ Shield
- 🍀 Lucky Spawns
- ✨ Score Boost

## Project Structure

```
app/src/main/java/com/match3/game/
├── domain/
│   ├── model/          # Data classes (Block, Cell, Position, etc.)
│   ├── engine/         # Game logic (Board, MatchFinder, GameEngine)
│   └── progression/    # Meta-progression (PlayerProgress, LevelGenerator)
├── data/
│   └── GameRepository  # Persistence layer
└── ui/
    ├── views/          # Custom views (BoardView, EnemyFieldView)
    ├── viewmodel/      # ViewModels for each screen
    ├── adapter/        # RecyclerView adapters
    └── Activities      # Main, Game, TowerDefense
```

## Building

```bash
./gradlew assembleDebug
```

## Architecture

- **Clean Architecture**: Separation of domain, data, and UI layers
- **MVVM**: ViewModels with LiveData for UI state
- **Deterministic**: All gameplay reproducible by seed
- **Native Android**: No web wrappers, pure Kotlin

## Level Structure

- 20 levels per block
- Mix of Score, Clear, and Tower Defense modes
- Tower Defense every 5th level
- Boss every 20th level
- Difficulty scales with block index

## Visual Style

- Dark theme with vibrant accents
- Emoji placeholders for all game elements
- Smooth animations for:
  - Block swapping
  - Falling blocks
  - Explosions
  - Score popups
  - Projectiles
