package org.stagemonitor.tracing;

/**
 * Interface for Custom property creator during request creation
 * @author h173799
 *
 */
public interface CustomPropertyCreator {

	/**
	 * Create the instance of the custom property
	 */
	public CustomProperty getInstance();
}
