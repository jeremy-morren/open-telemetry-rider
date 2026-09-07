<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Open Telemetry debug logs viewer Changelog

## [Unreleased]

- Telemetry is now scoped to the debug session that produced it, so debugging several projects at once
  no longer mixes their telemetry into every tab.
- Clearing the log is now permanent: telemetry is no longer replayed into the next debug session.
- Added a context menu on HTTP requests and dependencies to copy them as a curl command (bash or cmd)
  or as a JetBrains HTTP client request.
- Added an Event telemetry type for Azure Monitor custom events (logs carrying
  `microsoft.custom_event.name`).
- Added a "Pop out SQL" context menu action that opens a database dependency's query in a scratch file,
  with the connection details from its tags in a comment header.
- Captured HTTP headers (`http.request.header.*`) are now listed under Tags; array valued tags used to
  be skipped entirely.
- Added a flush frequency setting, and a setting for whether copied curl commands end with
  `--compressed`.
- Telemetry type filters are now remembered per run configuration, so hiding metrics for one service
  does not hide them for another.
- Fixed the search box not filtering while typing. Searching now also matches the row text as shown,
  so multi-line SQL matches the single line the table displays.
- Fixed newly received telemetry being inserted in the wrong place when sorting by duration or timestamp.
- Added third party notices for the libraries bundled with the plugin.

## [1.0.5] - 2026-06-02

- Fixed bug relating to shared file across projects
- Fixed `myUI is null` bug
- Fixed shared project telemetry state and erase not working

## [1.0.4] - 2026-04-23

- Upgraded to Rider `2026.1` (build version `261.*`)
- Converted to HTTP server instead of Debug Console
- Updated Exception tab with highlighting same as unit test output

## [1.0.2] - 2025-08-04

- Fixed HTTP requests missing `?` between path and query parameters.
- Right-clicking on a value now copies the value to the clipboard.

## [1.0.1] - 2025-05-30

### Added

- Initial release of the Open Telemetry debug logs viewer for Rider plugin.
- Support metrics, activities and logs viewing.

[Unreleased]: https://github.com/jeremy-morren/open-telemetry-rider/compare/1.0.5...HEAD
[1.0.5]: https://github.com/jeremy-morren/open-telemetry-rider/compare/1.0.4...1.0.5
[1.0.4]: https://github.com/jeremy-morren/open-telemetry-rider/compare/1.0.2...1.0.4
[1.0.2]: https://github.com/jeremy-morren/open-telemetry-rider/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/jeremy-morren/open-telemetry-rider/commits/1.0.1
