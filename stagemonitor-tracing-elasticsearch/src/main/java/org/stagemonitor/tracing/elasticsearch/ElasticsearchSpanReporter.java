package org.stagemonitor.tracing.elasticsearch;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.elasticsearch.ElasticsearchClient;
import org.stagemonitor.core.util.JsonUtils;
import org.stagemonitor.tracing.SpanContextInformation;
import org.stagemonitor.tracing.reporter.ReadbackSpan;
import org.stagemonitor.tracing.reporter.SpanReporter;
import org.stagemonitor.util.StringUtils;

public class ElasticsearchSpanReporter extends SpanReporter {

	public static final String ES_SPAN_LOGGER = "ElasticsearchSpanReporter";

	private final Logger spanLogger;

	protected CorePlugin corePlugin;
	protected ElasticsearchTracingPlugin elasticsearchTracingPlugin;
	protected List<ElasticsearchClient> elasticsearchClients;
	private static final String SPANS_TYPE = "spans";

	public ElasticsearchSpanReporter() {
		this(LoggerFactory.getLogger(ES_SPAN_LOGGER));
	}

	ElasticsearchSpanReporter(Logger spanLogger) {
		this.spanLogger = spanLogger;
	}

	@Override
	public void init(ConfigurationRegistry configuration) {
		corePlugin = configuration.getConfig(CorePlugin.class);
		elasticsearchTracingPlugin = configuration.getConfig(ElasticsearchTracingPlugin.class);
		elasticsearchClients = corePlugin.getElasticsearchClients();
	}

	@Override
	public void report(SpanContextInformation spanContext, ReadbackSpan readbackSpan) {
		final String spansIndex = "stagemonitor-spans-" + StringUtils.getLogstashStyleDate();
		if (elasticsearchTracingPlugin.isOnlyLogElasticsearchSpanReports()) {
			spanLogger.info(ElasticsearchClient.getBulkHeader("index", spansIndex, SPANS_TYPE) + JsonUtils.toJson(readbackSpan));
		} else {
			for(ElasticsearchClient esClient : elasticsearchClients) {
				esClient.index(spansIndex, SPANS_TYPE, readbackSpan);
			}
		}
	}

	@Override
	public boolean isActive(SpanContextInformation spanContext) {
		final boolean logOnly = elasticsearchTracingPlugin.isOnlyLogElasticsearchSpanReports();
		if(logOnly) {
			return true;
		}
		for(ElasticsearchClient esClient : elasticsearchClients) {
			if(esClient.isElasticsearchAvailable()) {
				return true;
			}
		}
		return false;
	}

}
