using System.Collections;
using System.Diagnostics;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Npgsql;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using Serilog;

namespace OtelEcho.Shared;

/// <summary>
/// The whole sample application.
///
/// Both sample projects run this; the only thing they do differently is whether they capture HTTP
/// headers, so that the plugin's "copy as curl / HTTP request" actions can be tried with and without
/// them. Running both at once is also how the plugin's per-debug-session telemetry scoping is tested:
/// each app must only ever show up in its own debug tab.
/// </summary>
public static class OtelEchoApp
{
    /// <summary>
    /// Headers the sample copies onto its spans when header capture is on.
    /// OpenTelemetry .NET has no built-in header capture (opentelemetry-dotnet-contrib#1696), so the
    /// attributes are written by hand, using the names and array values the semantic conventions define.
    /// </summary>
    private static readonly string[] CapturedHeaders = ["accept", "user-agent", "x-sample-note"];

    public static Task RunAsync(string[] args, bool captureHttpHeaders)
    {
        var builder = WebApplication.CreateBuilder(args);

        PrintOtlpEnvironment(builder.Environment.ApplicationName);

        builder.Services.AddHostedService<TelemetryLoop>();

        builder.Logging.ClearProviders();
        builder.Logging.AddOpenTelemetry();

        builder.Services.AddOpenTelemetry()
            // Gives each app its own service.name, so telemetry can be told apart at a glance.
            .ConfigureResource(resource => resource.AddService(builder.Environment.ApplicationName))
            .WithTracing(b => b.AddOtlpExporter())
            .WithMetrics(b => b.AddOtlpExporter())
            .WithLogging(b => b.AddOtlpExporter());

        builder.Host.UseSerilog((context, services, logger) =>
            {
                logger
                    .WriteTo.Console()
                    .ReadFrom.Configuration(context.Configuration)
                    .ReadFrom.Services(services)
                    .Enrich.FromLogContext();
            },
            writeToProviders: true);

        builder.Services.AddOpenTelemetry()
            .WithTracing(b => b
                .AddSource(TelemetryLoop.Source.Name)
                .AddSqlClientInstrumentation(o => o.RecordException = true)
                .AddHttpClientInstrumentation(o =>
                {
                    o.RecordException = true;
                    if (captureHttpHeaders)
                        o.EnrichWithHttpRequestMessage = (activity, request) =>
                            CaptureHeaders(activity, name =>
                                request.Headers.TryGetValues(name, out var values) ? values.ToArray() : null);
                })
                .AddAspNetCoreInstrumentation(o =>
                {
                    o.RecordException = true;
                    if (captureHttpHeaders)
                        o.EnrichWithHttpRequest = (activity, request) =>
                            CaptureHeaders(activity, name =>
                                request.Headers.TryGetValue(name, out var values)
                                    ? values.OfType<string>().ToArray()
                                    : null);
                })
                .AddNpgsql())

            .WithMetrics(b => b
                .AddNpgsqlInstrumentation()
                .AddAspNetCoreInstrumentation()
                .AddHttpClientInstrumentation()
                .AddSqlClientInstrumentation()
                .AddProcessInstrumentation());

        // The controllers live in this shared library rather than in the app projects.
        builder.Services.AddControllers()
            .AddApplicationPart(typeof(WeatherForecastController).Assembly);

        var app = builder.Build();

        app.UseSerilogRequestLogging();

        app.MapControllers();

        return app.RunAsync();
    }

    private static void CaptureHeaders(Activity activity, Func<string, string[]?> readHeader)
    {
        foreach (var name in CapturedHeaders)
        {
            var values = readHeader(name);
            if (values is { Length: > 0 })
                activity.SetTag($"http.request.header.{name}", values);
        }
    }

    /// <summary>
    /// Prints the OTLP endpoint the plugin injected. Each debug session gets its own scope in the URL,
    /// so the two apps should print two different endpoints.
    /// </summary>
    private static void PrintOtlpEnvironment(string applicationName)
    {
        var otelEnvVars = Environment.GetEnvironmentVariables()
            .Cast<DictionaryEntry>()
            .Where(e => e.Key is string s && s.Contains("OTEL", StringComparison.OrdinalIgnoreCase))
            .ToList();

        if (otelEnvVars.Count == 0)
        {
            Console.WriteLine($"[{applicationName}] No OTEL env vars found");
            return;
        }

        foreach (var e in otelEnvVars)
            Console.WriteLine($"[{applicationName}] {e.Key}: {e.Value}");
    }
}
