import 'package:flutter/material.dart';

import '../../core/reveal/reveal_controller.dart';

/// The scrollable strip of numbered colour chips at the bottom of the game.
///
/// Tapping a chip selects that number; the canvas then highlights every region
/// that carries it. A finished number collapses to a tick.
class PaletteBar extends StatelessWidget {
  final RevealController controller;

  const PaletteBar({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    final data = controller.data;
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        return Container(
          height: 84,
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: data.numbers.length,
            separatorBuilder: (_, i) => const SizedBox(width: 10),
            itemBuilder: (context, i) {
              final number = data.numbers[i];
              final color = data.colorFor(number);
              final done = controller.isNumberDone(number);
              final selected = controller.selectedNumber == number;
              return _Chip(
                number: number,
                color: color,
                remaining: controller.remainingFor(number),
                done: done,
                selected: selected,
                onTap: done ? null : () => controller.selectNumber(number),
              );
            },
          ),
        );
      },
    );
  }
}

class _Chip extends StatelessWidget {
  final int number;
  final Color color;
  final int remaining;
  final bool done;
  final bool selected;
  final VoidCallback? onTap;

  const _Chip({
    required this.number,
    required this.color,
    required this.remaining,
    required this.done,
    required this.selected,
    required this.onTap,
  });

  Color get _fg {
    final l = 0.299 * color.r + 0.587 * color.g + 0.114 * color.b;
    return l > 0.6 ? Colors.black87 : Colors.white;
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedOpacity(
      duration: const Duration(milliseconds: 200),
      opacity: done ? 0.35 : 1,
      child: GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 160),
          width: 60,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: selected ? Colors.white : Colors.transparent,
              width: 3,
            ),
            boxShadow: selected
                ? [
                    BoxShadow(
                      color: color.withValues(alpha: 0.6),
                      blurRadius: 12,
                      spreadRadius: 1,
                    )
                  ]
                : const [],
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (done)
                Icon(Icons.check_rounded, color: _fg, size: 26)
              else ...[
                Text(
                  '$number',
                  style: TextStyle(
                    color: _fg,
                    fontWeight: FontWeight.w800,
                    fontSize: 20,
                    height: 1,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  '$remaining',
                  style: TextStyle(
                    color: _fg.withValues(alpha: 0.75),
                    fontSize: 11,
                    height: 1,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
