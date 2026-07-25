# TerraFill Enemy Art Brief

The game currently ships **three** spider drawables (`sprite_spider_red`,
`sprite_spider_blue`, `sprite_spider` = green) and re-colours them for the other
eight enemy types. Re-colouring is done with a flat `SrcAtop` tint, which throws
away every bit of shading — those enemies render as solid-colour silhouettes.

This brief lists one prompt per enemy so each ability eventually gets real art.
Drop finished PNGs into `app/src/main/res/drawable-nodpi/` using the filename in
the table; the renderer picks them up by name.

## Rules for every image

- Generate at the largest size the tool allows; the ratio matters more than the pixels.
- Transparent background if supported, otherwise plain flat **WHITE** (it gets cut out).
- The whole creature must be inside the frame — no cropped legs.
- **Facing LEFT** (the engine mirrors the sprite when the enemy moves right).
- No text, no watermarks, no logos, no drop shadow baked into the image.
- All enemies must read as siblings of the three spiders already in the game:
  same cartoon tarantula construction, same outline weight, same high
  three-quarter camera angle.

Paste this shared style line at the start of every prompt:

> Vibrant 2D mobile game art, cel-shaded with crisp clean outlines, rich saturated
> colour, polished professional game asset, high detail, high three-quarter view,
> isolated character on plain white background, no text, no watermark.

## Files

| # | File | Size / ratio | Enemy | Debuts |
|---|------|--------------|-------|--------|
| 1 | sprite_spider_hunter.png | ~800x550 | Hunter | L5 |
| 2 | sprite_spider_speeder.png | ~900x500 | Speeder | L7 |
| 3 | sprite_spider_eater.png | ~800x600 | Eater | L4 |
| 4 | sprite_spider_spitter.png | ~800x600 | Spitter | L15 |
| 5 | sprite_weaver.png | ~800x600 | Weaver | L18 |
| 6 | sprite_hornet.png | ~900x520 | Hornet | L21 |
| 7 | sprite_phantom.png | ~800x600 | Phantom | L24 |
| 8 | sprite_broodmother.png | ~900x700 | Broodmother | L27 |
| 9 | sprite_spiderling.png | ~600x450 | Spiderling | with Broodmother |

## Prompts

1. **sprite_spider_hunter.png** — Hunter (chases the player relentlessly).
   A lean, athletic cartoon tarantula built for the chase, facing LEFT, deep
   crimson-red body with darker blood-red stripe markings across the abdomen,
   long powerful sprinting legs angled forward as if mid-stride, head low and
   aggressive, two large glowing amber eyes locked forward with a predatory
   stare, sharp visible fangs, a faint red rim-light along the back edge.

2. **sprite_spider_speeder.png** — Speeder (crosses the whole field in a blink).
   A streamlined cartoon racing spider, facing LEFT, bright golden-yellow and
   orange body with a smooth aerodynamic teardrop abdomen, thin whip-like legs
   swept backwards as if blasting forward at high speed, small determined eyes
   narrowed against the wind, subtle motion-blur streaks trailing off the back
   legs, low compact silhouette that reads instantly as "fast".

3. **sprite_spider_eater.png** — Eater (slowly devours captured land).
   A heavy, bloated cartoon tarantula, facing LEFT, deep violet-purple body with
   a fat sagging abdomen and thick stubby legs, oversized jaws and huge
   mandibles wide open showing chunky grinding teeth, a few crumbs and soil
   fragments falling from its mouth, small dull greedy eyes, slow lumbering
   posture, clearly the strongest and least agile of the family.

4. **sprite_spider_spitter.png** — Spitter (stands still, fires web projectiles).
   A cartoon tarantula planted in a wide braced stance, facing LEFT, toxic
   acid-green body with pale yellow-green venom markings, front legs raised and
   locked in a firing pose, abdomen swollen and glowing faintly with venom,
   mouth open with a glistening bead of white silk about to launch, four sharp
   glowing green eyes, a coiled spring-loaded look of "about to shoot".

5. **sprite_weaver.png** — Weaver (spins sticky traps on open ground).
   A delicate long-legged cartoon orb-weaver spider, facing LEFT, pale
   silver-white and soft grey body with fine pearlescent sheen, extremely long
   thin elegant legs, spinnerets at the rear actively trailing glossy white silk
   strands that curl beneath it, a small partly-spun spiral web hanging under
   the body, calm patient expression with many small dark eyes, ghostly-pale
   palette clearly distinct from the colourful spiders.

6. **sprite_hornet.png** — Hornet (fast flier that ignores walls).
   NOT a spider: a menacing cartoon hornet wasp seen from a high three-quarter
   angle, facing LEFT, glossy amber-orange and black striped segmented body,
   narrow wasp waist, translucent blurred wings beating fast on both sides, long
   sharp stinger curled forward beneath the abdomen, angular angry eyes, six thin
   legs tucked up in flight, aggressive dive-attack posture, unmistakably an
   airborne enemy rather than a ground crawler.

7. **sprite_phantom.png** — Phantom (drifts through walls, reaches you anywhere).
   A ghostly translucent cartoon spider, facing LEFT, pale ice-blue and white
   body that fades to semi-transparent wispy vapour at the tips of its legs and
   the end of its abdomen, soft glowing cyan aura, hollow empty white eye-glow
   instead of pupils, drifting weightless pose with legs trailing loosely
   downward, ethereal misty edges, no hard bottom outline — it should look like
   it is floating rather than standing.

8. **sprite_broodmother.png** — Broodmother (queen that hatches spiderlings).
   A massive regal cartoon spider queen, facing LEFT, dark royal purple and
   magenta body with a huge swollen glowing egg sac attached under the rear of
   the abdomen, faint pink light shining through the sac with tiny dark
   silhouettes of babies curled inside, thick armoured legs with bristled joints,
   a small crown-like ridge of spines on the head, cold intelligent glowing eyes,
   imposing boss-scale presence — clearly the biggest creature in the game.

9. **sprite_spiderling.png** — Spiderling (fast baby hatched by the Broodmother).
   A tiny cartoon baby spider, facing LEFT, pale lilac and light pink body,
   oversized round head and abdomen with short stubby legs relative to the body,
   big shiny black eyes, cute but manic scurrying pose, simple readable
   silhouette designed to stay legible when drawn very small on screen —
   obviously the offspring of the purple Broodmother.
