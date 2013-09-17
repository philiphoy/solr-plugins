# Brightsolid solr plugins

## Features
* Due to distributed search requirements the plugin disables idf.
* Enables query time boosting on position of term in text and a name search algorithm.


## Installation
1. get source.
2. build with 'ant compile'.
3. add to solr home lib directory.

## Configure 
1. in solr's schema.xml add the similarity class

```xml
   <similarity class="brightsolid.solr.plugins.BrightSolidSimilarity" />
```

2. in the solr's solrconfig.xml add the custom parser.

```xml
   <queryParser name="namequery" class="brightsolid.solr.plugins.NameQueryPlugin"/>
```

## Use 
In query url include a payload position query.

```
	&q=_query_:"{!namequery f=field }my name"
```	
* **f**  root field name to use for the search 
* **tie** (0.01) is the tiebreaker for a DisjunctionMax of each clause
* **usesyn** (true) add synonym search
* **synboost** (0.8) is the boost to apply to the synonym subsearch
* **useinitial** (true) add inital search
* **initialboost** (0.2) is the boost to apply to the search for initials of the search term
* **usephonetic** (true) add phonetic subsearch
* **usefuzzy** (true) add fuzzy subsearch
* **fuzzyboost** (0.2) is the boost to apply to the fuzzy subsearch
* **phonboost** (0.1) is boost to apply to the phonetic subserach
* **usenull** (true) add null search (actually search for -)
* **nullboost** (0.01) is the boost to apply to null boost (actually search for -)
* **gendervalue** will add required gender term query to the fuzzy, initial, null and phontic subseraches
* **genderfield** (Gender__facet_text) will add required gender term query to the fuzzy, initial, null and phontic subseraches

In essence the parser works as follows, for each clause in query it will create a DisjunctionMaxQuery of various subqueries with varying boosts
each with an aditional boost for when the position of the term in the field matches the clause in the query. Each disjunction is then added to a boolean query. 

For example the query:

&q=_query_:"{!namequery f=name__fname } my name"

would parse to this form of query:

+( 	spanTargPos(name__fname_an:my,0) | <br/>
	spanTargPos(name__fname_syn:my,0)^0.8 | <br/>
	spanTargPos(name__fname_an:m,0)^0.2 | <br/>
	spanTargPos(name__fname_an_rs:my,0)^0.1 | <br/>
	spanTargPos(SpanMultiTermQueryWrapper(name__fname_an:my~2),0)^0.2 | <br/>
	spanTargPos(name__fname:-,0)^0.01)~0.01 <br/>
 +( spanTargPos(name__fname_an:name,1) | <br/>
	spanTargPos(name__fname_syn:name,1)^0.8 | <br/>
	spanTargPos(name__fname_an:n,1)^0.2 | <br/>
	spanTargPos(name__fname_an_rs:name,1)^0.1 | <br/>
	spanTargPos(SpanMultiTermQueryWrapper(name__fname_an:name~2),1)^0.2 | <br/>
	spanTargPos(name__fname:-,1)^0.01)~0.01 <br/>

