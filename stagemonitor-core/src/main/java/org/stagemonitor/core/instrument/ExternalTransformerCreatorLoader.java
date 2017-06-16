package org.stagemonitor.core.instrument;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalTransformerCreatorLoader {
	private static final List<TransformerCreator> creators = new ArrayList<TransformerCreator>();
	private static final Logger logger = LoggerFactory.getLogger(ExternalTransformerCreatorLoader.class);

	private ExternalTransformerCreatorLoader(){
	};
	
	public static synchronized void addTransformer(TransformerCreator creator){
		if(creators.contains(creator))
			creators.remove(creator);
		creators.add(creator);
	}
	
	public static List<StagemonitorByteBuddyTransformer> getAdditionalTransformers(){
		List<StagemonitorByteBuddyTransformer> list = new ArrayList<StagemonitorByteBuddyTransformer> ();
		for(TransformerCreator creator: creators){
			list.add(creator.createInstance());
		}
		return list;
	}
}
