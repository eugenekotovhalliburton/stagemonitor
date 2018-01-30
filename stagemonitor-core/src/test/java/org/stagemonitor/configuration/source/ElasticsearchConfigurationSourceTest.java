package org.stagemonitor.configuration.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.stagemonitor.AbstractElasticsearchTest;
import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.Stagemonitor;
import org.stagemonitor.core.elasticsearch.ElasticsearchClient;
import org.stagemonitor.junit.ConditionalTravisTestRunner;
import org.stagemonitor.junit.ExcludeOnTravis;

import com.codahale.metrics.SharedMetricRegistries;

@RunWith(ConditionalTravisTestRunner.class)
public class ElasticsearchConfigurationSourceTest extends AbstractElasticsearchTest {

	private ElasticsearchConfigurationSource configurationSource;

	@AfterClass
	public static void reset() {
		Stagemonitor.reset();
		SharedMetricRegistries.clear();
	}

	@Before
	public void setUp() throws Exception {
		for(ElasticsearchClient esClient : elasticsearchClients) {
			CorePlugin.sendConfigurationMappingAsync(esClient).get();
			configurationSource = new ElasticsearchConfigurationSource(esClient, "test");
		}
	}

	@Test
	public void testSaveAndGet() throws Exception {
		configurationSource.save("foo.bar", "baz");
		refresh();
		configurationSource.reload();
		assertEquals("baz", configurationSource.getValue("foo.bar"));
	}

	@Test
	public void testGetName() throws Exception {
		assertEquals("Elasticsearch (test)", configurationSource.getName());
	}

	@Test
	public void testIsSavingPersistent() throws Exception {
		assertTrue(configurationSource.isSavingPersistent());
	}

	@Test
	public void testIsSavingPossible() throws Exception {
		assertTrue(configurationSource.isSavingPossible());
	}

	@Test
	@ExcludeOnTravis
	public void testMapping() throws Exception {
		configurationSource.save("foo", "bar");
		refresh();

		final GetMappingsResponse mappings = client.admin().indices().prepareGetMappings("stagemonitor-configuration").setTypes("configuration").get();
		assertEquals(1, mappings.getMappings().size());
		assertEquals("{\"configuration\":{" +
						"\"_all\":{\"enabled\":false}," +
						"\"properties\":{\"configuration\":{\"properties\":{" +
						"\"key\":{\"type\":\"keyword\"}," +
						"\"value\":{\"type\":\"keyword\"}}}}" +
						"}" +
						"}",
				mappings.getMappings().get("stagemonitor-configuration").get("configuration").source().toString());
	}
}
