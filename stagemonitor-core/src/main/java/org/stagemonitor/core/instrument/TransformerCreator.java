package org.stagemonitor.core.instrument;

import org.stagemonitor.core.instrument.StagemonitorByteBuddyTransformer;

/**
 * Interface of Transformer creator
 * @author h173799
 *
 */
public interface TransformerCreator {
	
	/**
	 * Create instance
	 * @return a new instance of the transformer
	 */
	public StagemonitorByteBuddyTransformer createInstance();

}
