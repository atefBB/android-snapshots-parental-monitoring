import 'package:flutter_test/flutter_test.dart';

import 'package:parental_monitor/main.dart';

void main() {
  testWidgets('App renders home screen', (WidgetTester tester) async {
    await tester.pumpWidget(const ParentalMonitorApp());

    // Verify that the app renders with the expected title
    expect(find.text('Parental Monitor'), findsOneWidget);
    expect(find.text('Start Monitoring'), findsOneWidget);
  });
}
