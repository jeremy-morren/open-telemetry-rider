package jeremymorren.opentelemetry.tests;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import io.opentelemetry.proto.resource.v1.Resource;
import jeremymorren.opentelemetry.models.LogMessage;
import jeremymorren.opentelemetry.models.TelemetryItem;
import jeremymorren.opentelemetry.otlp.OtlpTelemetryDecoder;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for Microsoft.Extensions.Logging / Serilog-compatible log message template formatting.
 *
 * <p>The formatting convention:
 * <ul>
 *   <li>The log body holds the message template.</li>
 *   <li>Each {@code {PropertyName}} placeholder (optionally prefixed with {@code @} or {@code $},
 *       optionally followed by {@code :formatSpec}) is replaced by the matching attribute value.</li>
 *   <li>When no matching attribute exists for a placeholder, the placeholder is kept as-is.</li>
 * </ul>
 */
public class LogMessageFormattingTests {

    private final OtlpTelemetryDecoder decoder = new OtlpTelemetryDecoder();

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private LogMessage decodeLog(String body, KeyValue... attributes) {
        LogRecord.Builder builder = LogRecord.newBuilder()
                .setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_INFO);
        if (body != null) {
            builder.setBody(AnyValue.newBuilder().setStringValue(body));
        }
        for (KeyValue attr : attributes) {
            builder.addAttributes(attr);
        }

        ExportLogsServiceRequest request = ExportLogsServiceRequest.newBuilder()
                .addResourceLogs(ResourceLogs.newBuilder()
                        .setResource(Resource.newBuilder())
                        .addScopeLogs(ScopeLogs.newBuilder()
                                .setScope(InstrumentationScope.newBuilder().setName("test"))
                                .addLogRecords(builder.build())))
                .build();

