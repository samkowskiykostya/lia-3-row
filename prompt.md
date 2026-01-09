
Create a **native Android game** (no Expo, no web wrappers) using a clean architecture that separates **game logic**, **rendering**, and **UI state**.  
The game is a **match-3 puzzle platform with multiple modes**, persistent economy, meta-progression, and a tower-defense confrontation system.
The game must feel **bright, juicy, playful, and “sexy”**, using:
-   emoji placeholders (🟦 🔥 🚀 💣 🌀 ✨ ❄️ 💎 etc.) that will later be replaced by art
    
-   simple but satisfying animations:
    
    -   block swap
        
    -   falling
        
    -   explosions
        
    -   flying projectiles
        
    -   score popups
        
-   no time limits, only turn-based logic
    
All gameplay must be **deterministic**, reproducible by seed.
----------
## Board and Core Rules (Global)
-   Board size varies by mode:
    
    -   Standard modes: **9×12**
        
    -   Tower Defense mode: **Player field 8×9 + Enemy field 4×9**
        
-   Gravity always pulls blocks downward.
    
-   Colors: **4 base colors** (🟥 🟦 🟩 🟨).
    
-   New blocks spawn from the top using a seeded RNG.
    
-   After every action, the board resolves fully until **no matches remain**.
    
----------
## Match Rules (Base Mechanics)
### 3 in a Row (Base Match)
-   Three or more identical colors in a straight line.
    
-   All matched blocks disappear.
    
-   Each removed block:
    
    -   gives **1 point**
        
    -   damages adjacent special cells
        
-   Blocks above fall down.
    
-   If new matches form after falling:
    
    -   they are resolved
        
    -   **new specials may be created**
        
    -   but no extra explosions occur automatically beyond defined rules
        
----------
## Special Blocks Creation
### 4 in a Row → Rocket 🚀
-   Horizontal 4 → Horizontal Rocket
    
-   Vertical 4 → Vertical Rocket
    
-   Rocket color = match color
    
-   Activation:
    
    -   Tap → fires immediately
        
    -   Swipe → moves one cell, then fires from new position
        
-   Effect:
    
    -   Horizontal Rocket clears entire row
        
    -   Vertical Rocket clears entire column
        
----------
### 5 in a Row → Disco Ball 🪩
-   Clears **one entire color** from the board
    
-   Activation:
    
    -   Tap → clears a **random color**
        
    -   Swipe with a neighboring block → clears that block’s color
        
----------
### 2×2 Square + any adjustant cells of same color → Free move
----------
### T-Shape → Bomb 💣
-   Explodes in a **3×3 area** centered on activation cell
    
----------
## Special Block Combinations (Critical – Must Be Exact)
### Rocket + Rocket 🚀🚀
-   Clears both:
    
    -   the row
        
    -   and the column
        
-   Forms a cross at the activation point
    
----------
### Rocket + Bomb 🚀💣
-   Clears:
    
    -   **3 rows**
        
    -   **3 columns**
        
-   Thick cross (width = 3)
    
----------
### Rocket + Disco 🚀🪩
-   All blocks cleared by the disco:
    
    -   transform into rockets (random orientation)
        
    -   then fire sequentially
        
----------
### Bomb + Bomb 💣💣
-   Explodes in a **5×5 area**
    
----------
### Bomb + Disco 💣🪩
-   All cleared blocks turn into bombs
    
-   Bombs explode sequentially
    
----------
### Disco + Disco 🪩🪩
-   Clears **the entire board**