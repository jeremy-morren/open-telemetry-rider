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
import jeremymorren.opentelemetry.models.TelemetryItem;
import jeremymorren.opentelemetry.models.TelemetryType;
import jeremymorren.opentelemetry.otlp.OtlpTelemetryDecoder;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

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
        assert telemetries.get(0).getTelemetry().getType() == TelemetryType.Exception;
        assert telemetries.get(0).getTelemetry().getActivity() != null;
        assert Duration.ofSeconds(1).equals(telemetries.get(0).getDuration());
        assert "Recorded".equals(telemetries.get(0).getTelemetry().getActivity().getTraceIds().get("Flags"));
        assert "/weather".equals(telemetries.get(0).getTelemetry().getActivity().getRequestPath());
        assert "demo-api".equals(telemetries.get(0).getTelemetry().getResource().getString("service.name"));
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
        assert telemetries.get(0).getTelemetry().getType() == TelemetryType.Message;
        assert telemetries.get(0).getTelemetry().getLog() != null;
        assert "cache miss for {CacheKey}".equals(telemetries.get(0).getTelemetry().getLog().getBody());
        assert "cache miss for user:42".equals(telemetries.get(0).getTelemetry().getLog().getFormattedMessage());
        assert "Demo.Logs".equals(telemetries.get(0).getTelemetry().getLog().getCategoryName());
        assert telemetries.get(0).getTelemetry().getLog().getEventId().getId() == 7;
        assert telemetries.get(0).getRawJson().contains("\n");
        assert telemetries.get(0).getRawJson().contains("  \"timeUnixNano\"");
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
        assert telemetries.get(0).getTelemetry().getType() == TelemetryType.Metric;
        assert telemetries.get(0).getTelemetry().getMetric() != null;
        assert "http.server.request.duration".equals(telemetries.get(0).getTelemetry().getMetric().getName());
        assert "CUMULATIVE".equals(telemetries.get(0).getTelemetry().getMetric().getTemporality());
        assert telemetries.get(0).getTelemetry().getMetric().getPoints().get(0).getDoubleSum() == 42.5;
    }

    private static KeyValue stringAttribute(String key, String value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build();
    }
}