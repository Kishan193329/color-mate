# ColorForge — Flutter app (colour-fill game)

A mobile colour-by-number game: pick a colour, tap its numbered regions — each paints its flat
colour then **morphs into the real photo pixels**. Selecting a colour highlights only its
(same-colour) regions. Completing every region paints the **original photograph**.

It's driven entirely by a **bundle** exported from the ColorForge backend
(`GET /api/job/<id>/color/export`) — no server needed at runtime.

## Run it (a demo image is already bundled)

```bash
cd flutter_app
flutter pub get
flutter run          # on a connected phone or emulator
```

That's it — the app opens the colouring page, a palette of glossy swatches at the bottom; tap a
colour then tap regions.

## Use YOUR image

1. In the backend Colour Studio (`/api/admin/studio`) or via `GET /api/job/<id>/color/export`,
   download the bundle zip for your image.
2. Unzip it into `assets/bundle/` so these sit **directly** inside that folder:
   ```
   assets/bundle/original.png
   assets/bundle/id_map.png
   assets/bundle/border.png
   assets/bundle/regions.json
   assets/bundle/swatches/1.png … N.png
   ```
   (replace the demo files that are already there)
3. `flutter pub get` (picks up any new swatch assets) then `flutter run`.

## How it works (so you can tweak it)

- `original.png` — the real photo, the fill/reveal source.
- `id_map.png` — region id per pixel, **id = Red + Green·256** (Blue unused). Used to (a) find which
  region a tap hit and (b) mask a region's pixels when filling.
- `border.png` — the clean line-art outline, drawn on top.
- `regions.json` — `{ w, h, regions:[{id,number,cx,cy,r,bbox}], palette:[{number,color,swatch}] }`.
  Same `number` = same colour; `color` is the flat paint colour; `cx,cy` is the number position.
- `swatches/<n>.png` — the glossy real-photo swatch shown as palette button `n`.

All the game logic is in `lib/main.dart` (~1 file):
- loads `original` + `id_map` as RGBA pixel buffers,
- keeps a white `display` buffer, tap → read the id from `id_map` → paint the region's flat colour
  then animate each pixel from flat → `original` (the "paint becomes real" morph),
- draws `border` + numbers on top, palette bar at the bottom with select/auto-advance.

## Notes
- Tested layout works portrait; the canvas scales to fit and taps map back to image pixels.
- Big images (~1254²) load ~3 buffers (~19 MB) — fine on modern phones. If you want it lighter,
  export a smaller `original`/`id_map` from the backend.
