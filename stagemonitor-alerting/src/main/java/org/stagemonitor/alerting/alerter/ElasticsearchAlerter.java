package org.stagemonitor.alerting.alerter;

import org.stagemonitor.core.CorePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.core.util.HttpClient;
import org.stagemonitor.util.StringUtils;

public class ElasticsearchAlerter extends Alerter {

	private CorePlugin corePlugin;
	private HttpClient httpClient;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ElasticsearchAlerter(ConfigurationRegistry configuration, HttpClient httpClient) {
		this.corePlugin = configuration.getConfig(CorePlugin.class);
		this.httpClient = httpClient;
	}

	@Override
	public void alert(AlertArguments alertArguments) {
		String target = alertArguments.getSubscription().getTarget();
		if (StringUtils.isEmpty(target)) {
			target = "/stagemonitor/alerts";
		}
		for(String urlStr: corePlugin.getElasticsearchUrls()) {
			try {
				httpClient.sendAsJson("POST", urlStr + target, alertArguments.getIncident());
			} catch (Exception e) {
				logger.error("Could not send data to server URL: " + urlStr, e);
			}
		}
	}

	@Override
	public String getAlerterType() {
		return "Elasticsearch";
	}

	@Override
	public boolean isAvailable() {
		return !corePlugin.getElasticsearchUrls().isEmpty();
	}

	@Override
	public String getTargetLabel() {
		return "Index";
	}
}
