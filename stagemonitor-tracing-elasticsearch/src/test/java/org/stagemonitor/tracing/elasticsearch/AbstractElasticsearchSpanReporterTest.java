package org.stagemonitor.tracing.elasticsearch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.slf4j.Logger;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.elasticsearch.ElasticsearchClient;
import org.stagemonitor.core.metrics.metrics2.Metric2Registry;
import org.stagemonitor.tracing.MockTracer;
import org.stagemonitor.tracing.RequestMonitor;
import org.stagemonitor.tracing.SpanContextInformation;
import org.stagemonitor.tracing.TagRecordingSpanEventListener;
import org.stagemonitor.tracing.TracingPlugin;
import org.stagemonitor.tracing.profiler.CallStackElement;
import org.stagemonitor.tracing.reporter.ReportingSpanEventListener;
import org.stagemonitor.tracing.sampling.SamplePriorityDeterminingSpanEventListener;
import org.stagemonitor.tracing.utils.SpanUtils;
import org.stagemonitor.tracing.wrapper.SpanWrappingTracer;

import com.uber.jaeger.context.TracingUtils;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

public class AbstractElasticsearchSpanReporterTest {
	protected List<ElasticsearchClient> elasticsearchClients;
	protected TracingPlugin tracingPlugin;
	protected ElasticsearchTracingPlugin elasticsearchTracingPlugin;
	protected Logger spanLogger;
	protected Metric2Registry registry;
	protected ConfigurationRegistry configuration;
	protected CorePlugin corePlugin;
	protected Map<String, Object> tags;
	protected ReportingSpanEventListener reportingSpanEventListener;

	@Before
	public void setUp() throws Exception {
		configuration = mock(ConfigurationRegistry.class);
		corePlugin = mock(CorePlugin.class);
		tracingPlugin = mock(TracingPlugin.class);
		elasticsearchTracingPlugin = mock(ElasticsearchTracingPlugin.class);

		when(configuration.getConfig(CorePlugin.class)).thenReturn(corePlugin);
		when(configuration.getConfig(TracingPlugin.class)).thenReturn(tracingPlugin);
		when(configuration.getConfig(ElasticsearchTracingPlugin.class)).thenReturn(elasticsearchTracingPlugin);
		when(tracingPlugin.getDefaultRateLimitSpansPerMinute()).thenReturn(1000000d);
		when(tracingPlugin.getDefaultRateLimitSpansPerMinuteOption()).thenReturn(mock(ConfigurationOption.class));
		when(tracingPlugin.getDefaultRateLimitSpansPerMinuteOption()).thenReturn(mock(ConfigurationOption.class));
		when(tracingPlugin.getRateLimitSpansPerMinutePerTypeOption()).thenReturn(mock(ConfigurationOption.class));
		when(tracingPlugin.getProfilerRateLimitPerMinuteOption()).thenReturn(mock(ConfigurationOption.class));
		when(tracingPlugin.getDefaultRateLimitSpansPercentOption()).thenReturn(mock(ConfigurationOption.class));
		when(tracingPlugin.getRateLimitSpansPerMinutePercentPerTypeOption()).thenReturn(mock(ConfigurationOption.class));
		when(tracingPlugin.getDefaultRateLimitSpansPercent()).thenReturn(1.0);
		when(tracingPlugin.getRateLimitSpansPerMinutePercentPerType()).thenReturn(Collections.emptyMap());
		when(tracingPlugin.getOnlyReportSpansWithName()).thenReturn(Collections.singleton("Report Me"));
		when(tracingPlugin.isProfilerActive()).thenReturn(true);
		when(tracingPlugin.getProfilerRateLimitPerMinute()).thenReturn(1_000_000d);
		when(corePlugin.getElasticsearchUrls()).thenReturn(Collections.singletonList("http://localhost:9200"));
		when(corePlugin.getElasticsearchClients()).thenReturn(elasticsearchClients = mockESClients());
		when(corePlugin.getThreadPoolQueueCapacityLimit()).thenReturn(1000);
		for(ElasticsearchClient esclient : elasticsearchClients) {
			when(esclient.isElasticsearchAvailable()).thenReturn(true);
		}
		registry = new Metric2Registry();
		when(corePlugin.getMetricRegistry()).thenReturn(registry);
		spanLogger = mock(Logger.class);
		tags = new HashMap<>();
		when(tracingPlugin.getRequestMonitor()).thenReturn(mock(RequestMonitor.class));
		reportingSpanEventListener = new ReportingSpanEventListener(configuration);
		final SpanWrappingTracer tracer = TracingPlugin.createSpanWrappingTracer(new MockTracer(),
				configuration, registry, TagRecordingSpanEventListener.asList(tags),
				new SamplePriorityDeterminingSpanEventListener(configuration), reportingSpanEventListener);
		when(tracingPlugin.getTracer()).thenReturn(tracer);
		Assert.assertTrue(TracingUtils.getTraceContext().isEmpty());
	}

	@After
	public void tearDown() throws Exception {
		Assert.assertTrue(TracingUtils.getTraceContext().isEmpty());
	}
	
	private List<ElasticsearchClient> mockESClients() {
		List<ElasticsearchClient> clients = new ArrayList<>();
		clients.add(mock(ElasticsearchClient.class));
		return clients;
	}

	protected SpanContextInformation reportSpanWithCallTree(long executionTimeMs, String operationName) {
		return reportSpan(executionTimeMs, CallStackElement.createRoot("test"), operationName);
	}

	protected SpanContextInformation reportSpan() {
		return reportSpan(1);
	}

	protected SpanContextInformation reportSpan(long executionTimeMs) {
		return reportSpan(executionTimeMs, "Report Me");
	}

	protected SpanContextInformation reportSpan(long executionTimeMs, String operationName) {
		return reportSpan(executionTimeMs, null, operationName);
	}

	private SpanContextInformation reportSpan(long executionTimeMs, CallStackElement callTree, String operationName) {
		final Tracer tracer = tracingPlugin.getTracer();
		final Span span;
		Tracer.SpanBuilder spanBuilder = tracer.buildSpan(operationName)
				.withStartTimestamp(1);
		spanBuilder = setStartTags(spanBuilder);

		span = spanBuilder
				.start();
		final SpanContextInformation spanContextInformation = SpanContextInformation.forSpan(span);
		spanContextInformation.setCallTree(callTree);
		// implicitly reports
		span.finish(TimeUnit.MILLISECONDS.toMicros(executionTimeMs) + 1);
		return spanContextInformation;
	}

	protected Tracer.SpanBuilder setStartTags(Tracer.SpanBuilder spanBuilder) {
		return spanBuilder
				.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
				.withTag(SpanUtils.OPERATION_TYPE, "http");
	}
}
