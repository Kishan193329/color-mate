import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

/// One numbered region of the picture.
///
/// Mirrors an entry in the backend's `regions.json`. `cx,cy` is where the number
/// label sits and `r` is the label radius the pipeline picked — we reuse `r` to
/// decide which regions are large enough to show a number without clutter.
class Region {
  final int id;
  final int number;
  final double cx;
  final double cy;
  final double r;

  const Region({
    required this.id,
    required this.number,
    required this.cx,
    required this.cy,
    required this.r,
  });

  factory Region.fromJson(Map<String, dynamic> j) => Region(
        id: (j['id'] as num).toInt(),
        number: (j['number'] as num).toInt(),
        cx: (j['cx'] as num).toDouble(),
        cy: (j['cy'] as num).toDouble(),
        r: (j['r'] as num).toDouble(),
      );
}

/// One palette entry: a number and the colour it stands for.
class PaletteColor {
  final int number;
  final Color color;

  const PaletteColor({required this.number, required this.color});

  factory PaletteColor.fromJson(Map<String, dynamic> j) => PaletteColor(
        number: (j['number'] as num).toInt(),
        color: _parseHex(j['color'] as String),
      );

  static Color _parseHex(String hex) {
    var h = hex.replaceFirst('#', '').trim();
    if (h.length == 6) h = 'FF$h';
    return Color(int.parse(h, radix: 16));
  }
}

/// Everything the reveal game needs for one picture, decoded and ready to play.
///
/// Built by [RevealDataLoader]; the game itself never touches PNGs or JSON.
class RevealData {
  final int w;
  final int h;
  final List<Region> regions;
  final List<PaletteColor> palette;

  /// The line-art base (numbers + borders) shown before anything is revealed.
  final ui.Image borderImage;

  /// Region id for every pixel, row-major, length `w*h`. 0 means "no region".
  final Int32List regionIdAt;

  /// The photograph a filled region reveals, as RGBA at exactly `w*h`.
  final Uint8List originalRgba;

  /// The line art as RGBA at exactly `w*h` — the starting state of the canvas.
  final Uint8List borderRgba;

  /// number -> its colour.
  final Map<int, Color> colorByNumber;

  /// region id -> its number.
  final Map<int, int> numberByRegionId;

  /// Distinct numbers actually used, in ascending order (the palette bar order).
  final List<int> numbers;

  /// number -> how many regions carry it (for the "how much is left" counter).
  final Map<int, int> totalByNumber;

  RevealData({
    required this.w,
    required this.h,
    required this.regions,
    required this.palette,
    required this.borderImage,
    required this.regionIdAt,
    required this.originalRgba,
    required this.borderRgba,
    required this.colorByNumber,
    required this.numberByRegionId,
    required this.numbers,
    required this.totalByNumber,
  });

  Color colorFor(int number) => colorByNumber[number] ?? Colors.grey;
}
