package brightsolid.solr.plugins;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.junit.BeforeClass;

public class TestLastNameSearch extends SolrTestCaseJ4{

  @BeforeClass
  public static void beforeTests() throws Exception {
    initCore("config.xml", "schema.xml");

    assertU(adoc("Id","1","name__lname_an","oconnor"));
    assertU(adoc("Id","2","name__lname_an","o'connor"));
    assertU(adoc("Id","3","name__lname_an","o connor"));
    assertU(adoc("Id","4","name__lname_an","o-connor"));
    assertU(adoc("Id","5","name__lname_an","connor"));
    assertU(adoc("Id","6","name__lname_an","o hagan"));
    assertU(adoc("Id","7","name__lname_an","o                         connor"));

    assertU(commit());
  }


	public void testConcat() throws Exception {
	    
	    assertJQ(req("_query_:{!nameQuery useinitial=false f=name__lname }oconnor"),
	            "/response/numFound==5"
	            );
	    
//	    assertQScore(req("_query_:{!nameQuery useinitial=false f=name__lname }oconnor"), 1, (float) 1.0);
	   
	  }

	
  
  public void testWithSpace() throws Exception {
	    
	    assertJQ(req("_query_:\"{!nameQuery useinitial=false f=name__lname }o connor\""),	                      
	            "/response/numFound==5"
	            );
	  }
  
  public void testWithHyphen() throws Exception {
	    
	    assertJQ(req("_query_:\"{!nameQuery useinitial=false f=name__lname }o-connor\""),	                      
	            "/response/numFound==5"
	            );
	  }
  
  public void testWithApostrophe() throws Exception {
	    
	    assertJQ(req("_query_:\"{!nameQuery useinitial=false f=name__lname }o'connor\""),	                      
	            "/response/numFound==5"
	            );
	  }
  
  public void testWithConnor() throws Exception {
	    
	    assertJQ(req("_query_:\"{!nameQuery useinitial=false f=name__lname }connor\""),	                      
	            "/response/numFound==5"
	            );
	  }
  
  public void testWithO() throws Exception {
	    
	    assertJQ(req("_query_:\"{!nameQuery useinitial=false f=name__lname }o hagan\""),	                      
	            "/response/numFound==1"
	            );
	  }
  
  public static void assertQScore(SolrQueryRequest req, int docIdx, float targetScore) {
	      try {
	        String handler = req.getParams().get(CommonParams.QT);
	        SolrQueryResponse resp;
	        try {
	          resp = h.queryAndResponse(handler, req);
	        } catch (Exception e) {
	          throw new RuntimeException(e);
	        }
	        ResultContext resCtx = (ResultContext) resp.getValues().get("response");
	        final DocList docList = resCtx.docs;
	        assertTrue("expected more docs", docList.size() >= docIdx+1);
	        assertTrue("expected scores", docList.hasScores());
	        DocIterator docIterator = docList.iterator();
	        for(int i = -1; i < docIdx; i++) {//loops at least once
	          docIterator.nextDoc();
	        }
	        float gotScore = docIterator.score();
	        assertEquals(gotScore,targetScore, 0.0001);
	      } finally {
	        req.close();
	      }
	    }
    
}
