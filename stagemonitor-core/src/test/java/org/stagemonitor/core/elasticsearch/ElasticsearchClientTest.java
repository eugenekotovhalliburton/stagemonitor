package org.stagemonitor.core.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.stagemonitor.AbstractElasticsearchTest;
import org.stagemonitor.core.util.HttpClient;
import org.stagemonitor.core.util.JsonUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.stagemonitor.core.elasticsearch.ElasticsearchClient.modifyIndexTemplate;

public class ElasticsearchClientTest extends AbstractElasticsearchTest {

	private ListAppender<ILoggingEvent> testAppender;

	@Before
	public void setUp() throws Exception {
		Logger bulkLogger = (Logger) LoggerFactory.getLogger(ElasticsearchClient.BulkErrorReportingResponseHandler.class);
		testAppender = new ListAppender<ILoggingEvent>();
		testAppender.start();
		bulkLogger.addAppender(testAppender);
	}

	@After
	public void tearDown() throws Exception {
		Logger bulkLogger = (Logger) LoggerFactory.getLogger(ElasticsearchClient.BulkErrorReportingResponseHandler.class);
		bulkLogger.detachAppender(testAppender);
	}

	@Test
	public void testGetDashboardForElasticsearch() throws Exception {
		String expected = "{\"user\":\"guest\",\"group\":\"guest\",\"title\":\"Test Title\",\"tags\":[\"jdbc\",\"db\"],\"dashboard\":\"{\\\"title\\\":\\\"Test Title\\\",\\\"editable\\\":false,\\\"failover\\\":false,\\\"panel_hints\\\":true,\\\"style\\\":\\\"dark\\\",\\\"refresh\\\":\\\"1m\\\",\\\"tags\\\":[\\\"jdbc\\\",\\\"db\\\"],\\\"timezone\\\":\\\"browser\\\"}\"}";
		for(ElasticsearchClient esClient: elasticsearchClients) {
			assertEquals(expected, esClient.getDashboardForElasticsearch("Test Dashboard.json").toString());
		}
	}

	@Test
	public void testRequireBoxTypeHotWhenHotColdActive() throws Exception {
		final String indexTemplate = modifyIndexTemplate("stagemonitor-elasticsearch-metrics-index-template.json", 2, null, 0);
		assertTrue(indexTemplate.contains("hot"));
		assertFalse(indexTemplate.contains("number_of_shards"));
		assertFalse(indexTemplate.contains("number_of_replicas"));
	}

	@Test
	public void testSetReplicas() throws Exception {
		final String indexTemplate = modifyIndexTemplate("stagemonitor-elasticsearch-metrics-index-template.json", 0, 0, 0);
		assertFalse(indexTemplate.contains("hot"));
		assertEquals(0, JsonUtils.getMapper().readTree(indexTemplate).get("settings").get("index").get("number_of_replicas").asInt());
		assertFalse(indexTemplate.contains("number_of_shards"));
	}

	@Test
	public void testSetShards() throws Exception {
		final String indexTemplate = modifyIndexTemplate("stagemonitor-elasticsearch-metrics-index-template.json", 0, -1, 1);
		assertFalse(indexTemplate.contains("hot"));
		assertEquals(1, JsonUtils.getMapper().readTree(indexTemplate).get("settings").get("index").get("number_of_shards").asInt());
		assertFalse(indexTemplate.contains("number_of_replicas"));
	}

	@Test
	public void modifyIndexTemplateIntegrationTest() throws Exception {
		for(ElasticsearchClient esclient : elasticsearchClients) {
			esclient.sendMappingTemplateAsync(modifyIndexTemplate("stagemonitor-elasticsearch-metrics-index-template.json", 0, 1, 2), "stagemonitor-elasticsearch-metrics");
			esclient.waitForCompletion();
			refresh();
			esclient.index("stagemonitor-metrics-test", "metrics", Collections.singletonMap("count", 1));
			esclient.waitForCompletion();
			refresh();
			final JsonNode indexSettings = esclient.getJson("/stagemonitor-metrics-test/_settings")
					.get("stagemonitor-metrics-test").get("settings").get("index");
			assertEquals(indexSettings.toString(),1, indexSettings.get("number_of_replicas").asInt());
			assertEquals(indexSettings.toString(),2, indexSettings.get("number_of_shards").asInt());
		}
	}

	@Test
	public void testDontRequireBoxTypeHotWhenHotColdInactive() throws Exception {
		assertFalse(modifyIndexTemplate("stagemonitor-elasticsearch-metrics-index-template.json", 0, 0, 0).contains("hot"));
		assertFalse(modifyIndexTemplate("stagemonitor-elasticsearch-metrics-index-template.json", -1, 0, 0).contains("hot"));
	}

	@Test
	public void testBulkNoRequest() {
		for(ElasticsearchClient esclient : elasticsearchClients) {
			esclient.sendBulk("", new HttpClient.OutputStreamHandler() {
				@Override
				public void withHttpURLConnection(OutputStream os) throws IOException {
					os.write(("").getBytes("UTF-8"));
				}
			});
			assertThat(testAppender.list).hasSize(1);
			final ILoggingEvent event = testAppender.list.get(0);
			assertThat(event.getLevel().toString()).isEqualTo("WARN");
			assertThat(event.getMessage()).startsWith("Error(s) while sending a _bulk request to elasticsearch: {}");
		}
	}

	@Test
	public void testBulkErrorInvalidRequest() {
		for(ElasticsearchClient esclient : elasticsearchClients) {
			esclient.sendBulk("", new HttpClient.OutputStreamHandler() {
				@Override
				public void withHttpURLConnection(OutputStream os) throws IOException {
					os.write(("{ \"update\" : {\"_id\" : \"1\", \"_type\" : \"type1\", \"_index\" : \"index1\", \"_retry_on_conflict\" : 3} }\n" +
							"{ \"doc\" : {\"field\" : \"value\"} }\n").getBytes("UTF-8"));
				}
			});
			assertThat(testAppender.list).hasSize(1);
			final ILoggingEvent event = testAppender.list.get(0);
			assertThat(event.getLevel().toString()).isEqualTo("WARN");
			assertThat(event.getMessage()).startsWith("Error(s) while sending a _bulk request to elasticsearch: {}");
		}
	}

	@Test
	public void testSuccessfulBulkRequest() {
		for(ElasticsearchClient esclient : elasticsearchClients) {
			esclient.sendBulk("", new HttpClient.OutputStreamHandler() {
				@Override
				public void withHttpURLConnection(OutputStream os) throws IOException {
					os.write(("{ \"index\" : { \"_index\" : \"test\", \"_type\" : \"type1\", \"_id\" : \"1\" } }\n" +
							"{ \"field1\" : \"value1\" }\n").getBytes("UTF-8"));
				}
			});
			assertThat(testAppender.list).hasSize(0);
		}
	}

}
