package brightsolid.solr.plugins;

import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.search.SyntaxError;

public class NameQueryParser extends QParser {

  private float tie = 0.0f;
  private String field;
  private String value;
  private float exactboost = 1.01f;
  private float synboost = 0.8f;
  private float initialboost = 0.42f;
  private float phoneticboost = 0.75f;
  private float nullboost = 0.01f;
  private float fuzzyboost = 0.2f;
  private float wildboost = 0.1f;
  private boolean useexact = false;
  private boolean usefuzzy = false;
  private boolean usephonetic = true;
  private boolean usenull = true;
  private boolean useinitial = true;
  private boolean usesyn = true;
  private boolean isSurname = true;
  private String gendervalue;
  private String genderfield;
  private String postfix ="";
  public NameQueryParser(String arg0, SolrParams arg1, SolrParams arg2, SolrQueryRequest arg3) {
    super(arg0, arg1, arg2, arg3);

    if (getParam("tie") != null) {
      tie = Float.parseFloat(getParam("tie"));
    }
    if (getParam("exactboost") != null) {
      exactboost = Float.parseFloat(getParam("exactboost"));
    }
    if (getParam("synboost") != null) {
      synboost = Float.parseFloat(getParam("synboost"));
    }
    if (getParam("initialboost") != null) {
      initialboost = Float.parseFloat(getParam("initialboost"));
    }
    if (getParam("phoneticboost") != null) {
      phoneticboost = Float.parseFloat(getParam("phoneticboost"));
    }
    if (getParam("nullboost") != null) {
      nullboost = Float.parseFloat(getParam("nullboost"));
    }
    if (getParam("fuzzyboost") != null) {
      fuzzyboost = Float.parseFloat(getParam("fuzzyboost"));
    }
    if (getParam("wildboost") != null) {
      wildboost = Float.parseFloat(getParam("wildboost"));
    }
    if (getParam("useexact") != null) {
      useexact = Boolean.parseBoolean(getParam("useexact"));
    }
    if (getParam("usesyn") != null) {
      usesyn = Boolean.parseBoolean(getParam("usesyn"));
    }
    if (getParam("usefuzzy") != null) {
      usefuzzy = Boolean.parseBoolean(getParam("usefuzzy"));
    }
    if (getParam("usephonetic") != null) {
      usephonetic = Boolean.parseBoolean(getParam("usephonetic"));
    }
    if (getParam("usenull") != null) {
      usenull = Boolean.parseBoolean(getParam("usenull"));
    }
    if (getParam("useinitial") != null) {
      useinitial = Boolean.parseBoolean(getParam("useinitial"));
    }
    if (getParam("isSurname") != null) {
    	isSurname = Boolean.parseBoolean(getParam("isSurname"));
     }
    gendervalue = localParams.get("gendervalue");
    genderfield = localParams.get("genderfield", "Gender__facet_text");
    field = localParams.get(QueryParsing.F);    
    value = localParams.get(QueryParsing.V).trim();
  }

  @Override
  public Query parse() throws SyntaxError {

	if(field.endsWith("_mv")){
    	field = field.substring(0, field.length() - 3);
    	postfix = "_mv";
    }
    BooleanQuery bq = new BooleanQuery();
    Analyzer analyzer = getReq().getSchema().getFieldType(field + "_an" + postfix).getQueryAnalyzer();

    TokenStream stream;
    try {
      stream = analyzer.tokenStream(null, new StringReader(value));
      CharTermAttribute cattr = stream.addAttribute(CharTermAttribute.class);
      stream.reset();
      int position = 0;
      String concatenated = "";
      while (stream.incrementToken()) {
    	String tokenised =  cattr.toString();
    	concatenated += tokenised;
        Query q = CreateDisjunctionOrWildcard(tokenised, position);
        bq.add(new BooleanClause(q, Occur.MUST));
        position++;
      }
      //only add a SHOUlD term when more than one term is tokenised.
      if(position>1){
    	  //position seems to greater than 0 when looking at solr's analysis web ui.
    	  Query q = CreateDisjunctionOrWildcard(concatenated, position - 1);  
    	  BooleanQuery concatBq = new BooleanQuery();
    	  concatBq.add(new BooleanClause(q, Occur.SHOULD));
    	  concatBq.add(bq, Occur.SHOULD);    	 
    	  bq = concatBq;
      }
      stream.end();
      stream.close();
    } catch (IOException e) {
      throw new SyntaxError("Query term could not be split", e);
    }

    return bq;
  }