        List<TelemetryItem> items = decoder.decodeLogs(request.toByteArray());
        assertEquals(1, items.size());
        assertNotNull(items.get(0).getTelemetry().getLog());
        return items.get(0).getTelemetry().getLog();
    }

    private static KeyValue str(String key, String value) {
        return KeyValue.newBuilder().setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }

    private static KeyValue intAttr(String key, long value) {
        return KeyValue.newBuilder().setKey(key)
                .setValue(AnyValue.newBuilder().setIntValue(value)).build();
    }

    private static KeyValue dblAttr(String key, double value) {
        return KeyValue.newBuilder().setKey(key)
                .setValue(AnyValue.newBuilder().setDoubleValue(value)).build();
    }

    private static KeyValue boolAttr(String key, boolean value) {
        return KeyValue.newBuilder().setKey(key)
                .setValue(AnyValue.newBuilder().setBoolValue(value)).build();
    }

    // -----------------------------------------------------------------------
    // 1. No placeholders – body returned as-is
    // -----------------------------------------------------------------------

    @Test
    public void noPlaceholders_returnsBodyAsIs() {
        LogMessage log = decodeLog("Plain message with no template");
        assertEquals("Plain message with no template", log.getFormattedMessage());
    }

    @Test
    public void noPlaceholders_withAttributes_returnsBodyAsIs() {
        LogMessage log = decodeLog("Static message", str("SomeKey", "value"));
        assertEquals("Static message", log.getFormattedMessage());
    }

    @Test
    public void usesBodyAsTemplate_evenWhenOriginalFormatIsDifferent() {
        LogMessage log = decodeLog("Hello {Name}",
                str("{OriginalFormat}", "THIS SHOULD BE IGNORED"),
                str("Name", "World"));
        assertEquals("Hello World", log.getFormattedMessage());
    }

    @Test
    public void nullBody_noAttributes_returnsNull() {
        LogMessage log = decodeLog(null);
        assertNull(log.getFormattedMessage());
    }

    @Test
    public void emptyBody_noTemplate_returnsEmpty() {
        LogMessage log = decodeLog("");
        assertEquals("", log.getFormattedMessage());
    }

    // -----------------------------------------------------------------------
    // 2. Simple named placeholder
    // -----------------------------------------------------------------------

    @Test
    public void singleNamedPlaceholder() {
        LogMessage log = decodeLog("Hello {Name}",
                str("{OriginalFormat}", "Hello {Name}"),
                str("Name", "World"));
        assertEquals("Hello World", log.getFormattedMessage());
    }

    @Test
    public void multiplePlaceholders() {
        LogMessage log = decodeLog("User {UserId} performed {Action}",
                str("{OriginalFormat}", "User {UserId} performed {Action}"),
                str("UserId", "42"),
                str("Action", "login"));
        assertEquals("User 42 performed login", log.getFormattedMessage());
    }

    @Test
    public void placeholderAtStart() {
        LogMessage log = decodeLog("{Name} logged in",
                str("{OriginalFormat}", "{Name} logged in"),
                str("Name", "Alice"));
        assertEquals("Alice logged in", log.getFormattedMessage());
    }

    @Test
    public void placeholderAtEnd() {
        LogMessage log = decodeLog("Hello {Name}",
                str("{OriginalFormat}", "Hello {Name}"),
                str("Name", "Bob"));
        assertEquals("Hello Bob", log.getFormattedMessage());
    }

    @Test
    public void onlyPlaceholder() {
        LogMessage log = decodeLog("{Message}",
                str("{OriginalFormat}", "{Message}"),
                str("Message", "just this"));
        assertEquals("just this", log.getFormattedMessage());
    }

    // -----------------------------------------------------------------------
    // 3. Non-string attribute types
    // -----------------------------------------------------------------------

    @Test
    public void integerAttribute() {
        LogMessage log = decodeLog("Count is {Count}",
                str("{OriginalFormat}", "Count is {Count}"),
                intAttr("Count", 99));
        assertEquals("Count is 99", log.getFormattedMessage());
    }

    @Test
    public void doubleAttribute() {
        LogMessage log = decodeLog("Value is {Value}",
                str("{OriginalFormat}", "Value is {Value}"),
                dblAttr("Value", 3.14));
        assertEquals("Value is 3.14", log.getFormattedMessage());
    }

    @Test
    public void booleanAttribute_true() {
        LogMessage log = decodeLog("Enabled: {Enabled}",
                str("{OriginalFormat}", "Enabled: {Enabled}"),
                boolAttr("Enabled", true));
        assertEquals("Enabled: true", log.getFormattedMessage());
    }

    @Test
    public void booleanAttribute_false() {
        LogMessage log = decodeLog("Enabled: {Enabled}",
                str("{OriginalFormat}", "Enabled: {Enabled}"),
                boolAttr("Enabled", false));
        assertEquals("Enabled: false", log.getFormattedMessage());
    }

    // -----------------------------------------------------------------------
    // 4. Format specifiers (`:format`) – value substituted, specifier ignored
    // -----------------------------------------------------------------------

    @Test
    public void formatSpecifier_numericFormat() {
        LogMessage log = decodeLog("Duration: {Duration:0.00}ms",
                str("{OriginalFormat}", "Duration: {Duration:0.00}ms"),
                dblAttr("Duration", 1.5));
        assertEquals("Duration: 1.5ms", log.getFormattedMessage());
    }

    @Test
    public void formatSpecifier_dateFormat() {
        LogMessage log = decodeLog("At {Time:HH:mm:ss}",
                str("{OriginalFormat}", "At {Time:HH:mm:ss}"),
                str("Time", "13:45:00"));
        assertEquals("At 13:45:00", log.getFormattedMessage());
    }

    @Test
    public void formatSpecifier_withColonInSpec() {
        // format spec itself contains a colon, e.g. {Value:yyyy-MM-dd HH:mm}
        LogMessage log = decodeLog("Date: {Date:yyyy-MM-dd HH:mm}",
                str("{OriginalFormat}", "Date: {Date:yyyy-MM-dd HH:mm}"),
                str("Date", "2026-04-22 10:00"));
        assertEquals("Date: 2026-04-22 10:00", log.getFormattedMessage());
    }

    // -----------------------------------------------------------------------
    // 5. Serilog destructuring and stringify operators (@ and $)
    // -----------------------------------------------------------------------

    @Test
    public void serilogDestructuringPrefix_at() {
        // {@Request} – key is "Request"
        LogMessage log = decodeLog("Request: {@Request}",
                str("{OriginalFormat}", "Request: {@Request}"),
                str("Request", "GET /api/items"));
        assertEquals("Request: GET /api/items", log.getFormattedMessage());
    }

    @Test
    public void serilogStringifyPrefix_dollar() {
        // {$Status} – key is "Status"
        LogMessage log = decodeLog("Status: {$Status}",
                str("{OriginalFormat}", "Status: {$Status}"),
                str("Status", "active"));
        assertEquals("Status: active", log.getFormattedMessage());
    }

    @Test
    public void serilogDestructuringWithFormatSpec() {
        // {@Obj:json} – key is "Obj", spec "json" ignored
        LogMessage log = decodeLog("Object: {@Obj:json}",
                str("{OriginalFormat}", "Object: {@Obj:json}"),
                str("Obj", "{\"id\":1}"));
        assertEquals("Object: {\"id\":1}", log.getFormattedMessage());
    }

    // -----------------------------------------------------------------------
    // 6. Missing placeholder values – left as-is
    // -----------------------------------------------------------------------

    @Test
    public void missingAttribute_leftAsPlaceholder() {
        LogMessage log = decodeLog("Hello {Name}",
                str("{OriginalFormat}", "Hello {Name}"));
        // No "Name" attribute – placeholder stays
        assertEquals("Hello {Name}", log.getFormattedMessage());
    }

    @Test
    public void partiallyMissingAttributes() {
        LogMessage log = decodeLog("{Found} and {Missing}",
                str("{OriginalFormat}", "{Found} and {Missing}"),
                str("Found", "yes"));
        assertEquals("yes and {Missing}", log.getFormattedMessage());
    }

    // -----------------------------------------------------------------------
    // 7. Repeated placeholder
    // -----------------------------------------------------------------------

    @Test
    public void repeatedPlaceholder() {
        LogMessage log = decodeLog("{Name} met {Name}",
                str("{OriginalFormat}", "{Name} met {Name}"),
                str("Name", "Alice"));
        assertEquals("Alice met Alice", log.getFormattedMessage());
    }

    // -----------------------------------------------------------------------
    // 8. Template without any placeholders
    // -----------------------------------------------------------------------

    @Test
    public void templateWithNoPlaceholders() {
        LogMessage log = decodeLog("Static log line",
                str("{OriginalFormat}", "Static log line"),
                str("ExtraKey", "extra"));
        assertEquals("Static log line", log.getFormattedMessage());
    }

    // -----------------------------------------------------------------------
    // 9. Real-world MEL patterns
    // -----------------------------------------------------------------------

    @Test
    public void mel_httpRequestLog() {
        LogMessage log = decodeLog("HTTP {Method} {Path} responded {StatusCode} in {Elapsed:0.0000}ms",
                str("{OriginalFormat}", "HTTP {Method} {Path} responded {StatusCode} in {Elapsed:0.0000}ms"),
                str("Method", "GET"),
                str("Path", "/api/items"),
                intAttr("StatusCode", 200),
                dblAttr("Elapsed", 12.3456));
        assertEquals("HTTP GET /api/items responded 200 in 12.3456ms", log.getFormattedMessage());
    }

    @Test
    public void mel_scopedCategoryAndEventId() {
        LogMessage log = decodeLog("Processing item {ItemId} of type {ItemType}",
                str("{OriginalFormat}", "Processing item {ItemId} of type {ItemType}"),
                str("ItemId", "abc-123"),
                str("ItemType", "Order"),
                str("categoryName", "MyApp.OrderProcessor"),
                str("event.id", "1001"),
                str("event.name", "ProcessItem"));

        assertEquals("Processing item abc-123 of type Order", log.getFormattedMessage());
        assertEquals("MyApp.OrderProcessor", log.getCategoryName());
        assertNotNull(log.getEventId());
        assertEquals(1001, log.getEventId().getId());
        assertEquals("ProcessItem", log.getEventId().getName());
    }

    @Test
    public void mel_exceptionLog() {
        LogMessage log = decodeLog("Failed to process {EntityId}",
                str("{OriginalFormat}", "Failed to process {EntityId}"),
                str("EntityId", "order-99"),
                str("exception.type", "System.InvalidOperationException"),
                str("exception.message", "Entity not found"),
                str("exception.stacktrace", "   at MyApp.Service.Process() in Service.cs:line 42"));

        assertEquals("Failed to process order-99", log.getFormattedMessage());
        assertNotNull(log.getException());
        assertEquals("System.InvalidOperationException", log.getException().getType());
    }

    @Test
    public void mel_nullValueAttribute() {
        // Parameter attribute can be an empty string
        LogMessage log = decodeLog("User {Name} connected",
                str("{OriginalFormat}", "User {Name} connected"),
                str("Name", ""));
        assertEquals("User  connected", log.getFormattedMessage());
    }

    // -----------------------------------------------------------------------
    // 10. Real-world Serilog patterns
    // -----------------------------------------------------------------------

    @Test
    public void serilog_destructuredObject() {
        LogMessage log = decodeLog("Received {@Order}",
                str("{OriginalFormat}", "Received {@Order}"),
                str("Order", "OrderId=42, Total=99.99"));
        assertEquals("Received OrderId=42, Total=99.99", log.getFormattedMessage());
    }

    @Test
    public void serilog_mixedPrefixes() {
        LogMessage log = decodeLog("{@Ctx} user {$UserId} logged {Action}",
                str("{OriginalFormat}", "{@Ctx} user {$UserId} logged {Action}"),
                str("Ctx", "[req-id=abc]"),
                str("UserId", "42"),
                str("Action", "in"));
        assertEquals("[req-id=abc] user 42 logged in", log.getFormattedMessage());
    }

    // -----------------------------------------------------------------------
    // 11. Special characters and edge cases
    // -----------------------------------------------------------------------

    @Test
    public void valueContainingBraces() {
        // A value that itself contains { } – should not be re-processed
        LogMessage log = decodeLog("Data: {Payload}",
                str("{OriginalFormat}", "Data: {Payload}"),
                str("Payload", "{key: value}"));
        assertEquals("Data: {key: value}", log.getFormattedMessage());
    }

    @Test
    public void templateContainsLiteralPercent() {
        LogMessage log = decodeLog("Progress: {Progress}%",
                str("{OriginalFormat}", "Progress: {Progress}%"),
                intAttr("Progress", 75));
        assertEquals("Progress: 75%", log.getFormattedMessage());
    }

    @Test
    public void unicodeValues() {
        LogMessage log = decodeLog("User {Name} connected",
                str("{OriginalFormat}", "User {Name} connected"),
                str("Name", "日本語"));
        assertEquals("User 日本語 connected", log.getFormattedMessage());
    }

    @Test
    public void multilineTemplateBody() {
        // Newlines in template are kept (formatting doesn't strip them)
        LogMessage log = decodeLog("Line1\nLine2 {Value}",
                str("{OriginalFormat}", "Line1\nLine2 {Value}"),
                str("Value", "x"));
        assertEquals("Line1\nLine2 x", log.getFormattedMessage());
    }

    @Test
    public void emptyTemplate_returnsEmpty() {
        LogMessage log = decodeLog("",
                str("{OriginalFormat}", ""),
                str("Key", "value"));
        assertEquals("", log.getFormattedMessage());
    }

    // -----------------------------------------------------------------------
    // 12. Raw JSON tab (protobuf JSON) – basic sanity
    // -----------------------------------------------------------------------

    @Test
    public void rawJsonIsProtobufJsonNotDomainModel() {
        LogRecord logRecord = LogRecord.newBuilder()
                .setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_INFO)
                .setBody(AnyValue.newBuilder().setStringValue("hello"))
                .addAttributes(str("{OriginalFormat}", "hello"))
                .build();

        ExportLogsServiceRequest request = ExportLogsServiceRequest.newBuilder()
                .addResourceLogs(ResourceLogs.newBuilder()
                        .setResource(Resource.newBuilder())
                        .addScopeLogs(ScopeLogs.newBuilder()
                                .setScope(InstrumentationScope.newBuilder())
                                .addLogRecords(logRecord)))
                .build();

        List<TelemetryItem> items = decoder.decodeLogs(request.toByteArray());

        assertEquals(1, items.size());
        String rawJson = items.get(0).getRawJson();

        // Protobuf JSON uses camelCase field names from the .proto schema,
        // not the Jackson-serialized domain model fields.
        assertNotNull(rawJson);
        assertFalse("rawJson should not be empty", rawJson.isEmpty());
        // Protobuf JSON represents body as {"stringValue": "hello"} nested under "body"
        assertTrue("rawJson should contain protobuf field 'body'", rawJson.contains("body"));
        // Domain model JSON would contain "formattedMessage", "logLevel", etc. – those should NOT appear
        assertFalse("rawJson must not contain domain-model field 'formattedMessage'",
                rawJson.contains("formattedMessage"));
        assertFalse("rawJson must not contain domain-model field 'logLevel'",
                rawJson.contains("logLevel"));
    }

    @Test
    public void rawJsonForTrace_containsProtobufFields() {
        // Spot-check that traces also use protobuf JSON in rawJson
        io.opentelemetry.proto.trace.v1.Span span = io.opentelemetry.proto.trace.v1.Span.newBuilder()
                .setName("my-span")
                .setKind(io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_CLIENT)
                .setStartTimeUnixNano(1_000_000_000L)
                .setEndTimeUnixNano(2_000_000_000L)
                .build();

        io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest request =
                io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest.newBuilder()
                        .addResourceSpans(io.opentelemetry.proto.trace.v1.ResourceSpans.newBuilder()
                                .setResource(Resource.newBuilder())
                                .addScopeSpans(io.opentelemetry.proto.trace.v1.ScopeSpans.newBuilder()
                                        .setScope(InstrumentationScope.newBuilder())
                                        .addSpans(span)))
                        .build();

        List<TelemetryItem> items = decoder.decodeTraces(request.toByteArray());

        assertEquals(1, items.size());
        String rawJson = items.get(0).getRawJson();
        assertNotNull(rawJson);
        // Protobuf JSON for a Span uses "name", "kind", "startTimeUnixNano"
        assertTrue("rawJson should contain span name", rawJson.contains("my-span"));
        // Domain model JSON would contain "displayName", "operationName" – those must not appear
        assertFalse("rawJson must not contain domain-model field 'displayName'",
                rawJson.contains("displayName"));
    }
}

