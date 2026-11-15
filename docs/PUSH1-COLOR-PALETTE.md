# Push 1 / Bitwig 64-color palette

PUSHdriver exposes exactly 64 color choices in the existing Color view. The framework's internal `COLOR_OFF` fallback remains separate and is not displayed as a menu color. The 64 menu slots map one-to-one to 64 distinct RGB values from the pinned Push 1 hardware `DEFAULT_PALETTE`; each slot registers the matching physical LED index. The palette is arranged as two consecutive families: 32 high-saturation flash colors, then 32 vivid colors with a slightly softer visual impact.

## Hardware constraints

- No synthetic RGB values: every displayed RGB triplet is copied from the Push 1 firmware palette.
- Exactly 64 distinct RGB values and 64 distinct hardware LED indices.
- The maximum RGB channel is at least 160/255, avoiding low-output hardware colors.
- HSV-style saturation `(max(R,G,B)-min(R,G,B))/max(R,G,B)` is at least 0.45, excluding neutral and washed-out pastel colors.
- The internal `COLOR_OFF` fallback remains intact and is excluded from the visible menu; the menu still contains exactly 64 choices.
- Track/clip/layer color assignment paths are unchanged.

## Track selector feedback

The first button row in Track, Clip, Volume, Pan and Send modes identifies tracks.
For an existing, active, non-selected track that is not record-armed, its LED
uses the closest RGB match from the eight stable, non-blinking colors in the
Push 1 first-row palette (hardware indices 1, 4, 7, 10, 13, 16, 19 and 22).
The 64-color Bitwig palette and the 128-entry pad RGB palette are not valid
sources for ROW1 button output. A selected Push 1 track uses the supported
orange-high first-row LED: ROW1 has no neutral white/grey state. A record-armed
track remains red (red-high when selected, red-low otherwise) with higher
priority than selection and track RGB. Push 2 retains its existing orange
selected-track and yellow ordinary-track feedback.

The output threshold describes the Push 1's fixed LED drive values, not a promise that every hue has identical perceived luminance. Blue and red LEDs are perceived differently by the human eye even at comparable channel values.

## Palette

