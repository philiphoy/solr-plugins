package brightsolid.solr.plugins;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.util.BytesRef;

public final class PositionPayloadTokenFilter extends TokenFilter {	 
	  private final PayloadAttribute payAtt = addAttribute(PayloadAttribute.class);
	  private int Position = 0;
	  public PositionPayloadTokenFilter(TokenStream input) {
	    super(input); 
	  }
	 
	  @Override
	  public boolean incrementToken() throws IOException {
	    if (input.incrementToken()) {	
	    	 Position = Position + 1;
		     final byte[] posn = encodeInt(Position);	
		     final BytesRef pl= new BytesRef(posn);
		     payAtt.setPayload(pl);		    
		     return true;	     
	     
	    } else return false;
	  }
	  
	  @Override
	  public void reset() throws IOException {
	    input.reset();
	    Position = 0;
	  }
	  
	  private static byte[] encodeInt(int payload){
         return encodeInt(payload, new byte[4], 0);
      }
     
	  private static byte[] encodeInt(int payload, byte[] data, int offset){
         data[offset] = (byte)(payload >> 24);
         data[offset + 1] = (byte)(payload >> 16);
         data[offset + 2] = (byte)(payload >>  8);
         data[offset + 3] = (byte) payload;
         return data;
       }
	}