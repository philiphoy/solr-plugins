package brightsolid.solr.plugins;

import org.apache.solr.SolrTestCaseJ4;
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
    
}
