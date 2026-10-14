import 'dart:convert';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import 'reveal_data.dart';

/// Turns the four raw files of a picture package into a ready-to-play [RevealData].
///
/// It is source-agnostic on purpose: bundled asset, downloaded file, or network
/// bytes all arrive here the same way, so there is exactly one decode path.
class RevealLoader {
  /// Decode a picture from its four raw parts.
  ///
  /// [regionsJson]  – the `regions.json` text
  /// [idMapBytes]   – `id_map.png` (id = R + G*256, must match w*h, never resampled)
  /// [borderBytes]  – `border.png` line art
  /// [originalBytes]– the reveal photo (`original.png`/`.jpg`)
  static Future<RevealData> decode({
    required String regionsJson,
    required Uint8List idMapBytes,
    required Uint8List borderBytes,
    required Uint8List originalBytes,
  }) async {
    final meta = json.decode(regionsJson) as Map<String, dynamic>;
    final w = (meta['w'] as num).toInt();
    final h = (meta['h'] as num).toInt();

    final regions = (meta['regions'] as List)
        .map((e) => Region.fromJson(e as Map<String, dynamic>))
        .toList();
    final palette = (meta['palette'] as List)
        .map((e) => PaletteColor.fromJson(e as Map<String, dynamic>))
        .toList();

    final colorByNumber = {for (final p in palette) p.number: p.color};
    final numberByRegionId = {for (final r in regions) r.id: r.number};

    final totalByNumber = <int, int>{};
    for (final r in regions) {
      totalByNumber[r.number] = (totalByNumber[r.number] ?? 0) + 1;
    }
    final numbers = totalByNumber.keys.toList()..sort();

    // Decode the three images concurrently.
    final results = await Future.wait([
      _decodeImage(idMapBytes),
      _decodeImage(borderBytes),
      _decodeImage(originalBytes),
    ]);
    final idMapImg = results[0];
    final borderImg = results[1];
    final originalImg = results[2];

    // id map: read raw, NEVER interpolate — a blended id is a wrong id.
    final idRgba = await _rawRgba(idMapImg, w, h, smooth: false);
    final regionIdAt = Int32List(w * h);
    for (var i = 0; i < w * h; i++) {
      regionIdAt[i] = idRgba[i * 4] + (idRgba[i * 4 + 1] << 8);
    }

    // Border + photo normalised to exactly w*h so every buffer shares one grid.
    final borderRgba = await _rawRgba(borderImg, w, h, smooth: true);
    final originalRgba = await _rawRgba(originalImg, w, h, smooth: true);

    idMapImg.dispose();
    originalImg.dispose();

    return RevealData(
      w: w,
      h: h,
      regions: regions,
      palette: palette,
      borderImage: borderImg, // kept alive for the painter
      regionIdAt: regionIdAt,
      originalRgba: originalRgba,
      borderRgba: borderRgba,
      colorByNumber: colorByNumber,
      numberByRegionId: numberByRegionId,
      numbers: numbers,
      totalByNumber: totalByNumber,
    );
  }

  static Future<ui.Image> _decodeImage(Uint8List bytes) async {
    final codec = await ui.instantiateImageCodec(bytes);
    final frame = await codec.getNextFrame();
    return frame.image;
  }

  /// RGBA bytes for [img] at exactly [w]x[h]. When the source already matches we
  /// read it straight; otherwise we redraw it at the target size ([smooth] picks
  /// bilinear vs nearest — nearest is required for the id map).
  static Future<Uint8List> _rawRgba(ui.Image img, int w, int h,
      {required bool smooth}) async {
    if (img.width == w && img.height == h) {
      final bd = await img.toByteData(format: ui.ImageByteFormat.rawRgba);
      return bd!.buffer.asUint8List();
    }
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    final paint = Paint()
      ..filterQuality = smooth ? FilterQuality.medium : FilterQuality.none;
    canvas.drawImageRect(
      img,
      Rect.fromLTWH(0, 0, img.width.toDouble(), img.height.toDouble()),
      Rect.fromLTWH(0, 0, w.toDouble(), h.toDouble()),
      paint,
    );
    final resized = await recorder.endRecording().toImage(w, h);
    final bd = await resized.toByteData(format: ui.ImageByteFormat.rawRgba);
    resized.dispose();
    return bd!.buffer.asUint8List();
  }
}
