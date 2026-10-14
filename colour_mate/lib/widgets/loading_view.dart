import 'package:flutter/material.dart';

/// The one loading indicator used across the app, so every wait looks the same.
class LoadingView extends StatelessWidget {
  final String? label;
  const LoadingView({super.key, this.label});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(
            width: 34,
            height: 34,
            child: CircularProgressIndicator(
              strokeWidth: 3,
              valueColor: AlwaysStoppedAnimation(Color(0xFF6C63FF)),
            ),
          ),
          if (label != null) ...[
            const SizedBox(height: 14),
            Text(
              label!,
              style: const TextStyle(color: Colors.white54, fontSize: 13),
            ),
          ],
        ],
      ),
    );
  }
}
