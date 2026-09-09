package jeremymorren.opentelemetry.tests;

import jeremymorren.opentelemetry.util.RawJsonFormatter;
import org.junit.Assert;
import org.junit.Test;

public class RawJsonFormatterTests {
    @Test
    public void objectWithOneScalarPropertyStaysOnOneLine() {
        Assert.assertEquals(
                "{ \"stringValue\": \"POST\" }",
                RawJsonFormatter.format("{\"stringValue\":\"POST\"}"));
    }

    @Test
    public void objectWithSeveralPropertiesIsSpreadOverLines() {
        Assert.assertEquals(
                """
                        {
                          "key": "http.request.method",
                          "keyStrindex": 0
                        }""",
                RawJsonFormatter.format("{\"key\":\"http.request.method\",\"keyStrindex\":0}"));
    }

    @Test
    public void objectWrappingOneObjectIsStillSpreadOverLines() {
        // Only scalars collapse; a nested object needs its own lines to stay readable.
        Assert.assertEquals(
                """
                        {
                          "value": { "intValue": "443" }
                        }""",
                RawJsonFormatter.format("{\"value\":{\"intValue\":\"443\"}}"));
    }

    @Test
    public void arrayElementsEachGetTheirOwnLine() {
        // The span attributes of an OTLP trace, which is what the format was chosen for.
        Assert.assertEquals(
                """
                        {
                          "attributes": [
                            {
                              "key": "http.request.method",
                              "value": { "stringValue": "POST" },
                              "keyStrindex": 0
                            },
                            {
                              "key": "server.port",
                              "value": { "intValue": "443" },
                              "keyStrindex": 0
                            }
                          ]
                        }""",
                RawJsonFormatter.format(
                        "{\"attributes\":[{\"key\":\"http.request.method\",\"value\":{\"stringValue\":\"POST\"},"
                                + "\"keyStrindex\":0},{\"key\":\"server.port\",\"value\":{\"intValue\":\"443\"},"
                                + "\"keyStrindex\":0}]}"));
    }

    @Test
    public void arraysOfScalarsAlsoGetALinePerElement() {
        Assert.assertEquals(
                """
                        [
                          1,
                          2
                        ]""",
                RawJsonFormatter.format("[1,2]"));
    }

    @Test
    public void emptyContainersStayInline() {
        Assert.assertEquals(
                """
                        {
                          "events": [],
                          "links": {}
                        }""",
                RawJsonFormatter.format("{\"events\":[],\"links\":{}}"));
    }

    @Test
    public void scalarsKeepTheirJsonTypes() {
        Assert.assertEquals(
                """
                        {
                          "flags": 257,
                          "traceState": "",
                          "sampled": true,
                          "parent": null
                        }""",
                RawJsonFormatter.format("{\"flags\":257,\"traceState\":\"\",\"sampled\":true,\"parent\":null}"));
    }

    @Test
    public void unreadableInputIsReturnedUnchanged() {
        Assert.assertEquals("not json at all", RawJsonFormatter.format("not json at all"));
    }
}
