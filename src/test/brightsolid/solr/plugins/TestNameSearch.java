package brightsolid.solr.plugins;

import org.apache.solr.SolrTestCaseJ4;
import org.junit.BeforeClass;

public class TestNameSearch extends SolrTestCaseJ4{

  @BeforeClass
  public static void beforeTests() throws Exception {
    initCore("config.xml", "schema.xml");

    assertU(adoc("Id","1","name__fname_an","one","name__lname_an","jones smith"));
    assertU(adoc("Id","2","name__fname_an","one two","name__lname_an","smith-jones"));
    assertU(adoc("Id","3","name__fname_an","two one","name__lname_an","bright","Gender__facet_text","unknown"));
    assertU(adoc("Id","4","name__fname_an","t o","Gender__facet_text","female"));
    assertU(adoc("Id","5","name__fname_an","o t","Gender__facet_text","male"));
    assertU(adoc("Id","6","name__fname_an","threee","Gender__facet_text","male"));
    assertU(adoc("Id","7","name__fname_an","three","name__fname_an_mv","seven"));
    assertU(adoc("Id","8","name__fname_an","-","Gender__facet_text","male"));

    assertU(commit());
  }

  public void testaStar() throws Exception {
	    
	    assertJQ(req("_query_:{!nameQuery useexact=true usesyn=false useinitial=false usephonetic=false usenull=false f=LastName__lname}a*"),
	            "/response/docs/[0]/Id=='7'",	           
	            "/response/numFound==2"
	            );
	  }
  
  public void testMv() throws Exception {
	    
	    assertJQ(req("_query_:{!nameQuery f=name__fname_mv }seven"),
	            "/response/docs/[0]/Id=='7'",	           
	            "/response/numFound==2"
	            );
	  }
  
  public void testSyn() throws Exception {
    
    assertJQ(req("_query_:{!nameQuery f=name__fname }won"),
            "/response/docs/[0]/Id=='1'",
            "/response/docs/[1]/Id=='2'",
            "/response/docs/[2]/Id=='3'",
            
            "/response/numFound==4"
            );
  }
  
  public void testFields() throws Exception {

    assertJQ(req("name__fname_an:one"),
            "/response/docs/[0]/Id=='1'",
            "/response/docs/[1]/Id=='2'"
            );
    assertJQ(req("name__fname_an_initial:o"),
            "/response/numFound==5"
            );
    assertJQ(req("name__fname_syn:too"),
            "/response/docs/[0]/Id=='2'",
            "/response/numFound==2"
            );
    assertJQ(req("name__fname_an_rs:thre"),
            "/response/docs/[0]/Id=='6'",
            "/response/numFound==2"
            );
    assertJQ(req("name__fname_an_initial: O       "),
            "/response/numFound==5"
            );
  }

  public void testNoMatch() throws Exception {
      
      assertJQ(req("_query_:{!nameQuery f=name__lname }brite"),
              "/response/numFound==0"
              );
  }
  
  public void testNameSearchWithWildcard() throws Exception {

    assertJQ(req("_query_:\"{!nameQuery f=name__fname }one tw*\""),
            "/response/docs/[0]/Id=='2'",
            "/response/numFound==2"
            );

    assertJQ(req("_query_:\"{!nameQuery f=name__fname }tw* one\""),
            "/response/docs/[0]/Id=='3'",
            "/response/numFound==2"
            );

    assertJQ(req("_query_:{!nameQuery f=name__fname }tw*"),
            "/response/docs/[0]/Id=='2'",
            "/response/numFound==2"
            ); 

    assertJQ(req("_query_:{!nameQuery f=name__fname }tw?"),
            "/response/docs/[0]/Id=='2'",
            "/response/numFound==2"
            );

    assertJQ(req("_query_:{!nameQuery usefuzzy=true f=name__fname }t*"),
            "/response/docs/[0]/Id=='2'",
            "/response/numFound==6"
            );
  }

  public void testNameSearch() throws Exception {
    
    assertJQ(req("_query_:\"{!nameQuery f=name__lname }smith jones\""), 
            "/response/docs/[0]/Id=='2'",
            "/response/docs/[1]/Id=='1'",
            "/response/numFound==2"
            );

    assertJQ(req("_query_:\"{!nameQuery f=name__fname }\""),           
            "/response/numFound==0"
            );

    assertJQ(req("_query_:\"{!nameQuery f=name__fname }o t\""),
            "/response/docs/[0]/Id=='5'",
            "/response/docs/[1]/Id=='4'",
            "/response/docs/[2]/Id=='2'",
            "/response/docs/[3]/Id=='3'",
            "/response/docs/[4]/Id=='8'",
            "/response/numFound==5"
            );

    assertJQ(req("_query_:\"{!nameQuery f=name__fname }o two\""),
            "/response/docs/[0]/Id=='2'",
            "/response/docs/[1]/Id=='5'",
            "/response/docs/[2]/Id=='3'",
            "/response/docs/[3]/Id=='4'",
            "/response/docs/[4]/Id=='8'",
            "/response/numFound==5"
            );

    assertJQ(req("_query_:\"{!nameQuery f=name__fname }two one\""),
            "/response/docs/[0]/Id=='3'",
            "/response/docs/[1]/Id=='2'",
            "/response/docs/[2]/Id=='4'",
            "/response/docs/[3]/Id=='5'",
            "/response/docs/[4]/Id=='8'",
            "/response/numFound==5"
            );

    assertJQ(req("_query_:{!nameQuery f=name__fname }one"),
            "/response/docs/[0]/Id=='1'",
            "/response/docs/[1]/Id=='2'",
            "/response/docs/[2]/Id=='3'",
            "/response/docs/[3]/Id=='5'",
            "/response/docs/[4]/Id=='4'",
            "/response/numFound==6"
            );

    assertJQ(req("_query_:{!nameQuery f=name__fname }three"),
            "/response/docs/[0]/Id=='7'",
            "/response/numFound==5"
            );

    assertJQ(req("_query_:{!nameQuery usefuzzy=false usephonetic=false f=name__fname }three"),
            "/response/docs/[0]/Id=='7'",
            "/response/numFound==4"
            );
    }

    public void testNameSearchWithGender() throws Exception {
        
      assertJQ(req("_query_:{!nameQuery gendervalue=male f=name__fname }one"),
              "/response/numFound==4"
              ); 

      assertJQ(req("_query_:{!nameQuery gendervalue=female f=name__fname }one"),
              "/response/numFound==5"
              );  

      assertJQ(req("_query_:{!nameQuery gendervalue=female f=name__fname }three"),
              "/response/docs/[0]/Id=='7'",
              "/response/numFound==4"
              );

      assertJQ(req("_query_:{!nameQuery gendervalue=male f=name__fname }three"),
              "/response/docs/[0]/Id=='7'",
              "/response/numFound==2"
              );
    }
}
