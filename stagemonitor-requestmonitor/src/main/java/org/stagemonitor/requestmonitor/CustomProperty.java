package org.stagemonitor.requestmonitor;

import java.util.Map;

/**
 * Interface for Custom property during request creation
 * @author h173799
 *
 */
public interface CustomProperty {
	
	/**
	 * Create new custom property
	 * @return the Entry Propertyname, propertyValue
	 */
	public Map<String, String> getProperties(String signature, Map<String, Object> parameters);

}