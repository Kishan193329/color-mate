import 'package:flutter/material.dart';

/// One place for the app's look. A dark, modern gaming palette with a single
/// indigo accent, applied through Material 3 so every screen matches.
class AppTheme {
  static const Color accent = Color(0xFF6C63FF);
  static const Color bg = Color(0xFF101116);
  static const Color surface = Color(0xFF1A1C24);

  static ThemeData get dark {
    final base = ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: ColorScheme.fromSeed(
        seedColor: accent,
        brightness: Brightness.dark,
      ).copyWith(surface: surface),
      scaffoldBackgroundColor: bg,
    );
    return base.copyWith(
      appBarTheme: const AppBarTheme(
        backgroundColor: bg,
        elevation: 0,
        centerTitle: false,
      ),
      splashFactory: InkSparkle.splashFactory,
    );
  }
}
