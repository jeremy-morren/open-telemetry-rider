using System.Diagnostics;
using Microsoft.AspNetCore.Hosting.Server;
using Microsoft.AspNetCore.Hosting.Server.Features;
using Microsoft.Data.SqlClient;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Npgsql;

// ReSharper disable EmptyGeneralCatchClause

namespace OtelEcho.Shared;

public class TelemetryLoop(ILogger<TelemetryLoop> logger, IServer server) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        try
        {
            while (!stoppingToken.IsCancellationRequested)
            {
                await ConnectToSqlServer();
                await ConnectToPg();
                await DoSomeWork();
                await HttpRequest();
                EmitCustomEvent();
                await Task.Delay(5000, stoppingToken);
            }
        }
        catch (OperationCanceledException e) when (e.CancellationToken == stoppingToken)
        {
            // Ignore
        }
    }

    public static readonly ActivitySource Source = new ("Sample.DistributedTracing");

    /// <summary>
    /// The address this app is actually listening on, so each sample calls itself rather than the other one.
    /// </summary>
    private string? SelfBaseUrl =>
        server.Features.Get<IServerAddressesFeature>()?.Addresses.FirstOrDefault()?.TrimEnd('/');

    // All the functions below simulate doing some arbitrary work
    private static async Task DoSomeWork()
    {
        using var activity = Source.StartActivity();
        await StepOne();
        await StepTwo();
    }

    private static async Task StepOne()
    {
        await Task.Delay(500);
    }

    private static async Task StepTwo()
    {
        await Task.Delay(1000);
    }

    private async Task ConnectToSqlServer()
    {
        try
        {
            const string connString = "Server=(localdb)\\MSSQLLocalDB;Integrated Security=true";
            await using var conn = new SqlConnection(connString);
            if (conn.State != System.Data.ConnectionState.Open)
                await conn.OpenAsync();
            await using var cmd = conn.CreateCommand();
            cmd.CommandText = "select top 10 * from INFORMATION_SCHEMA.TABLES";
            await using (var reader = await cmd.ExecuteReaderAsync())
            {
                while (await reader.ReadAsync()) {}
            }

            cmd.CommandText = "select 1 from asdf";
            await cmd.ExecuteScalarAsync();
        }
        catch (Exception e)
        {
            logger.LogCritical(e, "Error while connecting to SQL Server");
        }
    }

    private static async Task ConnectToPg()
    {
        try
        {
            const string connString = "Host=localhost;Port=5432;Username=postgres;Password=postgres;Database=postgres;";

            var dataSource = NpgsqlDataSource.Create(connString);
            await using (var cmd = dataSource.CreateCommand())
            {
                cmd.CommandText = "select * \r\nfrom \"pg_tables\"";
                await using var reader = await cmd.ExecuteReaderAsync();
                while (await reader.ReadAsync()) { }
            }
            await using (var cmd = dataSource.CreateCommand())
            {
                cmd.CommandText = "select * \r\nfrom \"11\"";
                await using var reader = await cmd.ExecuteReaderAsync();
                while (await reader.ReadAsync()) { }
            }
        }
        catch (Exception)
        {
        }
    }

    private async Task HttpRequest()
    {
        using var _ = logger.BeginScope(new Dictionary<string, object>()
        {
            ["ScopeKey"] = "A Scope",
        });
        try
        {
            using var response = await HttpClient.GetAsync("https://github.com/");

            logger.LogInformation("HTTP {Method} {Url} returned {@StatusCode}",
                response.RequestMessage?.Method,
                response.RequestMessage?.RequestUri,
                response.StatusCode);

            var self = SelfBaseUrl;
            if (self != null)
            {
                await HttpClient.GetAsync($"{self}/api/WeatherForecast/Random");
                await HttpClient.GetAsync($"{self}/api/WeatherForecast/ForCity/Kingstown?street=Halifax");
                await HttpClient.GetAsync($"{self}/DoesntExist");
            }

            await HttpClient.GetAsync("http://localhost:1111");
        }
        catch (Exception e)
        {
            logger.LogError(e, "An error occurred sending the request");
        }
    }

    /// <summary>
    /// Emits an Azure Monitor custom event.
    /// </summary>
    /// <remarks>
    /// A custom event is an ordinary log record carrying the <c>microsoft.custom_event.name</c>
    /// attribute; putting that name in the message template is all it takes. The Azure Monitor exporter
    /// turns such records into rows in the <c>customEvents</c> table, and no Azure Monitor package is
    /// needed to produce one - which is why this sample has none, and nothing to upload.
    /// See https://learn.microsoft.com/azure/azure-monitor/app/opentelemetry-add-modify
    /// </remarks>
    private void EmitCustomEvent()
    {
        _checkouts++;
#pragma warning disable CA2254 // The template is the point: its holes become the event's properties.
        logger.LogInformation(
            "{microsoft.custom_event.name} {CheckoutNumber} {Basket}",
            "Checkout", _checkouts, "2 items");
#pragma warning restore CA2254
    }

    private int _checkouts;

    private static readonly HttpClient HttpClient = CreateHttpClient();

    private static HttpClient CreateHttpClient()
    {
        var client = new HttpClient();
        // Headers worth seeing in a copied curl command. The note deliberately contains a quote and a
        // bang, which are the characters that force bash into ANSI-C quoting.
        client.DefaultRequestHeaders.Add("Accept", "application/json");
        client.DefaultRequestHeaders.Add("User-Agent", "OtelEcho/1.0");
        client.DefaultRequestHeaders.Add("X-Sample-Note", "it's a test!");
        return client;
    }
}
