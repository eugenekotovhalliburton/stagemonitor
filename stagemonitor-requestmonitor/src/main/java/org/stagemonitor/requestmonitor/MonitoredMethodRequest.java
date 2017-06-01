package org.stagemonitor.requestmonitor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.stagemonitor.core.configuration.Configuration;

public class MonitoredMethodRequest implements MonitoredRequest<RequestTrace> {
	
	private final static String os = System.getProperty("os.name");
	private final static String user = System.getProperty("user.name");

	private final String methodSignature;
	private final MethodExecution methodExecution;
	private final Map<String, Object> parameters;
	private final RequestMonitorPlugin requestMonitorPlugin;

	public MonitoredMethodRequest(Configuration configuration, String methodSignature, MethodExecution methodExecution) {
		this(configuration, methodSignature, methodExecution, null);
	}

	public MonitoredMethodRequest(Configuration configuration, String methodSignature, MethodExecution methodExecution, Map<String, Object> parameters) {
		this.requestMonitorPlugin = configuration.getConfig(RequestMonitorPlugin.class);
		this.methodSignature = methodSignature;
		this.methodExecution = methodExecution;
		this.parameters = parameters;
	}

	@Override
	public String getInstanceName() {
		return null;
	}

	@Override
	public RequestTrace createRequestTrace() {
		RequestTrace requestTrace = new RequestTrace(UUID.randomUUID().toString());
		requestTrace.setName(methodSignature);
		if (parameters != null && parameters.size() > 0) {
			Map<String, String> params = new LinkedHashMap<String, String>();
//			String errorStack = null;
			for (Map.Entry<String, Object> entry : parameters.entrySet()) {
				String valueAsString;
				try {
					Object obj = entry.getValue();
//					if (obj instanceof Exception)
//						errorStack = printExceptionStackTrace((Exception) obj);
					valueAsString = String.valueOf(obj);
				}
				catch (Exception e) {
					valueAsString = "[unavailable (" + e.getMessage() + ")]";
				}
				params.put(entry.getKey(), valueAsString);
			}
			requestTrace.setParameters(RequestMonitorPlugin.getSafeParameterMap(params, requestMonitorPlugin.getConfidentialParameters()));
//			requestTrace.addCustomProperty("caught_error_trace", errorStack);
			// sytem variables
			requestTrace.addCustomProperty("userName", user);
			requestTrace.addCustomProperty("system", os);
			
			Map<String, String> properties = new LinkedHashMap<String, String>();
			for (CustomProperty property : CustomPropertyCreatorLoader.getCustomProperties()){
				properties.putAll(property.getProperties(methodSignature, parameters));
			}
						
			for (Entry<String, String> entry : properties.entrySet()){
				requestTrace.addCustomProperty(entry.getKey(), entry.getValue());
			}
		}
		return requestTrace;
	}
	
	/**
	 * Create stack trace for an exception
	 */
	private String printExceptionStackTrace(Exception e){
		StringBuilder sb = new StringBuilder();
		sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append('\n');
		for(StackTraceElement elt : e.getStackTrace()){
			sb.append("        at ").append(elt.toString()).append('\n');
		}
		return sb.toString();
	}

	@Override
	public Object execute() throws Exception {
		return methodExecution.execute();
	}

	@Override
	public void onPostExecute(RequestMonitor.RequestInformation<RequestTrace> requestTrace) {
	}

	/**
	 * In a Method execution context, we only want to monitor the topmost monitored (forwarding) method call.
	 * <p/>
	 * Example:<br/>
	 * Suppose, we have three methods: monitored1(), monitored2() and notMonitored().
	 * <pre><code>
	 * public void monitored1() {
	 *     monitored2();
	 * }
	 * public void monitored2() {
	 *     notMonitored();
	 * }
	 * public void notMonitored() {}
	 * </code></pre>
	 * The first two Methods are monitored with a {@link RequestMonitor}.<br/>
	 * If method1() is called, we only want to collect metrics for method1() and not for method2().<br/>
	 * If method2() is called, we want to collect metrics for that method.<br/>
	 * If notMonitored() is called directly, we don't want to collect metrics.
	 *
	 * @return false
	 */
	@Override
	public boolean isMonitorForwardedExecutions() {
		return false;
	}

	public interface MethodExecution {
		Object execute() throws Exception;
	}
}