| Slot | Family | Name | RGB | Hex | Hardware LED index |
|---:|---|---|---|---|---:|
| 0 | Flash | Signal Red | 255, 0, 0 | #FF0000 | 72 |
| 1 | Flash | Deep Red | 160, 0, 0 | #A00000 | 120 |
| 2 | Flash | Scarlet | 255, 21, 0 | #FF1500 | 60 |
| 3 | Flash | Flame Orange | 255, 74, 0 | #FF4A00 | 84 |
| 4 | Flash | Electric Orange | 255, 84, 0 | #FF5400 | 9 |
| 5 | Flash | Tangerine | 255, 127, 0 | #FF7F00 | 96 |
| 6 | Flash | Burnt Amber | 179, 95, 0 | #B35F00 | 126 |
| 7 | Flash | Golden Olive | 185, 176, 0 | #B9B000 | 124 |
| 8 | Flash | Electric Yellow | 255, 255, 0 | #FFFF00 | 13 |
| 9 | Flash | Acid Yellow | 175, 237, 6 | #AFED06 | 74 |
| 10 | Flash | Neon Chartreuse | 136, 225, 6 | #88E106 | 85 |
| 11 | Flash | Electric Lime | 144, 255, 0 | #90FF00 | 98 |
| 12 | Flash | Laser Lime | 100, 255, 9 | #64FF09 | 75 |
| 13 | Flash | Lime | 84, 255, 0 | #54FF00 | 17 |
| 14 | Flash | Vivid Green | 26, 208, 0 | #1AD000 | 122 |
| 15 | Flash | Electric Green | 0, 255, 0 | #00FF00 | 87 |
| 16 | Flash | Neon Green | 0, 255, 25 | #00FF19 | 25 |
| 17 | Flash | Spring Green | 0, 255, 85 | #00FF55 | 29 |
| 18 | Flash | Bright Mint | 0, 255, 135 | #00FF87 | 77 |
| 19 | Flash | Emerald | 0, 255, 153 | #00FF99 | 33 |
| 20 | Flash | Azure | 0, 169, 255 | #00A9FF | 78 |
| 21 | Flash | Electric Blue | 0, 85, 255 | #0055FF | 41 |
| 22 | Flash | Cobalt Blue | 0, 42, 255 | #002AFF | 79 |
| 23 | Flash | Pure Blue | 0, 0, 255 | #0000FF | 67 |
| 24 | Flash | Indigo | 37, 0, 204 | #2500CC | 69 |
| 25 | Flash | Electric Indigo | 63, 0, 255 | #3F00FF | 80 |
| 26 | Flash | Ultramarine | 84, 0, 255 | #5400FF | 49 |
| 27 | Flash | Violet | 122, 0, 255 | #7A00FF | 81 |
| 28 | Flash | Electric Magenta | 255, 0, 255 | #FF00FF | 53 |
| 29 | Flash | Hot Pink | 255, 0, 93 | #FF005D | 95 |
| 30 | Flash | Fuchsia | 255, 0, 84 | #FF0054 | 57 |
| 31 | Flash | Crimson | 168, 0, 10 | #A8000A | 106 |
| 32 | Vive | Coral | 255, 76, 76 | #FF4C4C | 4 |
| 33 | Vive | Vermilion | 222, 81, 61 | #DE513D | 107 |
| 34 | Vive | Copper Orange | 216, 106, 28 | #D86A1C | 108 |
| 35 | Vive | Apricot Orange | 255, 189, 108 | #FFBD6C | 8 |
| 36 | Vive | Golden Yellow | 255, 225, 38 | #FFE126 | 109 |
| 37 | Vive | Lemon | 255, 255, 76 | #FFFF4C | 12 |
| 38 | Vive | Lime Yellow | 220, 255, 107 | #DCFF6B | 113 |
| 39 | Vive | Chartreuse | 189, 255, 45 | #BDFF2D | 73 |
| 40 | Vive | Citrus Green | 158, 225, 47 | #9EE12F | 110 |
| 41 | Vive | Leaf Green | 103, 181, 15 | #67B50F | 111 |
| 42 | Vive | Neon Lime | 114, 255, 21 | #72FF15 | 86 |
| 43 | Vive | Bright Chartreuse | 136, 255, 76 | #88FF4C | 16 |
| 44 | Vive | Electric Green Light | 59, 255, 38 | #3BFF26 | 88 |
| 45 | Vive | Bright Green | 76, 255, 76 | #4CFF4C | 20 |
| 46 | Vive | Spring Green Light | 76, 255, 94 | #4CFF5E | 24 |
| 47 | Vive | Fresh Green | 89, 255, 113 | #59FF71 | 89 |
| 48 | Vive | Mint Green | 76, 255, 136 | #4CFF88 | 28 |
| 49 | Vive | Aqua Mint | 128, 255, 189 | #80FFBD | 114 |
| 50 | Vive | Sea Green | 76, 255, 183 | #4CFFB7 | 32 |
| 51 | Vive | Turquoise | 56, 255, 204 | #38FFCC | 90 |
| 52 | Vive | Sky Blue | 76, 195, 255 | #4CC3FF | 36 |
| 53 | Vive | Cornflower Blue | 76, 136, 255 | #4C88FF | 40 |
| 54 | Vive | Azure Blue | 91, 138, 255 | #5B8AFF | 91 |
| 55 | Vive | Royal Blue | 49, 81, 198 | #3151C6 | 92 |
| 56 | Vive | Periwinkle Blue | 76, 76, 255 | #4C4CFF | 44 |
| 57 | Vive | Periwinkle | 135, 127, 233 | #877FE9 | 93 |
| 58 | Vive | Lavender Violet | 142, 102, 255 | #8E66FF | 116 |
| 59 | Vive | Purple | 135, 76, 255 | #874CFF | 48 |
| 60 | Vive | Orchid | 211, 29, 255 | #D31DFF | 94 |
| 61 | Vive | Magenta Pink | 255, 76, 255 | #FF4CFF | 52 |
| 62 | Vive | Berry | 178, 26, 125 | #B21A7D | 82 |
| 63 | Vive | Rose Pink | 255, 76, 135 | #FF4C87 | 56 |

The menu remains a single 64-color page. Selecting a color continues to apply the corresponding exact RGB value through the existing Bitwig track, clip and layer color APIs.
