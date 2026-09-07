using OtelEcho.Shared;

// Does not capture HTTP request headers, so its copied curl / HTTP request commands carry the URL and
// method only - which is what most real instrumentation produces out of the box.
await OtelEchoApp.RunAsync(args, captureHttpHeaders: false);
