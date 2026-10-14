import 'package:flutter/material.dart';

import '../../core/reveal/reveal_source.dart';
import '../reveal/reveal_game_page.dart';

/// "New 2026" — ColorForge's own content.
///
/// For this first milestone it shows the one bundled sample so the reveal game
/// is playable offline. Next step swaps this for the live category grid from the
/// backend (border/line-art thumbnails, same card layout).
class New2026Page extends StatelessWidget {
  const New2026Page({super.key});

  static const _sample = AssetRevealSource(
    dir: 'assets/sample_reveal',
    title: 'Sample',
    originalName: 'original.jpg',
  );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('New 2026'),
      ),
      body: GridView.count(
        crossAxisCount: 2,
        padding: const EdgeInsets.all(12),
        mainAxisSpacing: 12,
        crossAxisSpacing: 12,
        children: [
          _PictureCard(
            // The grid thumbnail is the line art, exactly like the game base.
            thumb: const AssetImage('assets/sample_reveal/border.png'),
            label: 'Sample picture',
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => const RevealGamePage(source: _sample),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PictureCard extends StatelessWidget {
  final ImageProvider thumb;
  final String label;
  final VoidCallback onTap;

  const _PictureCard({
    required this.thumb,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: const Color(0xFF1A1C24),
      borderRadius: BorderRadius.circular(16),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: Container(
                color: Colors.white,
                child: Image(image: thumb, fit: BoxFit.contain),
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(10),
              child: Text(
                label,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w600,
                  fontSize: 13,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
