package org.stagemonitor.tracing;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.MeasurementSession;
import org.stagemonitor.core.Stagemonitor;
import org.stagemonitor.core.elasticsearch.ElasticsearchClient;
import org.stagemonitor.core.metrics.metrics2.Metric2Filter;
import org.stagemonitor.core.metrics.metrics2.Metric2Registry;
import org.stagemonitor.core.metrics.metrics2.MetricName;
import org.stagemonitor.tracing.reporter.ReportingSpanEventListener;
import org.stagemonitor.tracing.sampling.SamplePriorityDeterminingSpanEventListener;
import org.stagemonitor.tracing.wrapper.SpanWrappingTracer;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.uber.jaeger.context.TracingUtils;

import io.opentracing.Tracer;

public abstract class AbstractRequestMonitorTest {

	protected CorePlugin corePlugin;
	protected TracingPlugin tracingPlugin;
	protected Metric2Registry registry;
	protected RequestMonitor requestMonitor;
	protected ConfigurationRegistry configuration;
	protected Map<String, Object> tags = new HashMap<>();
	protected SamplePriorityDeterminingSpanEventListener samplePriorityDeterminingSpanInterceptor;
	protected SpanWrappingTracer tracer;
	protected SpanCapturingReporter spanCapturingReporter;

	@Before
	public void before() {
		tracingPlugin = mock(TracingPlugin.class);
		configuration = mock(ConfigurationRegistry.class);
		corePlugin = mock(CorePlugin.class);
		registry = mock(Metric2Registry.class);

		doReturn(corePlugin).when(configuration).getConfig(CorePlugin.class);
		doReturn(tracingPlugin).when(configuration).getConfig(TracingPlugin.class);

		doReturn(true).when(corePlugin).isStagemonitorActive();
		doReturn(1000).when(corePlugin).getThreadPoolQueueCapacityLimit();
		doReturn(new Metric2Registry()).when(corePlugin).getMetricRegistry();
		doReturn(Collections.singletonList("http://mockhost:9200")).when(corePlugin).getElasticsearchUrls();
		List<ElasticsearchClient> esClients = mockESClients();
		for(ElasticsearchClient esclient: esClients) {
			
			doReturn(true).when(esclient).isElasticsearchAvailable();
		}
		doReturn(esClients).when(corePlugin).getElasticsearchClients();
		doReturn(false).when(corePlugin).isOnlyLogElasticsearchMetricReports();

		doReturn(true).when(tracingPlugin).isProfilerActive();

		doReturn(1000000d).when(tracingPlugin).getDefaultRateLimitSpansPerMinute();
		doReturn(mock(ConfigurationOption.class)).when(tracingPlugin).getDefaultRateLimitSpansPerMinuteOption();
		doReturn(mock(ConfigurationOption.class)).when(tracingPlugin).getDefaultRateLimitSpansPerMinuteOption();
		when(tracingPlugin.getDefaultRateLimitSpansPercentOption()).thenReturn(mock(ConfigurationOption.class));
		when(tracingPlugin.getRateLimitSpansPerMinutePercentPerTypeOption()).thenReturn(mock(ConfigurationOption.class));
		when(tracingPlugin.getDefaultRateLimitSpansPercent()).thenReturn(1.0);
		when(tracingPlugin.getRateLimitSpansPerMinutePercentPerType()).thenReturn(Collections.emptyMap());
		when(tracingPlugin.getRateLimitSpansPerMinutePerTypeOption()).thenReturn(mock(ConfigurationOption.class));
		doReturn(mock(ConfigurationOption.class)).when(tracingPlugin).getProfilerRateLimitPerMinuteOption();
		doReturn(mock(Timer.class)).when(registry).timer(any(MetricName.class));
		doReturn(mock(Meter.class)).when(registry).meter(any(MetricName.class));
		requestMonitor = new RequestMonitor(configuration, registry);
		when(tracingPlugin.isLogSpans()).thenReturn(true);
		when(tracingPlugin.getRequestMonitor()).thenReturn(requestMonitor);

		samplePriorityDeterminingSpanInterceptor = new SamplePriorityDeterminingSpanEventListener(configuration);
		final ReportingSpanEventListener reportingSpanEventListener = new ReportingSpanEventListener(configuration);
		spanCapturingReporter = new SpanCapturingReporter();
		reportingSpanEventListener.addReporter(spanCapturingReporter);
		tracer = TracingPlugin.createSpanWrappingTracer(getTracer(), configuration, registry,
				TagRecordingSpanEventListener.asList(tags),
				samplePriorityDeterminingSpanInterceptor, reportingSpanEventListener);
		when(corePlugin.getMeasurementSession()).thenReturn(new MeasurementSession(getClass().getSimpleName(), "test", "test"));
		when(tracingPlugin.getTracer()).then((invocation) -> {
			if (corePlugin.isStagemonitorActive()) {
				return tracer;
			} else {
				return NoopTracer.INSTANCE;
			}
		});
		assertTrue(TracingUtils.getTraceContext().isEmpty());
	}
	
	private List<ElasticsearchClient> mockESClients() {
		List<ElasticsearchClient> clients = new ArrayList<>();
		clients.add(mock(ElasticsearchClient.class));
		return clients;
	}

	protected Tracer getTracer() {
		return new MockTracer();
	}

	@After
	public void after() {
		Stagemonitor.getMetric2Registry().removeMatching(Metric2Filter.ALL);
		Stagemonitor.reset();
		assertTrue(TracingUtils.getTraceContext().isEmpty());
	}
}
