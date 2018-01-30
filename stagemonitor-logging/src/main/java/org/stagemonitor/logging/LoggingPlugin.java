package org.stagemonitor.logging;

import java.util.List;

import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.StagemonitorPlugin;
import org.stagemonitor.core.elasticsearch.ElasticsearchClient;
import org.stagemonitor.core.grafana.GrafanaClient;

public class LoggingPlugin extends StagemonitorPlugin {

	@Override
	public void initializePlugin(StagemonitorPlugin.InitArguments initArguments) {
		final CorePlugin corePlugin = initArguments.getPlugin(CorePlugin.class);
		final List<ElasticsearchClient> elasticsearchClients = corePlugin.getElasticsearchClients();
		final GrafanaClient grafanaClient = corePlugin.getGrafanaClient();
		if (corePlugin.isReportToElasticsearch()) {
			grafanaClient.sendGrafanaDashboardAsync("grafana/ElasticsearchLogging.json");
			for(ElasticsearchClient esClient : elasticsearchClients) {
				esClient.sendClassPathRessourceBulkAsync("kibana/Logging.bulk");
			}
		}
	}

	@Override
	public void registerWidgetMetricTabPlugins(WidgetMetricTabPluginsRegistry widgetMetricTabPluginsRegistry) {
		widgetMetricTabPluginsRegistry.addWidgetMetricTabPlugin("/stagemonitor/static/tabs/metrics/logging-metrics");
	}
}
