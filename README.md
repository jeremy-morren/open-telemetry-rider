# Open Telemetry debug viewer for Rider

![Build](https://github.com/jeremy-morren/open-telemetry-rider/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/26584.svg)](https://plugins.jetbrains.com/plugin/26584)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/26584.svg)](https://plugins.jetbrains.com/plugin/26584)

<!-- Plugin description -->
Open Telemetry debug viewer for Rider

View Open telemetry output instantly within JetBrains Rider. Supports Metrics, Traces, Logs.

Usage: Start a debug session with OpenTelemetry OTLP export enabled.
The plugin starts a loopback OTLP/HTTP receiver on `127.0.0.1` and telemetry will automatically appear in a new tab.

Traces are parsed as Dependencies where format is known e.g. HTTP & SQL

NB: This plugin is incompatible with the built-in Rider OpenTelemetry plugin, 
because it overrides the necessary environment variables.

<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Open Telemetry debug logs viewer"</kbd> >
  <kbd>Install</kbd>
  
- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/26584) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/26584/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/jeremy-morren/open-telemetry-rider/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---

## Building

Run the following command to build and verify the plugin:

```shell
$ ./gradlew :verifyPlugin
```

## Contributing

If you want to contribute to this project, feel free to open an issue or a pull request on GitHub.

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
