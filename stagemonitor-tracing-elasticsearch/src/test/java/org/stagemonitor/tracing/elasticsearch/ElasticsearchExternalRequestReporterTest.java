package org.stagemonitor.tracing.elasticsearch;

import com.codahale.metrics.Timer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.stagemonitor.core.metrics.metrics2.MetricName;
import org.stagemonitor.tracing.RequestMonitor;
import org.stagemonitor.tracing.SpanContextInformation;
import org.stagemonitor.tracing.reporter.ReadbackSpan;
import org.stagemonitor.tracing.utils.SpanUtils;

import java.util.Collections;

import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.stagemonitor.core.metrics.metrics2.MetricName.name;

public class ElasticsearchExternalRequestReporterTest extends AbstractElasticsearchSpanReporterTest {

	private ElasticsearchSpanReporter reporter;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		when(tracingPlugin.getDefaultRateLimitSpansPerMinute()).thenReturn(1000000d);
		reporter = new ElasticsearchSpanReporter(spanLogger);
		reporter.init(configuration);
		when(tracingPlugin.getOnlyReportSpansWithName()).thenReturn(Collections.emptyList());
		final RequestMonitor requestMonitor = mock(RequestMonitor.class);
		when(tracingPlugin.getRequestMonitor()).thenReturn(requestMonitor);
		reportingSpanEventListener.addReporter(reporter);
	}

	@Test
	public void testReportSpan() throws Exception {
		when(elasticsearchTracingPlugin.isOnlyLogElasticsearchSpanReports()).thenReturn(false);
		reportSpan();

		Mockito.verify(elasticsearchClients.get(0)).index(ArgumentMatchers.startsWith("stagemonitor-spans-"), ArgumentMatchers.eq("spans"), ArgumentMatchers.any());
		Assert.assertTrue(reporter.isActive(null));
		verifyTimerCreated(1);
	}

	@Test
	public void doNotReportSpan() throws Exception {
		when(elasticsearchTracingPlugin.isOnlyLogElasticsearchSpanReports()).thenReturn(false);
		when(elasticsearchClients.get(0).isElasticsearchAvailable()).thenReturn(false);
		reportSpan();

		Mockito.verify(elasticsearchClients.get(0), Mockito.times(0)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
		Mockito.verify(spanLogger, Mockito.times(0)).info(ArgumentMatchers.anyString());
		Assert.assertFalse(reporter.isActive(null));
		verifyTimerCreated(1);
	}

	@Test
	public void testLogReportSpan() throws Exception {
		when(elasticsearchTracingPlugin.isOnlyLogElasticsearchSpanReports()).thenReturn(true);

		reportSpan();
		Mockito.verify(elasticsearchClients.get(0), Mockito.times(0)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
		Mockito.verify(spanLogger).info(ArgumentMatchers.startsWith("{\"index\":{\"_index\":\"stagemonitor-spans-"));
	}

	@Test
	public void reportSpanRateLimited() throws Exception {
		when(tracingPlugin.getDefaultRateLimitSpansPerMinute()).thenReturn(1d);
		reportSpan();
		Mockito.verify(elasticsearchClients.get(0)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
		reportSpan();
		Mockito.verifyNoMoreInteractions(spanLogger);
		verifyTimerCreated(2);
	}

	@Test
	public void excludeExternalRequestsFasterThan() throws Exception {
		when(tracingPlugin.getExcludeExternalRequestsFasterThan()).thenReturn(100d);

		reportSpan(100);
		Mockito.verify(elasticsearchClients.get(0)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());

		reportSpan(99);
		Mockito.verifyNoMoreInteractions(spanLogger);
		verifyTimerCreated(2);
	}

	@Test
	public void testElasticsearchExcludeFastCallTree() throws Exception {
		when(tracingPlugin.getExcludeExternalRequestsWhenFasterThanXPercent()).thenReturn(0.85d);

		reportSpan(1000);
		Mockito.verify(elasticsearchClients.get(0)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
		reportSpan(250);
		Mockito.verifyNoMoreInteractions(spanLogger);
		verifyTimerCreated(2);
	}

	private void report(ReadbackSpan span) {
		if (reporter.isActive(null)) {
			reporter.report(mock(SpanContextInformation.class), span);
		}
	}

	@Test
	public void testElasticsearchDontExcludeSlowCallTree() throws Exception {
		when(tracingPlugin.getExcludeExternalRequestsWhenFasterThanXPercent()).thenReturn(0.85d);

		reportSpan(250);
		reportSpan(1000);

		Mockito.verify(elasticsearchClients.get(0), Mockito.times(2)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
		verifyTimerCreated(2);
	}

	protected Tracer.SpanBuilder setStartTags(Tracer.SpanBuilder spanBuilder) {
		return spanBuilder
				.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
				.withTag(SpanUtils.OPERATION_TYPE, "jdbc");
	}

	private void verifyTimerCreated(int count) {
		final MetricName timerName = name("response_time")
				.operationType("jdbc")
				.operationName("Report Me")
				.build();
		assertThat(registry.getTimers()).containsKey(timerName);
		final Timer timer = registry.getTimers().get(timerName);
		assertThat(timer.getCount()).isEqualTo(count);

		final MetricName allTimerName = name("response_time")
				.operationType("jdbc")
				.operationName("All")
				.build();
		assertThat(registry.getTimers()).containsKey(allTimerName);
		final Timer allTimer = registry.getTimers().get(allTimerName);
		assertThat(allTimer.getCount()).isEqualTo(count);
	}
}
