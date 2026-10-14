import 'package:flutter/material.dart';

import '../../core/reveal/reveal_controller.dart';
import '../../core/reveal/reveal_data.dart';

/// Minimum on-screen label radius; smaller regions stay unlabelled to avoid
/// clutter (the highlight still shows where they are).
const double _kMinLabelPx = 9.0;

/// The heavy layer: the composited picture plus every visible number label.
/// Repaints only when the game state changes (a region fills), never per frame.
class RevealCanvasPainter extends CustomPainter {
  final RevealController controller;

  RevealCanvasPainter(this.controller) : super(repaint: controller);

  RevealData get data => controller.data;

  @override
  void paint(Canvas canvas, Size size) {
    final image = controller.composite;
    if (image == null) return;

    final scale = size.width / data.w;

    canvas.drawImageRect(
      image,
      Rect.fromLTWH(0, 0, image.width.toDouble(), image.height.toDouble()),
      Rect.fromLTWH(0, 0, size.width, size.height),
      Paint()..filterQuality = FilterQuality.medium,
    );

    final selected = controller.selectedNumber;
    for (final region in data.regions) {
      if (controller.isFilled(region.id)) continue;
      final labelPx = region.r * scale;
      if (labelPx < _kMinLabelPx) continue;

      final isSel = region.number == selected;
      final tp = TextPainter(
        text: TextSpan(
          text: '${region.number}',
          style: TextStyle(
            fontSize: (labelPx * 0.95).clamp(8.0, 22.0),
            color: isSel ? Colors.black : Colors.black45,
            fontWeight: isSel ? FontWeight.w800 : FontWeight.w500,
            height: 1.0,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(
        canvas,
        Offset(region.cx * scale, region.cy * scale) -
            Offset(tp.width / 2, tp.height / 2),
      );
    }
  }

  @override
  bool shouldRepaint(covariant RevealCanvasPainter old) =>
      old.controller != controller;
}

/// The light layer: only the selected number's unfilled regions, pulsing. Cheap
/// enough to repaint every animation frame.
class HighlightPainter extends CustomPainter {
  final RevealController controller;
  final double pulse; // 0..1

  HighlightPainter({required this.controller, required this.pulse})
      : super(repaint: controller);

  RevealData get data => controller.data;

  @override
  void paint(Canvas canvas, Size size) {
    final selected = controller.selectedNumber;
    if (selected == null) return;
    final scale = size.width / data.w;
    final color = data.colorFor(selected);

    final disc = Paint()
      ..style = PaintingStyle.fill
      ..color = color.withValues(alpha: 0.25 + 0.25 * pulse);
    final ring = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2
      ..color = color.withValues(alpha: 0.9);

    for (final region in data.regions) {
      if (region.number != selected) continue;
      if (controller.isFilled(region.id)) continue;
      final labelPx = region.r * scale;
      if (labelPx < 4) continue;
      final center = Offset(region.cx * scale, region.cy * scale);
      canvas.drawCircle(center, labelPx * (1.05 + 0.3 * pulse), disc);
      canvas.drawCircle(center, labelPx * 1.05, ring);
    }
  }

  @override
  bool shouldRepaint(covariant HighlightPainter old) =>
      old.pulse != pulse || old.controller != controller;
}
