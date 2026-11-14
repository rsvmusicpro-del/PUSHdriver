# Push 1 / Bitwig 64-color palette

PUSHdriver exposes 64 color choices in the Color view. Each entry uses the exact
RGB triplet from the first 64 entries of DrivenByMoss 19.2.2
`PushColorManager.DEFAULT_PALETTE` and is registered to the same Push 1 pad
palette index. Bitwig stores the matching RGB value on the track, clip or layer;
the Push 1 displays the corresponding hardware palette entry.

This is a PUSHdriver palette derived from the Push 1 hardware palette, not a
claim that Bitwig's built-in factory palette contains these exact 64 entries.

| Pad index | Name | RGB | Hex |
|---:|---|---|---|
| 0 | Black | 0, 0, 0 | #000000 |
| 1 | Charcoal | 30, 30, 30 | #1E1E1E |
| 2 | Gray | 127, 127, 127 | #7F7F7F |
| 3 | White | 255, 255, 255 | #FFFFFF |
| 4 | Coral | 255, 76, 76 | #FF4C4C |
| 5 | Red | 255, 0, 0 | #FF0000 |
| 6 | Dark Red | 89, 0, 0 | #590000 |
| 7 | Deep Red | 25, 0, 0 | #190000 |
| 8 | Peach | 255, 189, 108 | #FFBD6C |
| 9 | Orange | 255, 84, 0 | #FF5400 |
| 10 | Brown | 89, 29, 0 | #591D00 |
| 11 | Dark Brown | 39, 27, 0 | #271B00 |
| 12 | Lemon | 255, 255, 76 | #FFFF4C |
| 13 | Yellow | 255, 255, 0 | #FFFF00 |
| 14 | Olive | 89, 89, 0 | #595900 |
| 15 | Dark Olive | 25, 25, 0 | #191900 |
| 16 | Chartreuse | 136, 255, 76 | #88FF4C |
| 17 | Lime | 84, 255, 0 | #54FF00 |
| 18 | Forest Green | 29, 89, 0 | #1D5900 |
| 19 | Deep Forest | 20, 43, 0 | #142B00 |
| 20 | Bright Green | 76, 255, 76 | #4CFF4C |
| 21 | Green | 0, 255, 0 | #00FF00 |
| 22 | Dark Green | 0, 89, 0 | #005900 |
| 23 | Deep Green | 0, 25, 0 | #001900 |
| 24 | Mint | 76, 255, 94 | #4CFF5E |
| 25 | Neon Green | 0, 255, 25 | #00FF19 |
| 26 | Deep Moss | 0, 89, 13 | #00590D |
| 27 | Night Green | 0, 25, 2 | #001902 |
| 28 | Mint Green | 76, 255, 136 | #4CFF88 |
| 29 | Spring Green | 0, 255, 85 | #00FF55 |
| 30 | Dark Teal | 0, 89, 29 | #00591D |
| 31 | Deep Teal | 0, 31, 18 | #001F12 |
| 32 | Aqua Mint | 76, 255, 183 | #4CFFB7 |
| 33 | Aqua | 0, 255, 153 | #00FF99 |
| 34 | Deep Aqua | 0, 89, 53 | #005935 |
| 35 | Night Teal | 0, 25, 18 | #001912 |
| 36 | Sky Blue | 76, 195, 255 | #4CC3FF |
| 37 | Azure | 0, 169, 255 | #00A9FF |
| 38 | Deep Teal Blue | 0, 65, 82 | #004152 |
| 39 | Midnight Blue | 0, 16, 25 | #001019 |
| 40 | Cornflower | 76, 136, 255 | #4C88FF |
| 41 | Blue | 0, 85, 255 | #0055FF |
| 42 | Navy | 0, 29, 89 | #001D59 |
| 43 | Midnight Navy | 0, 8, 25 | #000819 |
| 44 | Periwinkle | 76, 76, 255 | #4C4CFF |
| 45 | Royal Blue | 0, 0, 255 | #0000FF |
| 46 | Deep Blue | 0, 0, 89 | #000059 |
| 47 | Dark Navy | 0, 0, 25 | #000019 |
| 48 | Violet | 135, 76, 255 | #874CFF |
| 49 | Purple | 84, 0, 255 | #5400FF |
| 50 | Deep Purple | 25, 0, 100 | #190064 |
| 51 | Dark Purple | 15, 0, 48 | #0F0030 |
| 52 | Magenta | 255, 76, 255 | #FF4CFF |
| 53 | Fuchsia | 255, 0, 255 | #FF00FF |
| 54 | Deep Magenta | 89, 0, 89 | #590059 |
| 55 | Dark Magenta | 25, 0, 25 | #190019 |
| 56 | Hot Pink | 255, 76, 135 | #FF4C87 |
| 57 | Pink | 255, 0, 84 | #FF0054 |
| 58 | Deep Pink | 89, 0, 29 | #59001D |
| 59 | Wine | 34, 0, 19 | #220013 |
| 60 | Vermilion | 255, 21, 0 | #FF1500 |
| 61 | Burnt Orange | 153, 53, 0 | #993500 |
| 62 | Ochre | 121, 81, 0 | #795100 |
| 63 | Olive Green | 67, 100, 0 | #436400 |

The 64 values are distinct in the source palette. The color view uses all 64
Push pads in one page. The menu applies the selected RGB through the existing
Bitwig track, clip and layer color APIs.
