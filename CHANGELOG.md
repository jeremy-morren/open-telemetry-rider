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
- Added a "Pop out SQL" context menu action that opens a database dependency's query in a scratch file
  named after the database system and the statement, with the connection details from its tags in a
  comment header.
- Captured HTTP headers (`http.request.header.*`) are now listed under Tags; array valued tags used to
  be skipped entirely.
- Added a flush interval setting, a separate metrics flush interval (seconds, default 60, available to
  the environment variable template as `${OTLP_METRICS_FLUSH_INTERVAL}`), and a setting for whether
  copied curl commands end with `--compressed`.
- Tidied the global settings page: it is no longer forced ridiculously wide by its own labels, and the
  environment variables are edited in JetBrains Mono rather than the Swing default of Courier.
- The loopback OTLP receiver now listens on `127.0.0.2` rather than `127.0.0.1`, keeping it clear of
  whatever the debugged application binds. macOS assigns only `127.0.0.1` to its loopback interface,
  so the receiver stays there on that platform.
- Moved the Raw tab last, after Formatted, SQL and Exception, and reformatted the JSON it shows: an
  object wrapping a single scalar now stays on one line, and array elements each get a line of their
  own, instead of protobuf's much taller layout.
- Case insensitive search is now a toggle in the options menu next to the sort modes. It used to be
  duplicated as a per-project setting with a settings page of its own, which the filter never actually
  read; that page is gone, and the options menu now opens the settings that remain.
- Reworked the Formatted tab: a summary header, collapsible sections, aligned keys, selectable values
  with copy/filter buttons on hover, and a timing breakdown bar for database dependencies. Sections are
  laid out in columns when the pane is wide enough for them. Rows are one line tall rather than the
  height of a form control, so a lot more fits on screen.
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
