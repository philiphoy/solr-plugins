package brightsolid.solr.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.handler.dataimport.Context;
import org.apache.solr.handler.dataimport.DataImporter;
import org.apache.solr.handler.dataimport.RegexTransformer;
import org.apache.solr.handler.dataimport.Transformer;

public class SurnameTransformer extends Transformer  {

  public static final String SURNMANE_COL = "surnameColName";
  public static final String USE_DEFAULT_CHAR = "#";
  public static final String DEFAULT_MATCH = "defaultMatch";
  
  @Override
  public Object transformRow(Map<String, Object> aRow, Context ctx) {
    for (Map<String, String> map : ctx.getAllEntityFields()) {
      String surnameCol = map.get(SURNMANE_COL);
      if(surnameCol==null)
         continue;
      
      String column = map.get(DataImporter.COLUMN);      
      String regex = ctx.replaceTokens(map.get(RegexTransformer.REGEX));
      String regexMatch = ctx.replaceTokens(map.get(DEFAULT_MATCH));

      Object o = aRow.get(surnameCol);
      if (o == null)
        aRow.put(column, USE_DEFAULT_CHAR);
      
      if (o instanceof List) {
        List<String> inputs = (List) o;
        List<String> results = new ArrayList<String>();
        for (String input : inputs) {
          results.add(process(input,regexMatch,regex));
        }
        aRow.put(column, results);
      } else {
        aRow.put(column, process(o,regexMatch,regex));
      }      
    }
    return aRow;
  }

  private String process(Object input,String regexMatch, String regex) {
    if (input == null)
      return USE_DEFAULT_CHAR;
    
    String surname = input.toString();
    surname = surname.trim();
    
    if(surname.length()==0)
       return USE_DEFAULT_CHAR;
    
    Pattern regexp = getPattern(regexMatch);
    Matcher m = regexp.matcher(surname);
    if (m.find() && m.groupCount() > 0) {
      return USE_DEFAULT_CHAR; 
    }
    
    Pattern regexpFirst = getPattern(regex);
    Matcher firstMatch = regexpFirst.matcher(surname);
    if (firstMatch.find() && firstMatch.groupCount() > 0) {
      return firstMatch.group(0).toLowerCase();
    }
    
    return USE_DEFAULT_CHAR;    
  }
  
  private Pattern getPattern(String reStr) {
    Pattern result = PATTERN_CACHE.get(reStr);
    if (result == null) {
      PATTERN_CACHE.put(reStr, result = Pattern.compile(reStr));
    }
    return result;
  }

  private HashMap<String, Pattern> PATTERN_CACHE = new HashMap<String, Pattern>();
}
