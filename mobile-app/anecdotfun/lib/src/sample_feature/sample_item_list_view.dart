import 'package:flutter/material.dart';

import '../settings/settings_view.dart';

/// Displays a list of SampleItems.
class SampleItemListView extends StatelessWidget {
  static const routeName = '/';

  const SampleItemListView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Cast your vote'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () {
              // Navigate to the settings page. If the user leaves and returns
              // to the app after it has been killed while running in the
              // background, the navigation stack is restored.
              Navigator.restorablePushNamed(context, SettingsView.routeName);
            },
          ),
        ],
      ),
      body: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          ElevatedButton(
            onPressed: () {
              // Handle True button press
            },
            child: const Text('True'),
          ),
          const SizedBox(width: 20), // Add some spacing between the buttons
          ElevatedButton(
            onPressed: () {
              // Handle False button press
            },
            child: const Text('False'),
          ),
        ],
      ),
    );
  }
}
