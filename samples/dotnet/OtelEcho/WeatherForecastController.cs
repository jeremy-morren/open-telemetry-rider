using Microsoft.AspNetCore.Mvc;

namespace OtelEcho;

[ApiController, Route("/api/[controller]/[action]")]
public class WeatherForecastController
{
    private static readonly string[] Summaries =
    [
        "Freezing", "Bracing", "Chilly", "Cool", "Mild", "Warm", "Balmy", "Hot", "Sweltering", "Scorching"
    ];
    
    [HttpGet]
    public WeatherForecast[] Random()
    {
        var random = System.Random.Shared;
        var forecast = Enumerable.Range(1, 5)
            .Select(index =>
                new WeatherForecast
                (
                    DateOnly.FromDateTime(DateTime.Now.AddDays(index)),
                    random.Next(-20, 55),
                    Summaries[random.Next(Summaries.Length)]
                ))
            .ToArray();
        return forecast;
    }

    [HttpGet("{city}")]
    public WeatherForecast[] ForCity(string city) => Random();

    public record WeatherForecast(DateOnly Date, int TemperatureC, string? Summary)
    {
        public int TemperatureF => 32 + (int)(TemperatureC / 0.5556);
    }
}