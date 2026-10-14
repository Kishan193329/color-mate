import 'package:flutter_test/flutter_test.dart';

import 'package:colour_mate/app.dart';

void main() {
  testWidgets('App boots to the home shell', (WidgetTester tester) async {
    await tester.pumpWidget(const ColourMateApp());
    await tester.pump();

    // The bottom navigation sections are present.
    expect(find.text('New 2026'), findsWidgets);
    expect(find.text('Daily'), findsWidgets);
  });
}
