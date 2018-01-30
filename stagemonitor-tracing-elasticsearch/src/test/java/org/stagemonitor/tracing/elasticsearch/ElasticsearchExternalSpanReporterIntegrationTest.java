package org.stagemonitor.tracing.elasticsearch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.stagemonitor.AbstractElasticsearchTest;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.elasticsearch.ElasticsearchClient;
import org.stagemonitor.core.metrics.metrics2.Metric2Registry;
import org.stagemonitor.tracing.SpanContextInformation;
import org.stagemonitor.tracing.TracingPlugin;
import org.stagemonitor.tracing.reporter.ReadbackSpan;
import org.stagemonitor.util.IOUtils;

import com.fasterxml.jackson.databind.JsonNode;

import io.opentracing.tag.Tags;

public class ElasticsearchExternalSpanReporterIntegrationTest extends AbstractElasticsearchTest {

	protected ElasticsearchSpanReporter reporter;
	protected TracingPlugin tracingPlugin;
	protected ConfigurationRegistry configuration;

	@Before
	public void setUp() throws Exception {
		this.configuration = mock(ConfigurationRegistry.class);
		this.tracingPlugin = mock(TracingPlugin.class);
		when(configuration.getConfig(CorePlugin.class)).thenReturn(corePlugin);
		when(configuration.getConfig(TracingPlugin.class)).thenReturn(tracingPlugin);
		when(configuration.getConfig(ElasticsearchTracingPlugin.class)).thenReturn(mock(ElasticsearchTracingPlugin.class));
		when(corePlugin.getElasticsearchClients()).thenReturn(elasticsearchClients);
		when(corePlugin.getMetricRegistry()).thenReturn(new Metric2Registry());
		when(tracingPlugin.getDefaultRateLimitSpansPerMinute()).thenReturn(1000000d);
		reporter = new ElasticsearchSpanReporter();
		reporter.init(configuration);
		final String mappingTemplate = IOUtils.getResourceAsString("stagemonitor-elasticsearch-span-index-template.json");
		for(ElasticsearchClient esclient : elasticsearchClients) {
			esclient.sendMappingTemplateAsync(mappingTemplate, "stagemonitor-spans");
			esclient.waitForCompletion();
		}
	}

	@Test
	public void reportTemplateCreated() throws Exception {
		for(ElasticsearchClient esclient : elasticsearchClients) {
			final JsonNode template = esclient.getJson("/_template/stagemonitor-spans").get("stagemonitor-spans");
			Assert.assertEquals("stagemonitor-spans-*", template.get("template").asText());
			Assert.assertEquals(false, template.get("mappings").get("_default_").get("_all").get("enabled").asBoolean());
		}
	}

	@Test
	public void reportSpan() throws Exception {
		for(ElasticsearchClient esclient : elasticsearchClients) {
			reporter.report(mock(SpanContextInformation.class), getSpan(100));
			esclient.waitForCompletion();
			refresh();
			final JsonNode hits = esclient.getJson("/stagemonitor-spans*/_search").get("hits");
			Assert.assertEquals(1, hits.get("total").intValue());
			final JsonNode spanJson = hits.get("hits").elements().next().get("_source");
			Assert.assertEquals("jdbc", spanJson.get("type").asText());
			Assert.assertEquals("SELECT", spanJson.get("method").asText());
			Assert.assertEquals(100, spanJson.get("duration_ms").asInt());
			Assert.assertEquals("SELECT * from STAGEMONITOR where 1 < 2", spanJson.get("db").get("statement").asText());
			Assert.assertEquals("ElasticsearchExternalSpanReporterIntegrationTest#test", spanJson.get("name").asText());
		}
	}

	private ReadbackSpan getSpan(long executionTimeMillis) {
		final ReadbackSpan readbackSpan = new ReadbackSpan();
		readbackSpan.setName("ElasticsearchExternalSpanReporterIntegrationTest#test");
		readbackSpan.setDuration(executionTimeMillis);
		readbackSpan.setTag("type", "jdbc");
		readbackSpan.setTag("method", "SELECT");
		readbackSpan.setTag("db.statement", "SELECT * from STAGEMONITOR where 1 < 2");
		readbackSpan.setTag(Tags.PEER_SERVICE.getKey(), "foo@jdbc:bar");
		return readbackSpan;
	}
}
