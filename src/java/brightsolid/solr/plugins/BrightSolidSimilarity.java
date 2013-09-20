package brightsolid.solr.plugins;

import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.util.BytesRef;

public class BrightSolidSimilarity extends DefaultSimilarity {
  
	@Override
	public float queryNorm(float sumOfSquaredWeights) {
	    return 1.0f;
	  }
	
	@Override
    public float idf(long docFreq, long numDocs) {
        return 1.0f;
    }

	@Override
	public float scorePayload(int doc, int start, int end, BytesRef payload){		
		return (float)decodeInt( payload.bytes, payload.offset);
	}
	
	public static final int decodeInt(byte [] bytes, int offset){
       return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
		     | ((bytes[offset + 2] & 0xFF) <<  8) |  (bytes[offset + 3] & 0xFF);
	}
}