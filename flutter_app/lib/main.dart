// ColorForge — colour-fill game (mobile).
//
// Reads the exported bundle from assets/bundle/:
//   original.png   — the real photo (fill/reveal source)
//   id_map.png     — region id per pixel, id = Red + Green*256  (for tap + masking)
//   border.png     — the clean line-art outline (drawn on top)
//   regions.json   — { w, h, regions:[{id,number,cx,cy,r,bbox}], palette:[{number,color,swatch}] }
//   swatches/<n>.png — the glossy real-photo swatch per palette number
//
// Game: pick a colour, tap its numbered regions. Each region paints its flat colour, then morphs
// (~0.3 s) into the ORIGINAL photo pixels. Selecting a colour highlights only its (same-colour)
// regions. Completing every region paints the real photograph.
import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;

void main() => runApp(const ColorForgeApp());

class ColorForgeApp extends StatelessWidget {
  const ColorForgeApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'ColorForge',
        debugShowCheckedModeBanner: false,
        theme: ThemeData.dark(useMaterial3: true).copyWith(
          scaffoldBackgroundColor: const Color(0xFF0F1216),
        ),
        home: const GameScreen(),
      );
}

class Region {
  final int id, number, cx, cy;
  final double r;
  final List<int> bbox; // x, y, w, h
  Region(this.id, this.number, this.cx, this.cy, this.r, this.bbox);
}

class Swatch {
  final int number;
  final Color color;
  final String asset; // AssetImage path
  Swatch(this.number, this.color, this.asset);
}

class _Fill {
  final List<int> idx; // byte offsets into the RGBA display buffer
  final Color color;
  _Fill(this.idx, this.color);
}

class GameScreen extends StatefulWidget {
  const GameScreen({super.key});
  @override
  State<GameScreen> createState() => _GameScreenState();
}

class _GameScreenState extends State<GameScreen> with SingleTickerProviderStateMixin {
  static const String bundle = 'assets/bundle'; // unzip your exported bundle here

  int W = 0, H = 0;
  late Uint8List _original; // RGBA
  late Uint8List _idmap;    // RGBA (id = R + G*256)
  late Uint8List _display;  // RGBA (what we draw)
  ui.Image? _displayImg;
  ui.Image? _borderImg;

  final Map<int, Region> _regionById = {};
  final Map<int, List<int>> _idsByNumber = {};
  final Map<int, int> _filledByNumber = {};
  final List<Swatch> _palette = [];
  final Set<int> _filled = {};
  final List<_Fill> _active = [];

  int? _sel;
  bool _loading = true;
  bool _rebuilding = false;
  late final AnimationController _anim;

  @override
  void initState() {
    super.initState();
    _anim = AnimationController(vsync: this, duration: const Duration(milliseconds: 300))
      ..addListener(_onTick);
    _load();
  }

  @override
  void dispose() {
    _anim.dispose();
    super.dispose();
  }

  Future<Uint8List> _rawRgba(String path) async {
    final data = await rootBundle.load(path);
    final codec = await ui.instantiateImageCodec(data.buffer.asUint8List());
    final frame = await codec.getNextFrame();
    W = frame.image.width;
    H = frame.image.height;
    final bd = await frame.image.toByteData(format: ui.ImageByteFormat.rawRgba);
    return bd!.buffer.asUint8List();
  }

  Future<ui.Image> _image(String path) async {
    final data = await rootBundle.load(path);
    final codec = await ui.instantiateImageCodec(data.buffer.asUint8List());
    return (await codec.getNextFrame()).image;
  }

  Future<void> _load() async {
    _original = await _rawRgba('$bundle/original.png');
    _idmap = await _rawRgba('$bundle/id_map.png');
    _borderImg = await _image('$bundle/border.png');

    final js = jsonDecode(await rootBundle.loadString('$bundle/regions.json')) as Map<String, dynamic>;
    for (final r in (js['regions'] as List)) {
      final reg = Region(r['id'], r['number'], r['cx'], r['cy'],
          (r['r'] ?? 0).toDouble(), List<int>.from(r['bbox']));
      _regionById[reg.id] = reg;
      _idsByNumber.putIfAbsent(reg.number, () => []).add(reg.id);
    }
    for (final p in (js['palette'] as List)) {
      final hex = (p['color'] as String).replaceFirst('#', '');
      _palette.add(Swatch(p['number'], Color(0xFF000000 | int.parse(hex, radix: 16)),
          '$bundle/${p['swatch']}'));
    }
    _palette.sort((a, b) => a.number.compareTo(b.number));
    for (final p in _palette) {
      _filledByNumber[p.number] = 0;
    }
    _sel = _palette.isNotEmpty ? _palette.first.number : null;

    _display = Uint8List(W * H * 4)..fillRange(0, W * H * 4, 255); // white page
    await _rebuild();
    setState(() => _loading = false);
  }