  private Query CreateDisjunctionOrWildcard(String val, int position) throws SyntaxError {
    if (val.contains("*") || val.contains("?")) {
      if(!isSurname){
    	  if(val.length()==2 && !(val.startsWith("*")||val.startsWith("?"))){
	        String firstChar = val.substring(0, 1);
	        SpanQuery iq = new SpanTermQuery(new Term(field + "_an_initial" + postfix, firstChar));
	        Query siq = new SpanTargetPositionQuery(iq, position);
	        siq = addGenderQuery(siq);
	        siq.setBoost(wildboost);
	        return iq;
	      }
      }     
      Query wq = new WildcardQuery(new Term(field + "_an" + postfix, val));
      wq = addGenderQuery(wq);
      wq.setBoost(wildboost);
      return wq;
    } else {
      return CreateDisjunction(val, position);
    }
  }

  private Query CreateDisjunction (String val, int position) throws SyntaxError {

    DisjunctionMaxQuery dq = new DisjunctionMaxQuery(tie);

    // Add analysed name
    SpanQuery aq = new SpanTermQuery(new Term(field + "_an" + postfix, val));
    Query saq = new SpanTargetPositionQuery(aq, position);
    
    dq.add(saq);

    if(useexact){
      Query eq = addExactMatch(value,field);
      dq.add(eq);
    }

    if (usenull) {
      // Null names
      SpanQuery nq = new SpanTermQuery(new Term(field, "-"));
      Query snq = new SpanTargetPositionQuery(nq, position);
      snq = addGenderQuery(snq);
      snq.setBoost(nullboost);
      dq.add(snq);
    }

    if (val.length() == 1) {
      if (useinitial) {
        // Add initial query ie. x* type query.
        SpanQuery iq = new SpanTermQuery(new Term(field + "_an_initial" + postfix, val));
        Query siq = new SpanTargetPositionQuery(iq, position);
        siq = addGenderQuery(siq);
        siq.setBoost(initialboost);
        dq.add(siq);
      }
    } else {
      if (usesyn) {
        // Add synonyms
        SpanQuery sssq = new SpanTermQuery(new Term(field + "_syn" + postfix, val));
        Query ssq = new SpanTargetPositionQuery(sssq, position);
        ssq.setBoost(synboost);
        dq.add(ssq);
      }

      if (useinitial) {
        // Add initial
        String firstChar = val.substring(0, 1);
        SpanQuery iq = new SpanTermQuery(new Term(field + "_an" + postfix, firstChar));
        Query siq = new SpanTargetPositionQuery(iq, position);
        siq = addGenderQuery(siq);
        siq.setBoost(initialboost);
        dq.add(siq);
      }

      if (usephonetic) {
        // Add phonetics
        
        String pval = null;
        try {
          pval = translatePhonetic(val);
        } catch (SyntaxError e) {
          e.printStackTrace();
        }
        SpanQuery pq = new SpanTermQuery(new Term(field + "_an_rs" + postfix, pval));
        
        Query spq = new SpanTargetPositionQuery(pq, position);
        spq = addGenderQuery(spq);
        spq.setBoost(phoneticboost);
        dq.add(spq);
      }

      if (usefuzzy) {
        // Add fuzzy
        int edits = 1;
        if(val.length()>8){
          edits=2;
        }
        FuzzyQuery fq = new FuzzyQuery(new Term(field + "_an" + postfix, val),edits,2);       
        Query sfq = addGenderQuery(fq);
        sfq.setBoost(fuzzyboost);
        dq.add(sfq);
      }
    }

    return dq;
  }

  private String translatePhonetic(String val ) throws SyntaxError {
    
    Analyzer analyzer = getReq().getSchema().getFieldType(field + "_an_rs" + postfix).getQueryAnalyzer();
    TokenStream stream;
    try {
      stream = analyzer.tokenStream(null, new StringReader(val));
      CharTermAttribute cattr = stream.addAttribute(CharTermAttribute.class); 
      stream.reset();
      if (stream.incrementToken()) {
        val = cattr.toString();
      }
      stream.end();
      stream.close();      
    } catch (IOException e) {
      throw new SyntaxError("Query term could not be split", e);
    }
    return val;
  }

  private Query addExactMatch (String term, String field) throws SyntaxError{
    Analyzer analyzer = getReq().getSchema().getFieldType(field).getQueryAnalyzer();
    TokenStream stream;
    try {
      stream = analyzer.tokenStream(null, new StringReader(term));
      CharTermAttribute cattr = stream.addAttribute(CharTermAttribute.class);
      stream.reset();
      Query q = null;
      if (stream.incrementToken()) {
        String val = cattr.toString();
        q = new TermQuery(new Term(field, val));
        q.setBoost(exactboost);        
      }
      stream.end();
      stream.close();
      return q;
    } catch (IOException e) {
      throw new SyntaxError("Query term could not be analysed", e);
    }
  }
  
  private Query addGenderQuery(Query spq) {

    if (gendervalue != null && !gendervalue.isEmpty()) {
      BooleanQuery bq = new BooleanQuery();
      bq.add(spq, Occur.MUST);
      bq.add(new TermQuery(new Term(genderfield, gendervalue)), Occur.MUST_NOT);
      return bq;
    }
    return spq;
  }
}
