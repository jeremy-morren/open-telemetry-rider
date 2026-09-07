package jeremymorren.opentelemetry.tests;

import jeremymorren.opentelemetry.models.Activity;
import jeremymorren.opentelemetry.models.ActivityKind;
import jeremymorren.opentelemetry.models.LogLevel;
import jeremymorren.opentelemetry.models.LogMessage;
import jeremymorren.opentelemetry.models.ObjectDictionary;
import jeremymorren.opentelemetry.models.Telemetry;
import jeremymorren.opentelemetry.models.TelemetryType;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonObject;
import org.junit.Assert;
import org.junit.Test;

public class TelemetryDisplayTextTests {
    @Test
    public void multiLineSqlIsFlattenedOntoOneLine() {
        Activity activity = sqlActivity("select *\r\nfrom \"pg_tables\"");

        // What the table shows - and so what a search has to match.
        Assert.assertEquals("SQL - select * from \"pg_tables\"", activity.getDetailFull());
        Assert.assertTrue(new Telemetry(activity, null, null, null).getDisplayText()
                .contains("select * from \"pg_tables\""));
    }

    @Test
    public void displayTextIsNotTruncated() {
        Activity activity = sqlActivity("select " + "a".repeat(300) + " from t");

        Assert.assertTrue(activity.getDetail().endsWith("..."));
        Assert.assertEquals(103, activity.getDetail().length());
        Assert.assertFalse(activity.getDetailFull().endsWith("..."));
        Assert.assertTrue(activity.getDetailFull().contains("from t"));
    }

    @Test
    public void customEventNameMakesTheLogAnEvent() {
        LogMessage log = new LogMessage(
                "checkout completed", "checkout completed", LogLevel.Information, null, null,
                attributes("{ \"microsoft.custom_event.name\": \"Checkout\" }"),
                null, null, null, null, "Checkout");

        Assert.assertEquals(TelemetryType.Event, log.getType());
        Assert.assertEquals("Checkout - checkout completed", log.getDisplayMessage());
        Assert.assertEquals(TelemetryType.Event, new Telemetry(null, null, log, null).getType());
    }

    @Test
    public void logsWithoutACustomEventNameAreStillMessages() {
        LogMessage log = new LogMessage(
                "hello", "hello", LogLevel.Information, null, null, null, null, null, null, null, null);

        Assert.assertEquals(TelemetryType.Message, log.getType());
        Assert.assertEquals("[INF] hello", log.getDisplayMessage());
    }

    @Test
    public void arrayTagsAreShownInTheDisplayValues() {
        ObjectDictionary tags = attributes("""
                {
                  "http.request.header.accept": ["application/json"],
                  "http.request.header.x-forwarded-for": ["10.0.0.1", "10.0.0.2"],
                  "db.system": "postgresql"
                }
                """);

        // getPrimitiveValues drops arrays, which is what hid captured headers from the Tags section.
        Assert.assertFalse(tags.getPrimitiveValues().containsKey("http.request.header.accept"));
        Assert.assertEquals("application/json", tags.getDisplayValues().get("http.request.header.accept"));
        Assert.assertEquals("10.0.0.1, 10.0.0.2", tags.getDisplayValues().get("http.request.header.x-forwarded-for"));
        Assert.assertEquals("postgresql", tags.getDisplayValues().get("db.system"));
    }

    private static Activity sqlActivity(String sql) {
        ObjectDictionary tags = new ObjectDictionary(JsonObject(
                "{ \"db.system\": \"postgresql\", \"db.query.text\": " + quote(sql) + " }"));
        return new Activity(
                null, null, null, null, null, null, null, null, ActivityKind.Client,
                null, null, tags, null, null, null, null);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private static ObjectDictionary attributes(String json) {
        return new ObjectDictionary(JsonObject(json));
    }

    private static JsonObject JsonObject(String json) {
        return (JsonObject) Json.Default.parseToJsonElement(json);
    }
}
