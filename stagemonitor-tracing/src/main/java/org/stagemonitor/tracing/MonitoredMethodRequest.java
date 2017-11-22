package org.stagemonitor.tracing;

import com.uber.jaeger.context.TracingUtils;

import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.tracing.utils.SpanUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

public class MonitoredMethodRequest extends MonitoredRequest {

	public static final String OP_TYPE_METHOD_INVOCATION = "method_invocation";
	private final String methodSignature;
	private final MethodExecution methodExecution;
	private final Map<String, Object> parameters;
	private final TracingPlugin tracingPlugin;
	
	private static long sessionStartTime = -1;

	public MonitoredMethodRequest(ConfigurationRegistry configuration, String methodSignature, MethodExecution methodExecution) {
		this(configuration, methodSignature, methodExecution, null);
	}

	public MonitoredMethodRequest(ConfigurationRegistry configuration, String methodSignature, MethodExecution methodExecution, Map<String, Object> parameters) {
		this.tracingPlugin = configuration.getConfig(TracingPlugin.class);
		this.methodSignature = methodSignature;
		this.methodExecution = methodExecution;
		this.parameters = parameters;
	}

	private Map<String, String> getSafeParameterMap(Map<String, Object> parameters) {
		if (parameters == null) {
			return null;
		}
		Map<String, String> params = new LinkedHashMap<String, String>();
		for (Map.Entry<String, Object> entry : parameters.entrySet()) {
			String valueAsString;
			try {
				valueAsString = String.valueOf(entry.getValue());
			}
			catch (Exception e) {
				valueAsString = "[unavailable (" + e.getMessage() + ")]";
			}
			params.put(entry.getKey(), valueAsString);
		}
		return TracingPlugin.getSafeParameterMap(params, tracingPlugin.getConfidentialParameters());
	}

	@Override
	public Span createSpan() {
		final Tracer tracer = tracingPlugin.getTracer();
		final Tracer.SpanBuilder spanBuilder;
		if (!TracingUtils.getTraceContext().isEmpty()) {
			spanBuilder = tracer.buildSpan(methodSignature)
					.asChildOf(TracingUtils.getTraceContext().getCurrentSpan())
					.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
		} else {
			spanBuilder = tracer.buildSpan(methodSignature)
					.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
		}
		spanBuilder.withTag(SpanUtils.OPERATION_TYPE, OP_TYPE_METHOD_INVOCATION);
		final Span span = spanBuilder.start();
		SpanUtils.setParameters(span, getSafeParameterMap(parameters));
		SpanUtils.setCustomProperties(span, methodSignature, parameters);
		span.setTag("session_elapsed_time_ms", (System.currentTimeMillis() - getSessionStartTime()));
		return span;
	}

	private static long getSessionStartTime() {
		if(sessionStartTime == -1) {
			sessionStartTime = fetchSessionStartTime();
		}
		return sessionStartTime;
	}

	private static long fetchSessionStartTime() {
		long startTime = System.currentTimeMillis();
    	String sessionId = System.getProperty("dsg_session_id");
		if(sessionId != null) {
			try {
				String timeStampStr = sessionId.substring(sessionId.lastIndexOf('_') + 1);
				startTime = Long.parseLong(timeStampStr);
			} catch(Exception nfe) {
				//do nothing
			}
		}
		return startTime;
	}

	@Override
	public void execute() throws Exception {
		methodExecution.execute();
	}

	public interface MethodExecution {
		void execute() throws Exception;
	}
}
