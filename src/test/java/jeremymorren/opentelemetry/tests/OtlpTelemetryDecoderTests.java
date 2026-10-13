package jeremymorren.opentelemetry.tests;

import com.google.protobuf.ByteString;
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
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import jeremymorren.opentelemetry.models.Activity;
import jeremymorren.opentelemetry.models.LogMessage;
import jeremymorren.opentelemetry.models.ObjectDictionary;
import jeremymorren.opentelemetry.models.TelemetryItem;
import jeremymorren.opentelemetry.models.TelemetryType;
import jeremymorren.opentelemetry.otlp.OtlpTelemetryDecoder;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

/** Tests for decoding OTLP traces, logs and metrics into telemetry. */
public class OtlpTelemetryDecoderTests {
    private final OtlpTelemetryDecoder decoder = new OtlpTelemetryDecoder();

    @Test
    public void decodesTraceSpansIntoActivities() {
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

        ExportTraceServiceRequest request = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder()
                        .setResource(Resource.newBuilder().addAttributes(stringAttribute("service.name", "demo-api")))
                        .addScopeSpans(ScopeSpans.newBuilder()
                                .setScope(InstrumentationScope.newBuilder().setName("demo-tracer").setVersion("1.0.0"))
                                .addSpans(span)))
                .build();

        List<TelemetryItem> telemetries = decoder.decodeTraces(request.toByteArray());

        assert telemetries.size() == 1;
        TelemetryItem item = telemetries.getFirst();
        assert item.getTelemetry().getType() == TelemetryType.Exception;
        assert Duration.ofSeconds(1).equals(item.getDuration());
        Activity activity = item.getTelemetry().getActivity();
        assert activity != null;
        assert "Recorded".equals(activity.getTraceIds().get("Flags"));
        assert "/weather".equals(activity.getRequestPath());
        ObjectDictionary resource = item.getTelemetry().getResource();
        assert resource != null;
        assert "demo-api".equals(resource.getString("service.name"));
    }

    @Test
    public void decodesLogRecordsIntoMessages() {
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

        ExportLogsServiceRequest request = ExportLogsServiceRequest.newBuilder()
                .addResourceLogs(ResourceLogs.newBuilder()
                        .setResource(Resource.newBuilder().addAttributes(stringAttribute("service.name", "demo-api")))
                        .addScopeLogs(ScopeLogs.newBuilder()
                                .setScope(InstrumentationScope.newBuilder().setName("demo-logger"))
                                .addLogRecords(logRecord)))
                .build();

        List<TelemetryItem> telemetries = decoder.decodeLogs(request.toByteArray());

        assert telemetries.size() == 1;
        TelemetryItem item = telemetries.getFirst();
        assert item.getTelemetry().getType() == TelemetryType.Message;
        LogMessage log = item.getTelemetry().getLog();
        assert log != null;
        assert "cache miss for {CacheKey}".equals(log.getBody());
        assert "cache miss for user:42".equals(log.getFormattedMessage());
        assert "Demo.Logs".equals(log.getCategoryName());
        assert log.getEventId() != null;
        assert log.getEventId().getId() == 7;
        assert item.getRawJson().contains("\n");
        assert item.getRawJson().contains("  \"timeUnixNano\"");
    }

    @Test
    public void decodesMetricsIntoMetricPoints() {
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

        ExportMetricsServiceRequest request = ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .setResource(Resource.newBuilder().addAttributes(stringAttribute("service.name", "demo-api")))
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .setScope(InstrumentationScope.newBuilder().setName("demo-meter").setVersion("2.0.0"))
                                .addMetrics(metric)))
                .build();

        List<TelemetryItem> telemetries = decoder.decodeMetrics(request.toByteArray());

        assert telemetries.size() == 1;
        TelemetryItem item = telemetries.getFirst();
        assert item.getTelemetry().getType() == TelemetryType.Metric;
        jeremymorren.opentelemetry.models.Metric decoded = item.getTelemetry().getMetric();
        assert decoded != null;
        assert "http.server.request.duration".equals(decoded.getName());
        assert "CUMULATIVE".equals(decoded.getTemporality());
        assert decoded.getPoints() != null;
        assert Double.valueOf(42.5).equals(decoded.getPoints().getFirst().getDoubleSum());
    }

    private static KeyValue stringAttribute(String key, String value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build();
    }
}