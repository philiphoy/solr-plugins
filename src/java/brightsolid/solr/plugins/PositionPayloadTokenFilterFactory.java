package brightsolid.solr.plugins;
        
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import brightsolid.solr.plugins.PositionPayloadTokenFilter;
 

public class PositionPayloadTokenFilterFactory extends TokenFilterFactory{
  
  public PositionPayloadTokenFilterFactory(Map<String, String> args) {
    super(args);    
  }

  public PositionPayloadTokenFilter create(TokenStream input) {
    return new PositionPayloadTokenFilter(input);
  }
}
