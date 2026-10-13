# OtelEcho sample

Two ASP.NET Core apps that emit traces, metrics and logs, used to exercise the plugin.

Everything they do lives in **`OtelEcho.Shared`** — the OpenTelemetry setup, the background telemetry
loop (SQL Server, PostgreSQL, outgoing HTTP, a custom activity source) and the controller. Each app is
a three line `Program.cs` over that library:

| Project                | Port | HTTP header capture |
| ---------------------- | ---- | ------------------- |
| `OtelEcho.WithHeaders` | 5119 | yes                 |
| `OtelEcho.NoHeaders`   | 5219 | no                  |

Two apps rather than one because the plugin scopes telemetry to the debug session that produced it:
running both — separately or together — must give two tabs that never show each other's telemetry.

Header capture is written by hand in `OtelEchoApp.CaptureHeaders`, because OpenTelemetry .NET has no
built-in support for it ([opentelemetry-dotnet-contrib#1696][issue]). It sets the attributes the
semantic conventions define, `http.request.header.<lowercased name>`, with array values. One of the
captured headers deliberately contains a quote and a bang, which is what forces `bash` into ANSI-C
quoting when a request is copied as a curl command.

[issue]: https://github.com/open-telemetry/opentelemetry-dotnet-contrib/issues/1696
