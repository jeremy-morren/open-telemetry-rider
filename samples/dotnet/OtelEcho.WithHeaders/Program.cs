using OtelEcho.Shared;

// Captures HTTP request headers, so its spans carry http.request.header.* attributes and the copied
// curl / HTTP request commands include them.
await OtelEchoApp.RunAsync(args, captureHttpHeaders: true);