  Future<void> _rebuild() async {
    if (_rebuilding) return;
    _rebuilding = true;
    final c = Completer<ui.Image>();
    // copy so an in-flight decode can't race with the next animation tick mutating _display
    ui.decodeImageFromPixels(Uint8List.fromList(_display), W, H, ui.PixelFormat.rgba8888, c.complete);
    _displayImg = await c.future;
    _rebuilding = false;
    if (mounted) setState(() {});
  }

  int _idAt(int x, int y) {
    final i = (y * W + x) * 4;
    return _idmap[i] + _idmap[i + 1] * 256; // R + G*256
  }

  void _onTapDown(TapDownDetails d, Size canvas) {
    final x = (d.localPosition.dx * W / canvas.width).floor();
    final y = (d.localPosition.dy * H / canvas.height).floor();
    if (x < 0 || y < 0 || x >= W || y >= H) return;
    final id = _idAt(x, y);
    final reg = _regionById[id];
    if (reg == null || _filled.contains(id)) return;
    if (reg.number != _sel) setState(() => _sel = reg.number);
    _fillRegion(id);
  }

  void _fillRegion(int id) {
    final reg = _regionById[id];
    if (reg == null || _filled.contains(id)) return;
    _filled.add(id);
    _filledByNumber[reg.number] = (_filledByNumber[reg.number] ?? 0) + 1;

    final bb = reg.bbox;
    final idx = <int>[];
    for (int y = bb[1]; y < bb[1] + bb[3]; y++) {
      final rowBase = y * W;
      for (int x = bb[0]; x < bb[0] + bb[2]; x++) {
        final i = (rowBase + x) * 4;
        if (_idmap[i] + _idmap[i + 1] * 256 == id) idx.add(i);
      }
    }
    final col = _palette.firstWhere((p) => p.number == reg.number,
        orElse: () => Swatch(reg.number, const Color(0xFFC8C8C8), '')).color;
    for (final i in idx) {
      _display[i] = col.red;
      _display[i + 1] = col.green;
      _display[i + 2] = col.blue;
      _display[i + 3] = 255;
    }
    _active.add(_Fill(idx, col)); // animate flat -> real photo
    _advance(reg.number);
    if (!_anim.isAnimating) _anim.forward(from: 0);
    _rebuild();
  }

  void _onTick() {
    final t = Curves.easeInOut.transform(_anim.value);
    for (final f in _active) {
      final r = f.color.red, g = f.color.green, b = f.color.blue;
      for (final i in f.idx) {
        _display[i] = (r + (_original[i] - r) * t).round();
        _display[i + 1] = (g + (_original[i + 1] - g) * t).round();
        _display[i + 2] = (b + (_original[i + 2] - b) * t).round();
        _display[i + 3] = 255;
      }
    }
    if (_anim.value >= 1.0) _active.clear();
    _rebuild();
  }

  void _advance(int number) {
    if ((_filledByNumber[number] ?? 0) >= (_idsByNumber[number]?.length ?? 0)) {
      for (final p in _palette) {
        if ((_filledByNumber[p.number] ?? 0) < _idsByNumber[p.number]!.length) {
          _sel = p.number;
          break;
        }
      }
    }
  }

  void _reset() {
    setState(() {
      _display.fillRange(0, _display.length, 255);
      _filled.clear();
      _active.clear();
      for (final p in _palette) {
        _filledByNumber[p.number] = 0;
      }
      _sel = _palette.isNotEmpty ? _palette.first.number : null;
    });
    _rebuild();
  }

