package org.stagemonitor.core;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.ConfigurationSource;
import org.stagemonitor.configuration.source.SimpleSource;
import org.stagemonitor.core.elasticsearch.ElasticsearchClient;
import org.stagemonitor.core.metrics.metrics2.ElasticsearchReporter;
import org.stagemonitor.core.metrics.metrics2.Metric2Registry;

public class CorePluginTest {

	/*
	 * This test is irrelevant now because more than one url is supported
	 * */
//	@Test
//	public void testCycleElasticsearchUrls() throws Exception {
//		CorePlugin corePlugin = new ConfigurationRegistry(
//				Collections.singletonList(new CorePlugin()),
//				Collections.<ConfigurationSource>singletonList(new SimpleSource("test")
//						.add("stagemonitor.reporting.elasticsearch.url", "http://bla:1/,http://bla:2,http://bla:3")),
//				null).getConfig(CorePlugin.class);
//
//		assertEquals("http://bla:1", corePlugin.getElasticsearchUrl());
//		assertEquals("http://bla:2", corePlugin.getElasticsearchUrl());
//		assertEquals("http://bla:3", corePlugin.getElasticsearchUrl());
//		assertEquals("http://bla:1", corePlugin.getElasticsearchUrl());
//		assertEquals("http://bla:2", corePlugin.getElasticsearchUrl());
//		assertEquals("http://bla:3", corePlugin.getElasticsearchUrl());
//	}

	@Test
	public void testNoElasticsearchUrl() throws Exception {
		CorePlugin corePlugin = new ConfigurationRegistry(
				Collections.singletonList(new CorePlugin()),
				Collections.<ConfigurationSource>singletonList(new SimpleSource("test")),
				null).getConfig(CorePlugin.class);

		assertEquals(true, corePlugin.getElasticsearchUrls().isEmpty());
	}

	@Test
	public void testOnlyLogElasticsearchMetricReports() throws Exception {
		Metric2Registry registry = new Metric2Registry();
		CorePlugin corePlugin = new CorePlugin(getESClientList());
		ConfigurationRegistry configuration = new ConfigurationRegistry(
				Collections.singletonList(corePlugin),
				Collections.<ConfigurationSource>singletonList(new SimpleSource("test")
					.add("stagemonitor.reporting.elasticsearch.onlyLogElasticsearchMetricReports", "true")),
				null);

		corePlugin.registerReporters(registry, configuration, new MeasurementSession("OnlyLogElasticsearchMetricReportsTest", "test", "test"));

		boolean found = false;
		for (Closeable c : corePlugin.getReporters()) {
			found |= (c instanceof ElasticsearchReporter);
		}
		Assert.assertTrue("No ElasticsearchReporter found", found);
	}
	
	private List<ElasticsearchClient> getESClientList() {
		ElasticsearchClient mock = mock(ElasticsearchClient.class);
		List<ElasticsearchClient> list = new ArrayList<>();
		list.add(mock);
		return list;
	}
}
