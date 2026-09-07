package jeremymorren.opentelemetry.tests;

import jeremymorren.opentelemetry.http.HttpTelemetryRequest;
import jeremymorren.opentelemetry.models.Activity;
import jeremymorren.opentelemetry.models.ActivityKind;
import jeremymorren.opentelemetry.models.ObjectDictionary;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonObject;
import org.junit.Assert;
import org.junit.Test;

public class HttpTelemetryRequestTests {
    @Test
    public void readsClientSpanFromUrlFull() {
        HttpTelemetryRequest request = from("""
                {
                  "http.request.method": "GET",
                  "url.full": "https://example.com/weather?city=london"
                }
                """);

        Assert.assertNotNull(request);
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals("https://example.com/weather?city=london", request.getUrl());
        Assert.assertTrue(request.getHeaders().isEmpty());
    }

    @Test
    public void buildsServerSpanUrlFromItsParts() {
        HttpTelemetryRequest request = from("""
                {
                  "http.request.method": "POST",
                  "url.scheme": "https",
                  "server.address": "example.com",
                  "server.port": 8443,
                  "url.path": "/weather",
                  "url.query": "city=london"
                }
                """);

        Assert.assertNotNull(request);
        Assert.assertEquals("https://example.com:8443/weather?city=london", request.getUrl());
    }

    @Test
    public void omitsTheDefaultPortOfTheScheme() {
        HttpTelemetryRequest request = from("""
                {
                  "http.request.method": "GET",
                  "url.scheme": "https",
                  "server.address": "example.com",
                  "server.port": 443,
                  "url.path": "/weather"
                }
                """);

        Assert.assertNotNull(request);
        Assert.assertEquals("https://example.com/weather", request.getUrl());
    }

    @Test
    public void readsPreStableAttributeNames() {
        HttpTelemetryRequest request = from("""
                {
                  "http.method": "DELETE",
                  "http.url": "http://example.com/weather/1"
                }
                """);

        Assert.assertNotNull(request);
        Assert.assertEquals("DELETE", request.getMethod());
        Assert.assertEquals("http://example.com/weather/1", request.getUrl());
    }

    @Test
    public void isNullForNonHttpSpans() {
        Assert.assertNull(from("{ \"db.system\": \"postgresql\" }"));
        Assert.assertNull(HttpTelemetryRequest.from(null));
        Assert.assertNull(HttpTelemetryRequest.from(new Activity(
                null, null, null, null, null, null, null, null, ActivityKind.Client,
                null, null, null, null, null, null, null)));
    }

    @Test
    public void readsCapturedRequestHeaders() {
        HttpTelemetryRequest request = from("""
                {
                  "http.request.method": "GET",
                  "url.full": "https://example.com/weather",
                  "http.request.header.accept": ["application/json"],
                  "http.request.header.x-forwarded-for": ["10.0.0.1", "10.0.0.2"],
                  "http.response.header.content-type": ["application/json"]
                }
                """);

        Assert.assertNotNull(request);
        Assert.assertEquals(2, request.getHeaders().size());
        Assert.assertEquals("accept", request.getHeaders().get(0).getFirst());
        Assert.assertEquals("application/json", request.getHeaders().get(0).getSecond());
        Assert.assertEquals("x-forwarded-for", request.getHeaders().get(1).getFirst());
        Assert.assertEquals("10.0.0.1, 10.0.0.2", request.getHeaders().get(1).getSecond());
    }

    @Test
    public void curlBashMatchesChromeOutput() {
        HttpTelemetryRequest request = from("""
                {
                  "http.request.method": "POST",
                  "url.full": "https://example.com/weather",
                  "http.request.header.accept": ["application/json"]
                }
                """);

        Assert.assertNotNull(request);
        Assert.assertEquals(
                "curl 'https://example.com/weather' \\\n"
                        + "  -X 'POST' \\\n"
                        + "  -H 'accept: application/json' \\\n"
                        + "  --compressed",
                request.toCurlBash(true));
    }

    @Test
    public void curlBashKeepsShortCommandsOnOneLine() {
        HttpTelemetryRequest request = from("""
                { "http.request.method": "GET", "url.full": "https://example.com/weather" }
                """);

        Assert.assertNotNull(request);
        Assert.assertEquals("curl 'https://example.com/weather' --compressed", request.toCurlBash(true));
    }

