# Open Telemetry debug logs viewer for Rider

![Build](https://github.com/jeremy-morren/open-telemetry-rider/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/26584.svg)](https://plugins.jetbrains.com/plugin/26584)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/26584.svg)](https://plugins.jetbrains.com/plugin/26584)

<!-- Plugin description -->
Open Telemetry debug logs viewer for Rider

View Open telemetry output instantly within JetBrains Rider.

Usage: Enable open telemetry JSON debug export and start a debug session.
Logs will automatically appear in a new tab. 

See [OpenTelemetry JSON Console Exporter](https://github.com/jeremy-morren/opentelemetry-json-console-exporter)
to learn how to enable OpenTelemetry JSON debug export in your application.
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
