package jeremymorren.opentelemetry.tests;

import com.google.protobuf.ByteString;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import jeremymorren.opentelemetry.models.Telemetry;
import jeremymorren.opentelemetry.models.TelemetryItem;
import jeremymorren.opentelemetry.models.TelemetryType;
import jeremymorren.opentelemetry.otlp.OtlpHttpReceiverService;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class OtlpHttpReceiverServiceTests {
    @Test
    public void addListenerReplaysOnlyMatchingScopeHistory() throws Exception {
        OtlpHttpReceiverService service = new OtlpHttpReceiverService();
        TelemetryItem projectOneTelemetry = telemetry("project-one");
        TelemetryItem projectTwoTelemetry = telemetry("project-two");

        publish(service, "project-one", projectOneTelemetry);
        publish(service, "project-two", projectTwoTelemetry);

        List<TelemetryItem> projectOneReceived = new ArrayList<>();
        List<TelemetryItem> projectTwoReceived = new ArrayList<>();

        service.addListener("project-one", (Consumer<TelemetryItem>) projectOneReceived::add);
        service.addListener("project-two", (Consumer<TelemetryItem>) projectTwoReceived::add);

        assert projectOneReceived.size() == 1;
        assert projectOneReceived.get(0) == projectOneTelemetry;
        assert projectTwoReceived.size() == 1;
        assert projectTwoReceived.get(0) == projectTwoTelemetry;
    }

    @Test
    public void clearRemovesOnlyMatchingScopeHistory() throws Exception {
        OtlpHttpReceiverService service = new OtlpHttpReceiverService();
        TelemetryItem projectOneTelemetry = telemetry("project-one");
        TelemetryItem projectTwoTelemetry = telemetry("project-two");

        publish(service, "project-one", projectOneTelemetry);
        publish(service, "project-two", projectTwoTelemetry);

        service.clear("project-one");

        List<TelemetryItem> projectOneReceived = new ArrayList<>();
        List<TelemetryItem> projectTwoReceived = new ArrayList<>();

        service.addListener("project-one", (Consumer<TelemetryItem>) projectOneReceived::add);
        service.addListener("project-two", (Consumer<TelemetryItem>) projectTwoReceived::add);

        assert projectOneReceived.isEmpty();
        assert projectTwoReceived.size() == 1;
        assert projectTwoReceived.get(0) == projectTwoTelemetry;
    }

    @Test
    public void disposedListenerDoesNotReceiveFutureTelemetry() throws Exception {
        OtlpHttpReceiverService service = new OtlpHttpReceiverService();
        List<TelemetryItem> received = new ArrayList<>();

        AutoCloseable registration = service.addListener("project-one", (Consumer<TelemetryItem>) received::add);
        registration.close();

        publish(service, "project-one", telemetry("project-one"));

        assert received.isEmpty();
    }

    @Test
    public void resolvesSignalTypeFromRequestPath() throws Exception {
        assert "LOGS".equals(resolveSignalType("/project-one/v1/logs"));
        assert "METRICS".equals(resolveSignalType("/project-one/v1/metrics"));
        assert "TRACES".equals(resolveSignalType("/project-one/v1/traces"));
        assert resolveSignalType("/project-one/not-otlp/logs") == null;
    }

    @Test
    public void handleRoutesLogPayloadByProjectScope() throws Exception {
        OtlpHttpReceiverService service = new OtlpHttpReceiverService();
        List<TelemetryItem> projectOneReceived = new ArrayList<>();
        List<TelemetryItem> projectTwoReceived = new ArrayList<>();
        service.addListener("project-one", (Consumer<TelemetryItem>) projectOneReceived::add);
        service.addListener("project-two", (Consumer<TelemetryItem>) projectTwoReceived::add);

        FakeHttpExchange exchange = new FakeHttpExchange("POST", "/project-one/v1/logs", createLogsPayload());

        handle(service, exchange);

        assert exchange.responseCode == 200;
        assert "".equals(exchange.responseBodyAsString());
        assert "text/plain; charset=utf-8".equals(exchange.getResponseHeaders().getFirst("Content-Type"));
        assert projectOneReceived.size() == 1;
        assert projectOneReceived.get(0).getTelemetry().getType() == TelemetryType.Message;
        assert projectTwoReceived.isEmpty();
    }

    @Test
    public void handleDecodesMetricsPayloadFromRequestPath() throws Exception {
        OtlpHttpReceiverService service = new OtlpHttpReceiverService();
        List<TelemetryItem> received = new ArrayList<>();
        service.addListener("project-one", (Consumer<TelemetryItem>) received::add);

        handle(service, new FakeHttpExchange("POST", "/project-one/v1/metrics", createMetricsPayload()));

        assert received.size() == 1;
        assert received.get(0).getTelemetry().getType() == TelemetryType.Metric;
    }

    @Test
    public void handleDecodesTracePayloadFromRequestPath() throws Exception {
        OtlpHttpReceiverService service = new OtlpHttpReceiverService();
        List<TelemetryItem> received = new ArrayList<>();
        service.addListener("project-one", (Consumer<TelemetryItem>) received::add);

        handle(service, new FakeHttpExchange("POST", "/project-one/v1/traces", createTracesPayload()));

        assert received.size() == 1;
        assert received.get(0).getTelemetry().getType() == TelemetryType.Exception;
    }

    @Test
    public void handleRejectsUnknownPath() throws Exception {
        OtlpHttpReceiverService service = new OtlpHttpReceiverService();
        FakeHttpExchange exchange = new FakeHttpExchange("POST", "/project-one/not-otlp/logs", new byte[0]);

        handle(service, exchange);

        assert exchange.responseCode == 404;
        assert "Not Found".equals(exchange.responseBodyAsString());
    }

    @Test
    public void handleRejectsNonPostRequests() throws Exception {
        OtlpHttpReceiverService service = new OtlpHttpReceiverService();
        FakeHttpExchange exchange = new FakeHttpExchange("GET", "/project-one/v1/logs", new byte[0]);

        handle(service, exchange);

        assert exchange.responseCode == 405;
        assert "Method Not Allowed".equals(exchange.responseBodyAsString());
    }

    @Test
    public void handleReturnsBadRequestForInvalidPayload() throws Exception {
        OtlpHttpReceiverService service = new OtlpHttpReceiverService();
        FakeHttpExchange exchange = new FakeHttpExchange("POST", "/project-one/v1/logs", new byte[]{1, 2, 3});

        handle(service, exchange);

        assert exchange.responseCode == 400;
        assert !exchange.responseBodyAsString().isEmpty();
    }

    private static void handle(OtlpHttpReceiverService service, FakeHttpExchange exchange) throws Exception {
        Method handle = OtlpHttpReceiverService.class.getDeclaredMethod("handle", HttpExchange.class);
        handle.setAccessible(true);
        try {
            handle.invoke(service, exchange);
        } catch (InvocationTargetException ex) {
            throw unwrap(ex);
        }
    }

    private static void publish(OtlpHttpReceiverService service, String scopeKey, TelemetryItem telemetryItem) throws Exception {
        Method publish = OtlpHttpReceiverService.class.getDeclaredMethod("publish", String.class, TelemetryItem.class);
        publish.setAccessible(true);
        publish.invoke(service, scopeKey, telemetryItem);
    }

    private static String resolveSignalType(String path) throws Exception {
        Class<?> signalType = Class.forName("jeremymorren.opentelemetry.otlp.OtlpHttpReceiverService$SignalType");
        Class<?> companionClass = Class.forName("jeremymorren.opentelemetry.otlp.OtlpHttpReceiverService$SignalType$Companion");
        Method fromPath = companionClass.getDeclaredMethod("fromPath", String.class);
        fromPath.setAccessible(true);

        var companionField = signalType.getDeclaredField("Companion");
        companionField.setAccessible(true);
        Object companion = companionField.get(null);
        try {
            Object result = fromPath.invoke(companion, path);
            return result == null ? null : result.toString();
        } catch (InvocationTargetException ex) {
            throw unwrap(ex);
        }
    }

    private static RuntimeException unwrap(InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(cause);
    }

    private static TelemetryItem telemetry(String id) {
        return new TelemetryItem(id, id, new Telemetry(null, null, null, null));
    }

    private static byte[] createLogsPayload() {
        LogRecord logRecord = LogRecord.newBuilder()
                .setTimeUnixNano(3_000_000_000L)
                .setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_WARN)
                .setBody(AnyValue.newBuilder().setStringValue("cache miss for {CacheKey}"))
                .setTraceId(ByteString.copyFromUtf8("trace-log-123456"))
                .setSpanId(ByteString.copyFromUtf8("span-log"))
                .addAttributes(stringAttribute("categoryName", "Demo.Logs"))
                .addAttributes(stringAttribute("event.id", "7"))
                .addAttributes(stringAttribute("event.name", "CacheMiss"))
                .addAttributes(stringAttribute("CacheKey", "user:42"))
                .build();

        return ExportLogsServiceRequest.newBuilder()
                .addResourceLogs(ResourceLogs.newBuilder()
                        .setResource(Resource.newBuilder().addAttributes(stringAttribute("service.name", "demo-api")))
                        .addScopeLogs(ScopeLogs.newBuilder()
                                .setScope(InstrumentationScope.newBuilder().setName("demo-logger"))
                                .addLogRecords(logRecord)))
                .build()
                .toByteArray();
    }

    private static byte[] createMetricsPayload() {
        Metric metric = Metric.newBuilder()
                .setName("http.server.request.duration")
                .setDescription("Request duration")
                .setUnit("ms")
                .setSum(Sum.newBuilder()
                        .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE)
                        .addDataPoints(NumberDataPoint.newBuilder()
                                .setStartTimeUnixNano(1_000_000_000L)
                                .setTimeUnixNano(2_000_000_000L)
                                .setAsDouble(42.5)
                                .addAttributes(stringAttribute("http.method", "GET"))))
                .build();

        return ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .setResource(Resource.newBuilder().addAttributes(stringAttribute("service.name", "demo-api")))
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .setScope(InstrumentationScope.newBuilder().setName("demo-meter").setVersion("2.0.0"))
                                .addMetrics(metric)))
                .build()
                .toByteArray();
    }

    private static byte[] createTracesPayload() {
        Span.Event exceptionEvent = Span.Event.newBuilder()
                .setName("exception")
                .setTimeUnixNano(1_500_000_000L)
                .addAttributes(stringAttribute("exception.message", "boom"))
                .addAttributes(stringAttribute("exception.type", "System.Exception"))
                .addAttributes(stringAttribute("exception.stacktrace", "stack"))
                .build();

        Span span = Span.newBuilder()
                .setName("GET /weather")
                .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                .setStartTimeUnixNano(1_000_000_000L)
                .setEndTimeUnixNano(2_000_000_000L)
                .setTraceId(ByteString.copyFromUtf8("0123456789abcdef"))
                .setSpanId(ByteString.copyFromUtf8("span-id1"))
                .setParentSpanId(ByteString.copyFromUtf8("parent01"))
                .setFlags(1)
                .addAttributes(stringAttribute("url.path", "/weather"))
                .addAttributes(stringAttribute("http.response.status_code", "200"))
                .addEvents(exceptionEvent)
                .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR).setMessage("failed"))
                .build();

        return ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder()
                        .setResource(Resource.newBuilder().addAttributes(stringAttribute("service.name", "demo-api")))
                        .addScopeSpans(ScopeSpans.newBuilder()
                                .setScope(InstrumentationScope.newBuilder().setName("demo-tracer").setVersion("1.0.0"))
                                .addSpans(span)))
                .build()
                .toByteArray();
    }

    private static KeyValue stringAttribute(String key, String value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build();
    }

    private static final class FakeHttpExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final String requestMethod;
        private final URI requestUri;
        private final ByteArrayInputStream requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private final Map<String, Object> attributes = new HashMap<>();
        private int responseCode = -1;

        private FakeHttpExchange(String requestMethod, String requestPath, byte[] requestBody) {
            this.requestMethod = requestMethod;
            this.requestUri = URI.create("http://127.0.0.1" + requestPath);
            this.requestBody = new ByteArrayInputStream(requestBody);
        }

        private String responseBodyAsString() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return requestUri;
        }

        @Override
        public String getRequestMethod() {
            return requestMethod;
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return requestBody;
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            this.responseCode = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 4318);
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 4318);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}