import 'package:flutter/material.dart';

import '../gallery/new2026_page.dart';
import '../settings/settings_page.dart';

/// The app frame: a bottom navigation bar over the main sections.
///
/// Daily and Diamond are stubs for now — they'll share the same gallery/grid as
/// New 2026 once the backend feeds are wired. Keeping them here fixes the layout
/// and navigation early so later work just fills panels.
class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  late final List<Widget> _pages = const [
    New2026Page(),
    _Placeholder(title: 'Daily', subtitle: 'Fresh picture every day — coming next'),
    _Placeholder(title: 'Diamond', subtitle: 'Premium pictures — coming next'),
    SettingsPage(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _index, children: _pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.auto_awesome_outlined),
            selectedIcon: Icon(Icons.auto_awesome),
            label: 'New 2026',
          ),
          NavigationDestination(
            icon: Icon(Icons.today_outlined),
            selectedIcon: Icon(Icons.today),
            label: 'Daily',
          ),
          NavigationDestination(
            icon: Icon(Icons.diamond_outlined),
            selectedIcon: Icon(Icons.diamond),
            label: 'Diamond',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings),
            label: 'Settings',
          ),
        ],
      ),
    );
  }
}

class _Placeholder extends StatelessWidget {
  final String title;
  final String subtitle;
  const _Placeholder({required this.title, required this.subtitle});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: Center(
        child: Text(
          subtitle,
          style: const TextStyle(color: Colors.white38),
        ),
      ),
    );
  }
}
