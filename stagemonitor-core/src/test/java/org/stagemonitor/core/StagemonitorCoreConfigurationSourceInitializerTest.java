package org.stagemonitor.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.ConfigurationSource;
import org.stagemonitor.configuration.source.SimpleSource;
import org.stagemonitor.core.elasticsearch.ElasticsearchClient;
import org.stagemonitor.core.util.HttpClient;

public class StagemonitorCoreConfigurationSourceInitializerTest {

	private StagemonitorCoreConfigurationSourceInitializer initializer = new StagemonitorCoreConfigurationSourceInitializer();
	private final ConfigurationRegistry configuration = Mockito.mock(ConfigurationRegistry.class);
	private final CorePlugin corePlugin = Mockito.mock(CorePlugin.class);

	@Before
	public void setUp() throws Exception {
		when(corePlugin.getElasticsearchConfigurationSourceProfiles()).thenReturn(Collections.singletonList("test"));
		when(corePlugin.getThreadPoolQueueCapacityLimit()).thenReturn(1000);
		when(configuration.getConfig(CorePlugin.class)).thenReturn(corePlugin);
		List<ElasticsearchClient> esClients = new ArrayList<>();
		esClients.add(new ElasticsearchClient(corePlugin, new HttpClient(), -1, ""));
		when(corePlugin.getElasticsearchClients()).thenReturn(esClients);
	}

	@Test(expected = IllegalStateException.class)
	public void testEsDownDeactivate() throws Exception {
		when(corePlugin.isDeactivateStagemonitorIfEsConfigSourceIsDown()).thenReturn(true);

		initializer.onConfigurationInitialized(new StagemonitorConfigurationSourceInitializer.ConfigInitializedArguments(configuration));
	}

	@Test
	public void testEsDown() throws Exception {
		when(corePlugin.isDeactivateStagemonitorIfEsConfigSourceIsDown()).thenReturn(false);

		initializer.onConfigurationInitialized(new StagemonitorConfigurationSourceInitializer.ConfigInitializedArguments(configuration));

		verify(configuration).addConfigurationSourceAfter(any(ConfigurationSource.class), eq(SimpleSource.class));
	}
}
