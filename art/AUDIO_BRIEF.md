# TerraFill Audio Brief

Rules for every file:
- Preferred format: OGG (Vorbis). WAV/MP3 also fine - will be converted on integration.
- 44.1 kHz. Keep peaks below clipping; all SFX should feel like one loudness family.
- MUSIC must be a SEAMLESS LOOP: no fade-in/fade-out, no silence at start or end,
  the last beat must flow straight back into the first.
- SFX must be tightly trimmed: zero silence before the sound starts.
- No voices/vocals anywhere.
- Style anchor for every prompt: "playful tribal jungle arcade" - wooden percussion,
  marimba/kalimba, organic textures. Never orchestral, never electronic-dubstep.

| # | File | Length | Loop | What it is |
|---|------|--------|------|------------|
| 1 | music_menu.ogg | 60-90s | YES | Main menu ambience |
| 2 | music_game.ogg | 60-90s | YES | In-game action loop |
| 3 | sfx_capture.ogg | 0.5-1s | no | Territory claimed |
| 4 | sfx_crash.ogg | 0.6-1s | no | Spider caught you |
| 5 | sfx_powerup.ogg | ~0.5s | no | Power-up picked up |
| 6 | sfx_level_complete.ogg | 2-3s | no | Victory jingle |
| 7 | sfx_game_over.ogg | 2-3s | no | Defeat jingle |
| 8 | sfx_tap.ogg | 0.1-0.2s | no | Button/steer tick |
| 9 | sfx_countdown.ogg | ~0.2s | no | 3-2-1 tick |
| 10 | sfx_go.ogg | ~0.4s | no | "GO!" at level start |
| + | sfx_star.ogg | ~0.3s | no | Star pop on results (bonus) |

## Prompts

1. **music_menu.ogg** - Calm mysterious jungle ambience for a game menu: soft marimba
   melody wandering slowly, distant tropical bird calls, gentle shaker and soft hand
   drum pulse, warm low pad like evening air, relaxed tempo around 80 BPM, inviting
   and slightly magical, seamless loop, no vocals.

2. **music_game.ogg** - Energetic playful tribal arcade loop: driving congas and
   djembe groove, staccato marimba and kalimba riff that repeats with small
   variations, wood blocks and shakers keeping urgency, around 125 BPM, adventurous
   chase feeling but fun not scary, seamless loop, no vocals.

3. **sfx_capture.ogg** - Bright rewarding success chime: quick ascending marimba
   arpeggio of three notes with a soft rustle of leaves underneath, short and
   satisfying.

4. **sfx_crash.ogg** - Cartoon impact of getting caught: a low wooden thud, a fast
   descending two-note slide, and a brief rattle like a shaken seed pod, dramatic
   but playful, not gory.

5. **sfx_powerup.ogg** - Sparkly magical pickup: fast glassy ascending glissando
   with a tiny bell ping at the top.

6. **sfx_level_complete.ogg** - Triumphant mini-fanfare: tribal drum roll bursting
   into a happy four-note marimba victory melody with a shimmering shaker tail,
   ends cleanly.

7. **sfx_game_over.ogg** - Gentle defeat: slow descending three-note marimba motif
   over a fading low drum, melancholic but warm, invites retry rather than punishes.

8. **sfx_tap.ogg** - Single soft bamboo knock, very short, pleasant, like a wooden
   button.

9. **sfx_countdown.ogg** - One crisp wood-block tick, dry and short.

10. **sfx_go.ogg** - Short rising marimba flourish with a bright accent hit, like a
    starting whistle made of wood.

Bonus. **sfx_star.ogg** - Tiny ascending two-note chime with sparkle, like a star
    popping into place.

Suggested tools: Suno / Udio / Stable Audio for the two music loops; ElevenLabs
sound-effects generator (or similar) for the SFX. Generate SFX a few times and pick
the tightest take. Attach files in chat with their names and they will be integrated
(SoundManager will be rebuilt around real audio assets, keeping the procedural
synth as fallback).
