import 'package:flutter/material.dart';

/// App settings — a stub for now. Sound, vibration, server URL and the coin/ad
/// options land here as those systems come online, shared across every screen.
class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        children: const [
          _Tile(icon: Icons.volume_up_outlined, title: 'Sound', trailing: 'On'),
          _Tile(icon: Icons.vibration, title: 'Vibration', trailing: 'On'),
          _Tile(icon: Icons.info_outline, title: 'Version', trailing: '1.0.0'),
        ],
      ),
    );
  }
}

class _Tile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String trailing;
  const _Tile({required this.icon, required this.title, required this.trailing});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: Colors.white70),
      title: Text(title, style: const TextStyle(color: Colors.white)),
      trailing: Text(trailing, style: const TextStyle(color: Colors.white38)),
    );
  }
}
