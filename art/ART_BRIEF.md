# TerraFill Art Brief

Rules for every image:
- Generate at the LARGEST size the tool allows; aspect ratio matters more than exact pixels.
- Characters: transparent background if the tool supports it; otherwise plain flat WHITE background (will be cut out).
- Never include text, watermarks, or logos.
- Paste this shared style line at the start of every prompt:

> Vibrant 2D mobile game art, cel-shaded with crisp clean outlines, rich saturated
> jungle color palette of deep greens and warm sunlight, polished professional game
> asset, high detail, no text, no watermark.

| # | File | Size / ratio | Purpose |
|---|------|--------------|---------|
| 1 | icon.png | 1024x1024 | App icon (TOP PRIORITY) |
| 2 | menu_hero.png | 1080x720 (3:2) | Animated menu scene replacement |
| 3 | bg_menu.png | 1080x2340 (9:19.5) | Main menu background |
| 4 | bg_game.png | 1080x2340 (9:19.5) | In-game board background (optional, must be low-contrast) |
| 5 | sprite_caterpillar.png | ~1200x450 side view, FACING LEFT | Player character |
| 6 | spider_red.png | ~800x550, FACING LEFT | Basic enemy |
| 7 | spider_blue.png | ~800x550, FACING LEFT | Smart enemy |
| 8 | spider_green.png | ~800x550, FACING LEFT | Jumper enemy |
| + | feature.png | 1024x500 EXACT | Google Play feature graphic (bonus) |

## Prompts

1. **icon.png** — Close-up portrait of a cute heroic green caterpillar's head and face,
   big expressive friendly eyes, confident smile, tiny antennae, centered on a lush
   leaf-green background with subtle jungle leaves in the corners, bold simple
   silhouette readable at tiny size, mobile app icon composition, extreme close crop.

2. **menu_hero.png** — A cute heroic green caterpillar sitting on a giant curved jungle
   leaf in the foreground, spitting a thin white silk web strand upward from its mouth,
   while three menacing cartoon tarantula spiders (one red, one blue, one green) hang
   from white silk threads descending from the jungle canopy above, dense tropical
   rainforest background with sunbeams breaking through, dramatic but playful mood,
   wide cinematic composition with empty space in the center-left for UI buttons.

3. **bg_menu.png** — Tall portrait view looking into a dense tropical jungle, layers of
   huge leaves, hanging vines and thick tree trunks framing the edges, misty depth, the
   center-middle area darker and less detailed so interface text remains readable over
   it, deep green tones with a few warm golden light shafts, atmospheric.

4. **bg_game.png** — Top-down view of a jungle forest floor, moss, scattered leaves,
   roots and dark soil forming a subtle natural texture, muted dark green tones, low
   contrast and no strong focal point anywhere (pure ambient texture for a game board
   to sit on top of), even lighting.

5. **sprite_caterpillar.png** — Full body side view of a cute heroic green caterpillar
   facing LEFT, segmented glossy body with lighter belly, big friendly eyes, tiny legs
   visible, slight determined smile, standing pose, isolated character on plain white
   background, full character visible with nothing cropped.

6-8. **spider_red / spider_blue / spider_green.png** — Full body view of a menacing
   cartoon tarantula spider seen from a high three-quarter angle, hairy legs spread
   wide, fangs visible, glowing eyes, facing LEFT, [DEEP RED / ELECTRIC BLUE / TOXIC
   GREEN] coloring with darker markings, isolated on plain white background, full body
   visible with all eight legs inside the frame.
   (Same prompt for all three, change only the color - they must look like siblings.)

Bonus. **feature.png** — Wide banner: the green caterpillar hero on a leaf on the left
   side facing right, three colorful tarantulas charging from the right, jungle
   background with dramatic sunbeams, energetic action composition, empty margin at
   top for the game logo. Exactly 1024x500 for Google Play.
