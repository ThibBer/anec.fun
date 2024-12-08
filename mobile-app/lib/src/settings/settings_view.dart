import 'package:anecdotfun/src/core/services/page_routing.dart';
import 'package:anecdotfun/src/core/utils/disconnection_overlay.dart';
import 'package:anecdotfun/src/features/connection/connection_page.dart';
import 'package:flutter/material.dart';

import 'settings_controller.dart';

class SettingsView extends StatefulWidget {
  const SettingsView({super.key, required this.controller});

  static const routeName = '/settings';

  final SettingsController controller;

  @override
  _SettingsViewState createState() => _SettingsViewState();
}

class _SettingsViewState extends State<SettingsView> {
  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Scaffold(
          appBar: AppBar(
            title: const Text(
              'Settings',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            centerTitle: true,
          ),
          body: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                const SizedBox(height: 16),
                Text(
                  'Choose Theme',
                  style: Theme.of(context)
                      .textTheme
                      .bodyLarge!
                      .copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 12),
                Container(
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.grey.shade300, width: 1),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: DropdownButton<ThemeMode>(
                    value: widget.controller.themeMode,
                    onChanged: widget.controller.updateThemeMode,
                    isExpanded: true,
                    underline: const SizedBox(),
                    items: const [
                      DropdownMenuItem(
                        value: ThemeMode.system,
                        child: Text('System Theme'),
                      ),
                      DropdownMenuItem(
                        value: ThemeMode.light,
                        child: Text('Light Theme'),
                      ),
                      DropdownMenuItem(
                        value: ThemeMode.dark,
                        child: Text('Dark Theme'),
                      )
                    ],
                  ),
                ),
                if (GlobalNavigationService.currentRoute !=
                    ConnectionPage.routeName)
                  Column(children: [
                    const SizedBox(height: 24),
                    ElevatedButton.icon(
                      onPressed: widget.controller.disconnect,
                      icon: const Icon(
                        Icons.logout,
                        color: Colors.white,
                      ),
                      label: const Text(
                        "Disconnect",
                        style: TextStyle(fontSize: 16, color: Colors.white),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.redAccent,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 24,
                          vertical: 12,
                        ),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(20),
                        ),
                      ),
                    ),
                  ]),
                const Spacer(),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Text(
                      'Developed by:',
                      style: Theme.of(context)
                          .textTheme
                          .bodyLarge
                          ?.copyWith(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'François Bechet',
                      style: TextStyle(fontSize: 14),
                    ),
                    const Text(
                      'Thibaut Berg',
                      style: TextStyle(fontSize: 14),
                    ),
                    const Text(
                      'Victor Santelé',
                      style: TextStyle(fontSize: 14),
                    ),
                    const SizedBox(height: 16),
                  ],
                ),
              ],
            ),
          ),
        ),
        disconnectionOverlay(widget.controller),
      ],
    );
  }
}