    @Test
    public void curlOmitsCompressedWhenTheSettingIsOff() {
        HttpTelemetryRequest request = from("""
                {
                  "http.request.method": "POST",
                  "url.full": "https://example.com/weather",
                  "http.request.header.accept": ["application/json"]
                }
                """);

        Assert.assertNotNull(request);
        Assert.assertEquals(
                "curl 'https://example.com/weather' \\\n"
                        + "  -X 'POST' \\\n"
                        + "  -H 'accept: application/json'",
                request.toCurlBash(false));
        Assert.assertEquals(
                "curl \"https://example.com/weather\" ^\r\n"
                        + "  -X \"POST\" ^\r\n"
                        + "  -H \"accept: application/json\"",
                request.toCurlCmd(false));
    }

    @Test
    public void curlBashUsesAnsiCQuotingForQuotesAndBangs() {
        HttpTelemetryRequest request = from("""
                {
                  "http.request.method": "GET",
                  "url.full": "https://example.com/weather",
                  "http.request.header.x-note": ["it's here!"]
                }
                """);

        Assert.assertNotNull(request);
        Assert.assertTrue(request.toCurlBash(true).contains("-H $'x-note: it\\'s here\\x21'"));
    }

    @Test
    public void curlBashEscapesControlCharacters() {
        HttpTelemetryRequest request = from("""
                {
                  "http.request.method": "GET",
                  "url.full": "https://example.com/weather",
                  "http.request.header.x-note": ["bell:\\u0007 tab:\\t"]
                }
                """);

        Assert.assertNotNull(request);
        // Chrome writes code points below 0x10 padded out to four hex digits, and the rest as \xNN.
        Assert.assertTrue(request.toCurlBash(true).contains("bell:" + "\\" + "u0007 tab:" + "\\" + "u0009"));
    }

    @Test
    public void curlBashEscapesBracesInTheUrl() {
        HttpTelemetryRequest request = from("""
                { "http.request.method": "GET", "url.full": "https://example.com/weather/{id}" }
                """);

        Assert.assertNotNull(request);
        Assert.assertEquals("curl 'https://example.com/weather/\\{id\\}' --compressed", request.toCurlBash(true));
    }

    @Test
    public void curlCmdMatchesChromeOutput() {
        HttpTelemetryRequest request = from("""
                {
                  "http.request.method": "POST",
                  "url.full": "https://example.com/weather",
                  "http.request.header.accept": ["application/json"]
                }
                """);

        Assert.assertNotNull(request);
        Assert.assertEquals(
                "curl \"https://example.com/weather\" ^\r\n"
                        + "  -X \"POST\" ^\r\n"
                        + "  -H \"accept: application/json\" ^\r\n"
                        + "  --compressed",
                request.toCurlCmd(true));
    }

    @Test
    public void curlCmdNeutralisesPercentSigns() {
        HttpTelemetryRequest request = from("""
                { "http.request.method": "GET", "url.full": "https://example.com/weather?q=100%25" }
                """);

        Assert.assertNotNull(request);
        Assert.assertEquals("curl \"https://example.com/weather?q=100\"%\"25\" --compressed", request.toCurlCmd(true));
    }

    @Test
    public void httpRequestUsesTheJetBrainsHttpClientFormat() {
        HttpTelemetryRequest request = from("""
                {
                  "http.request.method": "POST",
                  "url.full": "https://example.com/weather",
                  "http.request.header.accept": ["application/json"],
                  "http.request.header.content-type": ["application/json"]
                }
                """);

        Assert.assertNotNull(request);
        String newLine = System.lineSeparator();
        Assert.assertEquals(
                "### POST https://example.com/weather" + newLine
                        + "POST https://example.com/weather" + newLine
                        + "accept: application/json" + newLine
                        + "content-type: application/json" + newLine,
                request.toHttpRequest());
    }

    private static HttpTelemetryRequest from(String tagsJson) {
        ObjectDictionary tags = new ObjectDictionary((JsonObject) Json.Default.parseToJsonElement(tagsJson));
        Activity activity = new Activity(
                null, null, null, null, null, null, null, null, ActivityKind.Client,
                null, null, tags, null, null, null, null);
        return HttpTelemetryRequest.from(activity);
    }
}
