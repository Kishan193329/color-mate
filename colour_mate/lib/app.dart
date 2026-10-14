import 'package:flutter/material.dart';

import 'features/home/home_shell.dart';
import 'theme/app_theme.dart';

class ColourMateApp extends StatelessWidget {
  const ColourMateApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Colour Mate',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark,
      home: const HomeShell(),
    );
  }
}
