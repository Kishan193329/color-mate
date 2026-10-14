import 'package:flutter/material.dart';

import '../../core/reveal/reveal_controller.dart';
import '../../core/reveal/reveal_data.dart';
import '../../core/reveal/reveal_source.dart';
import '../../widgets/loading_view.dart';
import 'palette_bar.dart';
import 'reveal_painter.dart';

/// The reveal game screen. Loads a [RevealSource], then plays it colour-by-number:
/// pick a number, its regions light up, tap them to reveal the photo underneath.
class RevealGamePage extends StatefulWidget {
  final RevealSource source;

  const RevealGamePage({super.key, required this.source});

  @override
  State<RevealGamePage> createState() => _RevealGamePageState();
}

class _RevealGamePageState extends State<RevealGamePage>
    with SingleTickerProviderStateMixin {
  late final Future<RevealData> _future = widget.source.load();
  RevealController? _controller;

  late final AnimationController _pulse = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 900),
  )..repeat(reverse: true);

  bool _celebrated = false;

  @override
  void dispose() {
    _pulse.dispose();
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF14151A),
      body: SafeArea(
        child: FutureBuilder<RevealData>(
          future: _future,
          builder: (context, snap) {
            if (snap.hasError) {
              return _ErrorView(message: '${snap.error}');
            }
            if (!snap.hasData) {
              return LoadingView(label: 'Preparing ${widget.source.title}…');
            }
            _controller ??= RevealController(snap.data!);
            return _buildGame(_controller!);
          },
        ),
      ),
    );
  }

  Widget _buildGame(RevealController controller) {
    return Column(
      children: [
        _Header(controller: controller),
        Expanded(child: _canvas(controller)),
        PaletteBar(controller: controller),
      ],
    );
  }

  Widget _canvas(RevealController controller) {
    final data = controller.data;
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        // Celebrate once, after the frame, when the picture is finished.
        if (controller.isComplete && !_celebrated) {
          _celebrated = true;
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) _showComplete();
          });
        }
        return InteractiveViewer(
          maxScale: 6,
          child: Center(
            child: AspectRatio(
              aspectRatio: data.w / data.h,
              child: Builder(
                builder: (ctx) => GestureDetector(
                  onTapUp: (d) {
                    final box = ctx.findRenderObject() as RenderBox;
                    final sz = box.size;
                    controller.tapImage(
                      d.localPosition.dx / sz.width * data.w,
                      d.localPosition.dy / sz.height * data.h,
                    );
                  },
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      CustomPaint(painter: RevealCanvasPainter(controller)),
                      AnimatedBuilder(
                        animation: _pulse,
                        builder: (context, _) => CustomPaint(
                          painter: HighlightPainter(
                            controller: controller,
                            pulse: _pulse.value,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  void _showComplete() {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF1E2028),
        title: const Text('Done! 🎉', style: TextStyle(color: Colors.white)),
        content: const Text('You revealed the whole picture.',
            style: TextStyle(color: Colors.white70)),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.of(context).pop();
              Navigator.of(context).maybePop();
            },
            child: const Text('Back'),
          ),
        ],
      ),
    );
  }
}

class _Header extends StatelessWidget {
  final RevealController controller;
  const _Header({required this.controller});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        return Padding(
          padding: const EdgeInsets.fromLTRB(4, 4, 16, 4),
          child: Row(
            children: [
              IconButton(
                icon: const Icon(Icons.arrow_back, color: Colors.white),
                onPressed: () => Navigator.of(context).maybePop(),
              ),
              Expanded(
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: LinearProgressIndicator(
                    value: controller.progress,
                    minHeight: 8,
                    backgroundColor: Colors.white12,
                    valueColor: const AlwaysStoppedAnimation(Color(0xFF6C63FF)),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Text(
                '${(controller.progress * 100).round()}%',
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String message;
  const _ErrorView({required this.message});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.broken_image_outlined,
                color: Colors.white38, size: 48),
            const SizedBox(height: 12),
            Text(
              "Couldn't open this picture.\n$message",
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white54),
            ),
          ],
        ),
      ),
    );
  }
}
