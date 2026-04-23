using System.Diagnostics;
using Microsoft.Data.SqlClient;
using Npgsql;

// ReSharper disable EmptyGeneralCatchClause

namespace OtelEcho;

public class TelemetryLoop(ILogger<TelemetryLoop> logger) : BackgroundService
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
                await Task.Delay(5000, stoppingToken);
            }
        }
        catch (OperationCanceledException e) when (e.CancellationToken == stoppingToken)
        {
            // Ignore
        }
    }

    public static readonly ActivitySource Source = new ("Sample.DistributedTracing");

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
            
            await HttpClient.GetAsync("http://localhost:5119/swagger");
            await HttpClient.GetAsync("http://localhost:5119/api/WeatherForecast/Random");
            await HttpClient.GetAsync("http://localhost:5119/api/WeatherForecast/ForCity/Kingstown?street=Halifax");
            await HttpClient.GetAsync("http://localhost:5119/DoesntExist");
            await HttpClient.GetAsync("http://localhost:1111");
        }
        catch (Exception e)
        {
            logger.LogError(e, "An error occurred sending the request");
        }
    }
    
    private static readonly HttpClient HttpClient = new ();
}