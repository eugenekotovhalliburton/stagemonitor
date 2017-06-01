package org.stagemonitor.requestmonitor;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomPropertyCreatorLoader {
	private static final List<CustomPropertyCreator> creators = new ArrayList<CustomPropertyCreator>();
	private static final Logger logger = LoggerFactory.getLogger(CustomPropertyCreatorLoader.class);

	private CustomPropertyCreatorLoader(){
	};
	
	/**
	 * Add a creator to the list
	 * @param creator The creator to add
	 */
	public static synchronized void addCreator(CustomPropertyCreator creator){
		if(creators.contains(creator))
			creators.remove(creator);
		creators.add(creator);
	}
	
	/**
	 * Get the custom properties by calling all creators
	 */
	public static List<CustomProperty> getCustomProperties(){
		List<CustomProperty> properties = new ArrayList<CustomProperty> ();
		for(CustomPropertyCreator creator : creators){
			properties.add(creator.getInstance());
		}
		return properties;
	}

}