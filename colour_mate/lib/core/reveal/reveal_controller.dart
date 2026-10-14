import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';

import 'reveal_data.dart';

/// Owns the live state of one reveal game: which number is selected, which
/// regions are filled, and the composited image that shows the result.
///
/// The canvas is one RGBA buffer that starts as the line art and, region by
/// region, has the photograph written into it. After each change we hand the
/// buffer to `decodeImageFromPixels` to get a fresh [ui.Image] for the painter.
class RevealController extends ChangeNotifier {
  final RevealData data;

  final Uint8List _work; // the mutable canvas, w*h*4 RGBA
  final Set<int> _filled = <int>{};
  final Map<int, int> _remainingByNumber;

  int? _selectedNumber;
  ui.Image? _composite;

  bool _recomposing = false;
  bool _dirty = false;

  RevealController(this.data)
      : _work = Uint8List.fromList(data.borderRgba),
        _remainingByNumber = Map<int, int>.from(data.totalByNumber) {
    _recompose();
    // Start on the number with the most regions — a satisfying first fill.
    if (data.numbers.isNotEmpty) {
      final start = data.numbers.reduce(
          (a, b) => (data.totalByNumber[a]! >= data.totalByNumber[b]!) ? a : b);
      _selectedNumber = start;
    }
  }

  // ── read-only state for the UI ──
  ui.Image? get composite => _composite;
  int? get selectedNumber => _selectedNumber;
  bool isFilled(int regionId) => _filled.contains(regionId);
  int remainingFor(int number) => _remainingByNumber[number] ?? 0;
  bool isNumberDone(int number) => remainingFor(number) == 0;

  int get filledCount => _filled.length;
  int get totalCount => data.regions.length;
  double get progress => totalCount == 0 ? 1 : _filled.length / totalCount;
  bool get isComplete => _filled.length >= totalCount;

  void selectNumber(int number) {
    if (_selectedNumber == number) return;
    _selectedNumber = number;
    notifyListeners();
  }

  /// A tap at image-pixel [x],[y]. Fills the region there only when it belongs
  /// to the selected number (true colour-by-number), then auto-advances when a
  /// number is finished. Returns true if something was filled.
  bool tapImage(double x, double y) {
    final px = x.floor();
    final py = y.floor();
    if (px < 0 || py < 0 || px >= data.w || py >= data.h) return false;

    final id = data.regionIdAt[py * data.w + px];
    if (id == 0 || _filled.contains(id)) return false;

    final number = data.numberByRegionId[id];
    if (number == null) return false;

    // With nothing chosen yet, a tap adopts that region's number for convenience.
    if (_selectedNumber == null) {
      _selectedNumber = number;
      notifyListeners();
      return false;
    }
    if (number != _selectedNumber) {
      return false; // wrong colour — ignore, the highlight shows where to tap
    }

    _fillRegion(id, number);
    if (isNumberDone(number)) _advanceFrom(number);
    _scheduleRecompose();
    return true;
  }

  void _fillRegion(int id, int number) {
    _filled.add(id);
    _remainingByNumber[number] = (_remainingByNumber[number] ?? 1) - 1;

    // Write the photo's pixels for this region into the canvas.
    final ids = data.regionIdAt;
    final src = data.originalRgba;
    final dst = _work;
    final n = ids.length;
    for (var i = 0; i < n; i++) {
      if (ids[i] == id) {
        final o = i * 4;
        dst[o] = src[o];
        dst[o + 1] = src[o + 1];
        dst[o + 2] = src[o + 2];
        dst[o + 3] = src[o + 3];
      }
    }
  }

  /// Move to the next number that still has regions left.
  void _advanceFrom(int justFinished) {
    final nums = data.numbers;
    final start = nums.indexOf(justFinished);
    for (var step = 1; step <= nums.length; step++) {
      final cand = nums[(start + step) % nums.length];
      if (!isNumberDone(cand)) {
        _selectedNumber = cand;
        return;
      }
    }
    _selectedNumber = null; // all done
  }

  // ── compositing ──

  void _scheduleRecompose() {
    _dirty = true;
    if (!_recomposing) _recompose();
  }

  Future<void> _recompose() async {
    _recomposing = true;
    _dirty = false;
    final w = data.w, h = data.h;
    // Copy so we can keep mutating _work while the decode runs.
    final snapshot = Uint8List.fromList(_work);
    final image = await _decodePixels(snapshot, w, h);
    final old = _composite;
    _composite = image;
    old?.dispose();
    notifyListeners();
    _recomposing = false;
    if (_dirty) _recompose(); // fold in taps that landed mid-decode
  }

  static Future<ui.Image> _decodePixels(Uint8List rgba, int w, int h) {
    final c = Completer<ui.Image>();
    ui.decodeImageFromPixels(rgba, w, h, ui.PixelFormat.rgba8888, c.complete);
    return c.future;
  }

  @override
  void dispose() {
    _composite?.dispose();
    data.borderImage.dispose();
    super.dispose();
  }
}
