@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package jeremymorren.opentelemetry.otlp

import com.google.protobuf.ByteString
import com.google.protobuf.util.JsonFormat
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.InstrumentationScope
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.logs.v1.LogRecord
import io.opentelemetry.proto.logs.v1.SeverityNumber
import io.opentelemetry.proto.metrics.v1.*
import io.opentelemetry.proto.metrics.v1.Metric
import io.opentelemetry.proto.trace.v1.Span
import io.opentelemetry.proto.trace.v1.Status
import jeremymorren.opentelemetry.models.*
import kotlinx.serialization.json.*
import java.time.Duration
import java.time.Instant
import java.util.*

class OtlpTelemetryDecoder(
    private val telemetryFactory: TelemetryFactory = TelemetryFactory(),
) {
    private val jsonPrinter: JsonFormat.Printer = JsonFormat.printer().alwaysPrintFieldsWithNoPresence()

    private val messageTemplateRegex = Regex("""\{[@$]?([^}:@]+?)(?::[^}]*)?}""")

    fun decodeTraces(payload: ByteArray): List<TelemetryItem> {
        val request = ExportTraceServiceRequest.parseFrom(payload)
        return buildList {
            for (resourceSpan in request.resourceSpansList) {
                val resource = attributesToObjectDictionary(resourceSpan.resource.attributesList)
                for (scopeSpan in resourceSpan.scopeSpansList) {
                    for (span in scopeSpan.spansList) {
                        val telemetry = toTraceTelemetry(span, resource, scopeSpan.scope)
                        add(telemetryFactory.createFromTelemetry(telemetry, jsonPrinter.print(span)))
                    }
                }
            }
        }
    }

    fun decodeLogs(payload: ByteArray): List<TelemetryItem> {
        val request = ExportLogsServiceRequest.parseFrom(payload)
        return buildList {
            for (resourceLog in request.resourceLogsList) {
                val resource = attributesToObjectDictionary(resourceLog.resource.attributesList)
                for (scopeLog in resourceLog.scopeLogsList) {
                    for (logRecord in scopeLog.logRecordsList) {
                        val telemetry = toLogTelemetry(logRecord, resource, scopeLog.scope)
                        add(telemetryFactory.createFromTelemetry(telemetry, jsonPrinter.print(logRecord)))
                    }
                }
            }
        }
    }

    fun decodeMetrics(payload: ByteArray): List<TelemetryItem> {
        val request = ExportMetricsServiceRequest.parseFrom(payload)
        return buildList {
            for (resourceMetric in request.resourceMetricsList) {
                val resource = attributesToObjectDictionary(resourceMetric.resource.attributesList)
                for (scopeMetric in resourceMetric.scopeMetricsList) {
                    for (metric in scopeMetric.metricsList) {
                        val telemetry = toMetricTelemetry(metric, resource, scopeMetric.scope)
                        add(telemetryFactory.createFromTelemetry(telemetry, jsonPrinter.print(metric)))
                    }
                }
            }
        }
    }

    private fun toTraceTelemetry(span: Span, resource: ObjectDictionary?, scope: InstrumentationScope): Telemetry {
        val duration =
            if (span.startTimeUnixNano > 0 && span.endTimeUnixNano >= span.startTimeUnixNano) {
                Duration.ofNanos(span.endTimeUnixNano - span.startTimeUnixNano)
            } else {
                null
            }

        val activity = Activity(
            traceId = bytesToHex(span.traceId),
            spanId = bytesToHex(span.spanId),
            parentSpanId = bytesToHex(span.parentSpanId).takeUnless { it.isNullOrBlank() },
            activityTraceFlags = span.flags.toString(),
            source = scopeToActivitySource(scope),
            displayName = span.name.takeUnless { it.isBlank() },
            kind = toActivityKind(span.kind),
            startTime = toInstant(span.startTimeUnixNano),
            duration = duration,
            tags = attributesToObjectDictionary(span.attributesList),
            operationName = span.name.takeUnless { it.isBlank() },
            status = toActivityStatus(span.status),
            statusDescription = span.status.message.takeUnless { it.isBlank() },
            events = span.eventsList.map { event ->
                ActivityEvent(
                    name = event.name.takeUnless { it.isBlank() },
                    timestamp = toInstant(event.timeUnixNano),
                    tags = attributesToObjectDictionary(event.attributesList),
                )
            },
        )

        return Telemetry(activity = activity, resource = resource)
    }

    private fun toLogTelemetry(logRecord: LogRecord, resource: ObjectDictionary?, scope: InstrumentationScope): Telemetry {
        val attributes = attributesToObjectDictionary(logRecord.attributesList)
        val exception = exceptionFromAttributes(attributes)
        val body = anyValueToDisplayString(logRecord.body)
        val formattedMessage = formatLogMessage(body, attributes)
        val log = LogMessage(
            body = body,
            formattedMessage = formattedMessage,
            logLevel = toLogLevel(logRecord.severityNumber, logRecord.severityText),
            timestamp = toInstant(logRecord.timeUnixNano),
            exception = exception,
            attributes = attributes,
            traceId = bytesToHex(logRecord.traceId).takeUnless { it.isNullOrBlank() },
            spanId = bytesToHex(logRecord.spanId).takeUnless { it.isNullOrBlank() },
            categoryName = attributes?.getString("categoryName") ?: scope.name.takeUnless { it.isBlank() },
            eventId = attributes?.getString("event.id")?.toIntOrNull()?.let { EventId(it, attributes.getString("event.name")) },
        )

        return Telemetry(log = log, resource = resource)
    }

    /**
     * Formats a log message body using the Microsoft.Extensions.Logging / Serilog message template convention.
     *
     * The template is taken directly from [body]. Each `{PropertyName}` placeholder (with optional
     * Serilog destructuring prefix `@` / `$` and format specifier `:fmt`) is replaced with the
     * corresponding attribute value. If [attributes] is null, [body] is returned unchanged.
     */
    private fun formatLogMessage(body: String?, attributes: ObjectDictionary?): String? {
        if (attributes == null || body == null) return body
        return messageTemplateRegex.replace(body) { match ->
            val key = match.groupValues[1].trim()
            attributes.getString(key) ?: match.value
        }
    }

    private fun toMetricTelemetry(metric: Metric, resource: ObjectDictionary?, scope: InstrumentationScope): Telemetry {
        val mappedMetric = when (metric.dataCase) {
            Metric.DataCase.GAUGE -> jeremymorren.opentelemetry.models.Metric(
                metricType = "Gauge",
                temporality = null,
                name = metric.name.takeUnless { it.isBlank() },
                description = metric.description.takeUnless { it.isBlank() },
                unit = metric.unit.takeUnless { it.isBlank() },
                meterName = scope.name.takeUnless { it.isBlank() },
                meterVersion = scope.version.takeUnless { it.isBlank() },
                meterTags = attributesToObjectDictionary(scope.attributesList),
                points = toGaugePoints(metric.gauge),
            )

            Metric.DataCase.SUM -> jeremymorren.opentelemetry.models.Metric(
                metricType = "Sum",
                temporality = aggregationTemporality(metric.sum.aggregationTemporality),
                name = metric.name.takeUnless { it.isBlank() },
                description = metric.description.takeUnless { it.isBlank() },
                unit = metric.unit.takeUnless { it.isBlank() },
                meterName = scope.name.takeUnless { it.isBlank() },
                meterVersion = scope.version.takeUnless { it.isBlank() },
                meterTags = attributesToObjectDictionary(scope.attributesList),
                points = toSumPoints(metric.sum),
            )

            Metric.DataCase.HISTOGRAM -> jeremymorren.opentelemetry.models.Metric(
                metricType = "Histogram",
                temporality = aggregationTemporality(metric.histogram.aggregationTemporality),
                name = metric.name.takeUnless { it.isBlank() },
                description = metric.description.takeUnless { it.isBlank() },
                unit = metric.unit.takeUnless { it.isBlank() },
                meterName = scope.name.takeUnless { it.isBlank() },
                meterVersion = scope.version.takeUnless { it.isBlank() },
                meterTags = attributesToObjectDictionary(scope.attributesList),
                points = toHistogramPoints(metric.histogram),
            )

            Metric.DataCase.EXPONENTIAL_HISTOGRAM -> jeremymorren.opentelemetry.models.Metric(
                metricType = "ExponentialHistogram",
                temporality = aggregationTemporality(metric.exponentialHistogram.aggregationTemporality),
                name = metric.name.takeUnless { it.isBlank() },
                description = metric.description.takeUnless { it.isBlank() },
                unit = metric.unit.takeUnless { it.isBlank() },
                meterName = scope.name.takeUnless { it.isBlank() },
                meterVersion = scope.version.takeUnless { it.isBlank() },
                meterTags = attributesToObjectDictionary(scope.attributesList),
                points = metric.exponentialHistogram.dataPointsList.map(::toExponentialHistogramPoint),
            )

            Metric.DataCase.SUMMARY -> jeremymorren.opentelemetry.models.Metric(
                metricType = "Summary",
                temporality = null,
                name = metric.name.takeUnless { it.isBlank() },
                description = metric.description.takeUnless { it.isBlank() },
                unit = metric.unit.takeUnless { it.isBlank() },
                meterName = scope.name.takeUnless { it.isBlank() },
                meterVersion = scope.version.takeUnless { it.isBlank() },
                meterTags = attributesToObjectDictionary(scope.attributesList),
                points = metric.summary.dataPointsList.map(::toSummaryPoint),
            )

            else -> jeremymorren.opentelemetry.models.Metric(
                metricType = metric.dataCase.name,
                name = metric.name.takeUnless { it.isBlank() },
                description = metric.description.takeUnless { it.isBlank() },
                unit = metric.unit.takeUnless { it.isBlank() },
                meterName = scope.name.takeUnless { it.isBlank() },
                meterVersion = scope.version.takeUnless { it.isBlank() },
                meterTags = attributesToObjectDictionary(scope.attributesList),
                points = emptyList(),
            )
        }

        return Telemetry(metric = mappedMetric, resource = resource)
    }

    private fun toGaugePoints(gauge: Gauge): List<MetricPoint> = gauge.dataPointsList.map(::toNumberDataPoint)

    private fun toSumPoints(sum: Sum): List<MetricPoint> = sum.dataPointsList.map(::toNumberDataPoint)

    private fun toHistogramPoints(histogram: Histogram): List<MetricPoint> = histogram.dataPointsList.map(::toHistogramPoint)

    private fun toNumberDataPoint(dataPoint: NumberDataPoint): MetricPoint =
        MetricPoint(
            startTime = toInstant(dataPoint.startTimeUnixNano),
            endTime = toInstant(dataPoint.timeUnixNano),
            tags = attributesToObjectDictionary(dataPoint.attributesList),
            longSum = if (dataPoint.valueCase == NumberDataPoint.ValueCase.AS_INT) dataPoint.asInt else null,
            doubleSum = if (dataPoint.valueCase == NumberDataPoint.ValueCase.AS_DOUBLE) dataPoint.asDouble else null,
            longGauge = if (dataPoint.valueCase == NumberDataPoint.ValueCase.AS_INT) dataPoint.asInt else null,
            doubleGauge = if (dataPoint.valueCase == NumberDataPoint.ValueCase.AS_DOUBLE) dataPoint.asDouble else null,
        )

    private fun toHistogramPoint(dataPoint: HistogramDataPoint): MetricPoint =
        MetricPoint(
            startTime = toInstant(dataPoint.startTimeUnixNano),
            endTime = toInstant(dataPoint.timeUnixNano),
            tags = attributesToObjectDictionary(dataPoint.attributesList),
            histogramCount = dataPoint.count,
            histogramSum = if (dataPoint.hasSum()) dataPoint.sum else null,
        )

    private fun toExponentialHistogramPoint(dataPoint: ExponentialHistogramDataPoint): MetricPoint =
        MetricPoint(
            startTime = toInstant(dataPoint.startTimeUnixNano),
            endTime = toInstant(dataPoint.timeUnixNano),
            tags = attributesToObjectDictionary(dataPoint.attributesList),
            histogramCount = dataPoint.count,
            histogramSum = if (dataPoint.hasSum()) dataPoint.sum else null,
        )

    private fun toSummaryPoint(dataPoint: SummaryDataPoint): MetricPoint =
        MetricPoint(
            startTime = toInstant(dataPoint.startTimeUnixNano),
            endTime = toInstant(dataPoint.timeUnixNano),
            tags = attributesToObjectDictionary(dataPoint.attributesList),
            histogramCount = dataPoint.count,
            histogramSum = dataPoint.sum,
        )

    private fun aggregationTemporality(temporality: AggregationTemporality): String? =
        when (temporality) {
            AggregationTemporality.AGGREGATION_TEMPORALITY_UNSPECIFIED -> null
            else -> temporality.name.removePrefix("AGGREGATION_TEMPORALITY_")
        }

    private fun exceptionFromAttributes(attributes: ObjectDictionary?): ExceptionInfo? {
        if (attributes == null) {
            return null
        }

        val message = attributes.getString("exception.message")
        val type = attributes.getString("exception.type")
        val stacktrace = attributes.getString("exception.stacktrace")
        if (message == null && type == null && stacktrace == null) {
            return null
        }

        return ExceptionInfo(
            message = message,
            display = stacktrace ?: message,
            type = type,
        )
    }

    private fun toActivityKind(kind: Span.SpanKind): ActivityKind =
        when (kind) {
            Span.SpanKind.SPAN_KIND_SERVER -> ActivityKind.Server
            Span.SpanKind.SPAN_KIND_CLIENT -> ActivityKind.Client
            Span.SpanKind.SPAN_KIND_PRODUCER -> ActivityKind.Producer
            Span.SpanKind.SPAN_KIND_CONSUMER -> ActivityKind.Consumer
            else -> ActivityKind.Internal
        }

    private fun toActivityStatus(status: Status): ActivityStatusCode =
        when (status.code) {
            Status.StatusCode.STATUS_CODE_OK -> ActivityStatusCode.Ok
            Status.StatusCode.STATUS_CODE_ERROR -> ActivityStatusCode.Error
            else -> ActivityStatusCode.Unset
        }

    private fun toLogLevel(severityNumber: SeverityNumber, severityText: String): LogLevel =
        when {
            severityNumber.number in SeverityNumber.SEVERITY_NUMBER_TRACE.number..SeverityNumber.SEVERITY_NUMBER_TRACE4.number -> LogLevel.Trace
            severityNumber.number in SeverityNumber.SEVERITY_NUMBER_DEBUG.number..SeverityNumber.SEVERITY_NUMBER_DEBUG4.number -> LogLevel.Debug
            severityNumber.number in SeverityNumber.SEVERITY_NUMBER_INFO.number..SeverityNumber.SEVERITY_NUMBER_INFO4.number -> LogLevel.Information
            severityNumber.number in SeverityNumber.SEVERITY_NUMBER_WARN.number..SeverityNumber.SEVERITY_NUMBER_WARN4.number -> LogLevel.Warning
            severityNumber.number in SeverityNumber.SEVERITY_NUMBER_ERROR.number..SeverityNumber.SEVERITY_NUMBER_ERROR4.number -> LogLevel.Error
            severityNumber.number in SeverityNumber.SEVERITY_NUMBER_FATAL.number..SeverityNumber.SEVERITY_NUMBER_FATAL4.number -> LogLevel.Critical
            severityText.equals("warning", ignoreCase = true) -> LogLevel.Warning
            severityText.equals("error", ignoreCase = true) -> LogLevel.Error
            severityText.equals("critical", ignoreCase = true) -> LogLevel.Critical
            else -> LogLevel.Information
        }

    private fun scopeToActivitySource(scope: InstrumentationScope): ActivitySource? {
        if (scope.name.isBlank()) {
            return null
        }
        return ActivitySource(scope.name, scope.version.takeUnless { it.isBlank() })
    }

    private fun toInstant(unixNano: Long): Instant? =
        if (unixNano <= 0) {
            null
        } else {
            Instant.ofEpochSecond(0, unixNano)
        }

    private fun attributesToObjectDictionary(attributes: List<KeyValue>): ObjectDictionary? {
        if (attributes.isEmpty()) {
            return null
        }

        return ObjectDictionary(
            JsonObject(
                attributes.associate { attribute ->
                    attribute.key to anyValueToJson(attribute.value)
                },
            ),
        )
    }

    private fun anyValueToDisplayString(anyValue: AnyValue): String? =
        when (anyValue.valueCase) {
            AnyValue.ValueCase.STRING_VALUE -> anyValue.stringValue
            AnyValue.ValueCase.BOOL_VALUE -> anyValue.boolValue.toString()
            AnyValue.ValueCase.INT_VALUE -> anyValue.intValue.toString()
            AnyValue.ValueCase.DOUBLE_VALUE -> anyValue.doubleValue.toString()
            AnyValue.ValueCase.BYTES_VALUE -> Base64.getEncoder().encodeToString(anyValue.bytesValue.toByteArray())
            AnyValue.ValueCase.ARRAY_VALUE,
            AnyValue.ValueCase.KVLIST_VALUE,
            -> anyValueToJson(anyValue).toString()

            else -> null
        }

    private fun anyValueToJson(anyValue: AnyValue): JsonElement =
        when (anyValue.valueCase) {
            AnyValue.ValueCase.STRING_VALUE -> JsonPrimitive(anyValue.stringValue)
            AnyValue.ValueCase.BOOL_VALUE -> JsonPrimitive(anyValue.boolValue)
            AnyValue.ValueCase.INT_VALUE -> JsonPrimitive(anyValue.intValue)
            AnyValue.ValueCase.DOUBLE_VALUE -> JsonPrimitive(anyValue.doubleValue)
            AnyValue.ValueCase.BYTES_VALUE -> JsonPrimitive(Base64.getEncoder().encodeToString(anyValue.bytesValue.toByteArray()))
            AnyValue.ValueCase.ARRAY_VALUE -> JsonArray(anyValue.arrayValue.valuesList.map(::anyValueToJson))
            AnyValue.ValueCase.KVLIST_VALUE -> JsonObject(anyValue.kvlistValue.valuesList.associate { it.key to anyValueToJson(it.value) })
            else -> JsonNull
        }

    private fun bytesToHex(bytes: ByteString): String? {
        if (bytes.isEmpty) {
            return null
        }

        return bytes.toByteArray().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

}