import 'dart:typed_data';

import 'package:flutter/services.dart' show rootBundle;

import 'reveal_data.dart';
import 'reveal_loader.dart';

/// Where a picture package comes from. The game only knows this interface, so a
/// bundled sample, a downloaded folder, or a network fetch are interchangeable.
abstract class RevealSource {
  /// A stable title for the app bar.
  String get title;

  /// Load and decode the four parts into a ready [RevealData].
  Future<RevealData> load();
}

/// A picture bundled in the APK under `assets/<dir>/` with the four standard
/// files. Used for the offline sample and any built-in content.
class AssetRevealSource implements RevealSource {
  final String dir; // e.g. 'assets/sample_reveal'
  @override
  final String title;

  /// original may be `.png` or `.jpg` depending on the package.
  final String originalName;

  const AssetRevealSource({
    required this.dir,
    this.title = 'Sample',
    this.originalName = 'original.jpg',
  });

  @override
  Future<RevealData> load() async {
    final regionsJson = await rootBundle.loadString('$dir/regions.json');
    final idMap = await _bytes('$dir/id_map.png');
    final border = await _bytes('$dir/border.png');
    final original = await _bytes('$dir/$originalName');
    return RevealLoader.decode(
      regionsJson: regionsJson,
      idMapBytes: idMap,
      borderBytes: border,
      originalBytes: original,
    );
  }

  Future<Uint8List> _bytes(String path) async =>
      (await rootBundle.load(path)).buffer.asUint8List();
}
