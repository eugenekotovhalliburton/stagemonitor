package org.stagemonitor.tracing;

import java.util.Map;

/**
 * Interface for Custom property during request creation
 * @author h173799
 *
 */
public interface CustomProperty {
	
	/**
	 * Create new custom property
	 * @return the Entry Property name, propertyValue
	 */
	public Map<String, String> getProperties(String signature, Map<String, Object> parameters);

}
