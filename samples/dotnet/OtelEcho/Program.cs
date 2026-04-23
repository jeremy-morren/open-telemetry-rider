using System.Collections;
using System.Diagnostics;
using Npgsql;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Trace;
using Serilog;
using OtelEcho;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddHostedService<TelemetryLoop>();

builder.Logging.ClearProviders();
builder.Logging.AddOpenTelemetry();

var otelEnvVars = Environment.GetEnvironmentVariables()
    .Cast<DictionaryEntry>()
    .Where(e => e.Key is string s && s.Contains("OTEL", StringComparison.OrdinalIgnoreCase))
    .ToList();
if (otelEnvVars.Count == 0)
{
    Console.WriteLine("No OTEL env vars found");
}
else
{
    foreach (var e in otelEnvVars)
        Console.WriteLine($"{e.Key}: {e.Value}");
}

builder.Services.AddOpenTelemetry()
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
        .AddHttpClientInstrumentation(o => o.RecordException = true)
        .AddAspNetCoreInstrumentation(o => o.RecordException = true)
        .AddNpgsql())

    .WithMetrics(b => b
        .AddNpgsqlInstrumentation()
        .AddAspNetCoreInstrumentation()
        .AddHttpClientInstrumentation()
        .AddSqlClientInstrumentation()
        .AddProcessInstrumentation());

builder.Services.AddControllers();

var app = builder.Build();

app.UseSerilogRequestLogging();

app.MapControllers();

app.Run();