  @override
  Widget build(BuildContext context) {
    if (_loading || _displayImg == null || _borderImg == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final done = _filled.length == _regionById.length;
    return Scaffold(
      appBar: AppBar(
        title: Text('ColorForge · ${_filled.length}/${_regionById.length}${done ? " — done!" : ""}'),
        actions: [IconButton(onPressed: _reset, icon: const Icon(Icons.refresh))],
      ),
      body: Column(children: [
        Expanded(
          child: Center(
            child: LayoutBuilder(builder: (ctx, cons) {
              final ar = W / H;
              double cw = cons.maxWidth, ch = cw / ar;
              if (ch > cons.maxHeight) {
                ch = cons.maxHeight;
                cw = ch * ar;
              }
              final size = Size(cw, ch);
              return GestureDetector(
                onTapDown: (d) => _onTapDown(d, size),
                child: CustomPaint(
                  size: size,
                  painter: _GamePainter(_displayImg!, _borderImg!, _regionById, _filled, _sel, _palette),
                ),
              );
            }),
          ),
        ),
        _paletteBar(),
      ]),
    );
  }

  Widget _paletteBar() {
    return Container(
      height: 96,
      color: const Color(0xFF12161C),
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.all(10),
        itemCount: _palette.length,
        itemBuilder: (ctx, i) {
          final p = _palette[i];
          final total = _idsByNumber[p.number]!.length;
          final left = total - (_filledByNumber[p.number] ?? 0);
          final isDone = left <= 0;
          return GestureDetector(
            onTap: () => setState(() => _sel = p.number),
            child: Opacity(
              opacity: isDone ? 0.35 : 1,
              child: Container(
                width: 60,
                height: 60,
                margin: const EdgeInsets.symmetric(horizontal: 5, vertical: 6),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  image: DecorationImage(image: AssetImage(p.asset), fit: BoxFit.cover),
                  border: Border.all(color: p.number == _sel ? Colors.white : Colors.transparent, width: 3),
                ),
                child: Stack(children: [
                  Center(
                    child: Text('${p.number}',
                        style: const TextStyle(
                            fontWeight: FontWeight.w800,
                            fontSize: 17,
                            color: Colors.white,
                            shadows: [Shadow(blurRadius: 3, color: Colors.black), Shadow(blurRadius: 2, color: Colors.black)])),
                  ),
                  Positioned(
                    right: -2,
                    bottom: -2,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      decoration: BoxDecoration(color: const Color(0xFF0F1216), borderRadius: BorderRadius.circular(8)),
                      child: Text(isDone ? '✓' : '$left',
                          style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: Color(0xFF9AA4B2))),
                    ),
                  ),
                ]),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _GamePainter extends CustomPainter {
  final ui.Image display, border;
  final Map<int, Region> regions;
  final Set<int> filled;
  final int? sel;
  final List<Swatch> palette;
  _GamePainter(this.display, this.border, this.regions, this.filled, this.sel, this.palette);

  @override
  void paint(Canvas canvas, Size size) {
    final dst = Rect.fromLTWH(0, 0, size.width, size.height);
    final paint = Paint()..filterQuality = FilterQuality.medium;
    canvas.drawImageRect(display, Rect.fromLTWH(0, 0, display.width.toDouble(), display.height.toDouble()), dst, paint);
    canvas.drawImageRect(border, Rect.fromLTWH(0, 0, border.width.toDouble(), border.height.toDouble()), dst, paint);

    final sx = size.width / display.width, sy = size.height / display.height;
    for (final r in regions.values) {
      if (filled.contains(r.id) || r.r < 8) continue;
      final isSel = r.number == sel;
      if (isSel) {
        final col = palette.firstWhere((p) => p.number == r.number,
            orElse: () => Swatch(r.number, const Color(0xFFC8C8C8), '')).color;
        canvas.drawCircle(Offset(r.cx * sx, r.cy * sy), (r.r * sx).clamp(6, 14), Paint()..color = col);
      }
      final tp = TextPainter(
        text: TextSpan(
            text: '${r.number}',
            style: TextStyle(
                fontSize: isSel ? 14 : 12,
                fontWeight: FontWeight.w700,
                color: isSel ? Colors.white : const Color(0xFF6E6E6E))),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, Offset(r.cx * sx - tp.width / 2, r.cy * sy - tp.height / 2));
    }
  }

  @override
  bool shouldRepaint(covariant _GamePainter old) => true;
}
