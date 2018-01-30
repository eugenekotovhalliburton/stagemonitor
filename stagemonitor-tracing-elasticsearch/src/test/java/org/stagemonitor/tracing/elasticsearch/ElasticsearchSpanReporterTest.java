package org.stagemonitor.tracing.elasticsearch;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.stagemonitor.tracing.SpanContextInformation;
import org.stagemonitor.tracing.reporter.ReadbackSpan;
import org.stagemonitor.tracing.reporter.SpanReporter;
import org.stagemonitor.tracing.utils.SpanUtils;
import org.stagemonitor.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import io.opentracing.tag.Tags;

import static org.junit.Assert.assertEquals;

public class ElasticsearchSpanReporterTest extends AbstractElasticsearchSpanReporterTest {

	private ElasticsearchSpanReporter reporter;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		reporter = new ElasticsearchSpanReporter(spanLogger);
		reporter.init(configuration);
		reportingSpanEventListener.addReporter(reporter);
	}

	@Test
	public void testReportSpan() throws Exception {
		final SpanContextInformation spanContext = reportSpanWithCallTree(1000, "Report Me");

		Mockito.verify(elasticsearchClients.get(0)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
		Assert.assertTrue(reporter.isActive(spanContext));
	}

	@Test
	public void testLogReportSpan() throws Exception {
		Mockito.when(elasticsearchTracingPlugin.isOnlyLogElasticsearchSpanReports()).thenReturn(true);
		final SpanContextInformation spanContext = reportSpanWithCallTree(1000, "Report Me");

		Mockito.verify(elasticsearchClients.get(0), Mockito.times(0)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
		Mockito.verify(spanLogger).info(ArgumentMatchers.startsWith("{\"index\":{\"_index\":\"stagemonitor-spans-" + StringUtils.getLogstashStyleDate() + "\",\"_type\":\"spans\"}}\n"));
		Assert.assertTrue(reporter.isActive(spanContext));
	}

	@Test
	public void testReportSpanDontReport() throws Exception {
		final SpanContextInformation info = reportSpanWithCallTree(1, "Regular Foo");

		Assert.assertTrue(reporter.isActive(info));
		Assert.assertFalse(info.isSampled());
		assertEquals(0, tags.get(Tags.SAMPLING_PRIORITY.getKey()));
	}

	@Test
	public void testElasticsearchExcludeCallTree() throws Exception {
		Mockito.when(tracingPlugin.getExcludeCallTreeFromReportWhenFasterThanXPercentOfRequests()).thenReturn(1d);

		reportSpanWithCallTree(1000, "Report Me");
		reportSpanWithCallTree(500, "Report Me");
		reportSpanWithCallTree(250, "Report Me");

		ArgumentCaptor<ReadbackSpan> spanCaptor = ArgumentCaptor.forClass(ReadbackSpan.class);
		Mockito.verify(elasticsearchClients.get(0), Mockito.times(3)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), spanCaptor.capture());
		ReadbackSpan span = spanCaptor.getValue();
		Assert.assertNull(span.getTags().get(SpanUtils.CALL_TREE_ASCII));
		Assert.assertNull(span.getTags().get(SpanUtils.CALL_TREE_JSON));
	}

	@Test
	public void testElasticsearchDontExcludeCallTree() throws Exception {
		Mockito.when(tracingPlugin.getExcludeCallTreeFromReportWhenFasterThanXPercentOfRequests()).thenReturn(0d);

		reportSpanWithCallTree(250, "Report Me");
		reportSpanWithCallTree(500, "Report Me");
		reportSpanWithCallTree(1000, "Report Me");

		ArgumentCaptor<ReadbackSpan> spanCaptor = ArgumentCaptor.forClass(ReadbackSpan.class);
		Mockito.verify(elasticsearchClients.get(0), Mockito.times(3)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), spanCaptor.capture());
		verifyContainsCallTree(spanCaptor.getValue(), true);
	}

	private void verifyContainsCallTree(ReadbackSpan span, boolean contains) {
		assertEquals(contains, span.getTags().get(SpanUtils.CALL_TREE_ASCII) != null);
		assertEquals(contains, span.getTags().get(SpanUtils.CALL_TREE_JSON) != null);
	}

	@Test
	public void testElasticsearchExcludeFastCallTree() throws Exception {
		Mockito.when(tracingPlugin.getExcludeCallTreeFromReportWhenFasterThanXPercentOfRequests()).thenReturn(0.85d);

		SpanContextInformation spanContext = reportSpanWithCallTree(1000, "Report Me");
		Assert.assertFalse(spanContext.getPostExecutionInterceptorContext().isExcludeCallTree());
		verifyContainsCallTree(spanContext.getReadbackSpan(), true);

		spanContext = reportSpanWithCallTree(250, "Report Me");

		Assert.assertTrue(spanContext.getPostExecutionInterceptorContext().isExcludeCallTree());
		verifyContainsCallTree(spanContext.getReadbackSpan(), false);
	}

	@Test
	public void testElasticsearchDontExcludeSlowCallTree() throws Exception {
		Mockito.when(tracingPlugin.getExcludeCallTreeFromReportWhenFasterThanXPercentOfRequests()).thenReturn(0.85d);

		reportSpanWithCallTree(250, "Report Me");
		reportSpanWithCallTree(1000, "Report Me");

		Mockito.verify(elasticsearchClients.get(0), Mockito.times(2)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.isA(ReadbackSpan.class));
	}

	@Test
	public void testInterceptorServiceLoader() throws Exception {
		Mockito.when(tracingPlugin.getExcludeCallTreeFromReportWhenFasterThanXPercentOfRequests()).thenReturn(0d);

		reportSpanWithCallTree(250, "Report Me");

		ArgumentCaptor<ReadbackSpan> spanCaptor = ArgumentCaptor.forClass(ReadbackSpan.class);
		Mockito.verify(elasticsearchClients.get(0)).index(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), spanCaptor.capture());
		ReadbackSpan span = spanCaptor.getValue();
		Assert.assertTrue((Boolean) span.getTags().get("serviceLoaderWorks"));
	}

	@Test
	public void testLoadedViaServiceLoader() throws Exception {
		List<Class<? extends SpanReporter>> spanReporters = new ArrayList<>();
		ServiceLoader.load(SpanReporter.class).forEach(reporter -> spanReporters.add(reporter.getClass()));
		Assert.assertTrue(spanReporters.contains(ElasticsearchSpanReporter.class));
	}
}
