package org.stagemonitor.alerting.incident;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.stagemonitor.core.elasticsearch.ElasticsearchClient;

public class ElasticsearchIncidentRepository implements IncidentRepository {

	public static final String BASE_URL = "/stagemonitor/incidents";
	private List<ElasticsearchClient> elasticsearchClients;

	public ElasticsearchIncidentRepository(List<ElasticsearchClient> elasticsearchClients) {
		this.elasticsearchClients = elasticsearchClients;
	}

	@Override
	public Collection<Incident> getAllIncidents() {
		Set<Incident> incidents = new LinkedHashSet<>();
		for(ElasticsearchClient esClient : elasticsearchClients) {
			incidents.addAll(esClient.getAll(BASE_URL, 100, Incident.class));
		}
		return incidents;
	}

	@Override
	public Incident getIncidentByCheckId(String checkId) {
		Incident incident = null;
		for(ElasticsearchClient esClient : elasticsearchClients) {
			incident = esClient.getObject(BASE_URL + "/" + checkId, Incident.class);
			if(incident != null) {
				return incident; //return first match
			}
		}
		return incident;
	}

	@Override
	public boolean deleteIncident(Incident incident) {
		boolean allSuccess = true;
		for(ElasticsearchClient esClient : elasticsearchClients) {
			allSuccess = allSuccess &&  hasNoConflict(esClient.sendRequest("DELETE", BASE_URL + "/" + incident.getCheckId() + getVersionParameter(incident)));
		}
		return allSuccess;
	}

	@Override
	public boolean createIncident(Incident incident) {
		if (incident.getVersion() != 1) {
			throw new IllegalArgumentException("Tried to create an incident with version not equal to 1: " + incident.getVersion());
		}
		return updateIncident(incident);
	}

	@Override
	public boolean updateIncident(Incident incident) {
		boolean allSuccess = true;
		for(ElasticsearchClient esClient : elasticsearchClients) {
			allSuccess = allSuccess &&  hasNoConflict(esClient.sendAsJson("PUT", BASE_URL + "/" + incident.getCheckId() + getVersionParameter(incident), incident));
		}
		return allSuccess;
	}

	@Override
	public void clear() {
		for (Incident incident : getAllIncidents()) {
			deleteIncident(incident);
		}
	}

	private String getVersionParameter(Incident incident) {
		return "?version=" + incident.getVersion() + "&version_type=external";
	}

	private boolean hasNoConflict(int statusCode) {
		return statusCode != 409;
	}

	void setElasticsearchClient(List<ElasticsearchClient> elasticsearchClients) {
		this.elasticsearchClients = elasticsearchClients;
	}
}
