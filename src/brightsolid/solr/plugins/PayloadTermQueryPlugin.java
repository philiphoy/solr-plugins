package brightsolid.solr.plugins;

import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.QParser;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.SolrException;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.payloads.*;
import org.apache.solr.search.QueryParsing;
import org.apache.lucene.index.Term;

public class PayloadTermQueryPlugin extends QParserPlugin {
  public void init(NamedList args) {
  }

  @Override
  public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
    return new QParser(qstr, localParams, params, req) {
      public Query parse() {
        return new PayloadTermQuery(
            new Term(localParams.get(QueryParsing.F), localParams.get(QueryParsing.V)),
            createPayloadFunction(localParams.get("mult"),localParams.get("posn")),
            false);
      }
    };
  }

  private PayloadFunction createPayloadFunction(String mult,String position) {
	if (position == null) {
		throw new SolrException( SolrException.ErrorCode.BAD_REQUEST, "no posn passed.");
	} 
	float multFactor=1;
	if (mult != null) {
		try {
			multFactor = Float.parseFloat(mult);
		} catch (NumberFormatException name) {
			throw new SolrException( SolrException.ErrorCode.BAD_REQUEST, "mult is not a number: " + mult);    	
		}
	} 
	int intPosition;
	try {
		intPosition = Integer.parseInt(position);		
	} catch (NumberFormatException name) {
		throw new SolrException( SolrException.ErrorCode.BAD_REQUEST, "posn is not a number: " + position);    	
	}
	return new PositionPayloadFunction(multFactor,intPosition);
  }
}