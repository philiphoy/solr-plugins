# Brightsolid solr plugins

## Features
* Due to distributed search requirements the plugin disables idf.
* The plugin enables query time boosting on the position of a term in text and implements a name search query parser.


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
In query url include a name query, for example here it uses the nested query syntax.

```
	&q=_query_:"{!namequery f=field }my name"
```	
#### Available local params that you can pass
* **f**  root field name to use for the search 
* **tie** (0.01) the tiebreaker used by the DisjunctionMaxQuery of each clause

* **usesyn** (true) add synonym search
* **synboost** (0.8) the boost to apply to the synonym subsearch
* **useinitial** (true) add inital search
* **initialboost** (0.2) the boost to apply to the search for initials of the search term
* **usephonetic** (true) add phonetic subsearch
* **phoneticboost** (0.1) boost to apply to the phonetic subsearch
* **usefuzzy** (true) add fuzzy subsearch
* **fuzzyboost** (0.2) the boost to apply to the fuzzy subsearch
* **usenull** (true) add null search (actually search for -)
* **nullboost** (0.01) the boost to apply to null boost (actually search for -)
* **gendervalue** will add required gender term query to the fuzzy, initial, null and phontic subseraches
* **genderfield** (Gender__facet_text) will add required gender term query to the fuzzy, initial, null and phontic subseraches

In essence the parser works as follows, for each clause in the query it will create a DisjunctionMaxQuery containing a number of subqueries, each with varying boosts,
there is also an aditional boost for where the position of the term in the field matches the clause in the query. Each disjunction is then added to a boolean must occur query. 

For example the query:

&q=_query_:"{!namequery f=name__fname } my name"

would parse to this form of query:


 +( spanTargPos(name__fname_an:my,0) |<br/>  _//straight search, target position 0_ <br/>
	spanTargPos(name__fname_syn:my,0)^0.8 | <br/>_//synonym search, target position 0, boost 0.8_ <br/>
	spanTargPos(name__fname_an:m,0)^0.2 |<br/> _//initial search, target position 0, boost 0.2_ <br/>
	spanTargPos(name__fname_an_rs:my,0)^0.1 |<br/> _//phonetic search, target position 0, boost 0.1_ <br/>
	spanTargPos(SpanMultiTermQueryWrapper(name__fname_an:my~2),0)^0.2<br/> _//fuzzy search, target position 0, boost 0.2_  <br/>
	spanTargPos(name__fname:-,0)^0.01)~0.01 | <br/> _//null search, target position 0, boost 0.01_ <br/>
 +( spanTargPos(name__fname_an:name,1) | <br/>
	spanTargPos(name__fname_syn:name,1)^0.8 | <br/>
	spanTargPos(name__fname_an:n,1)^0.2 | <br/>
	spanTargPos(name__fname_an_rs:name,1)^0.1 | <br/>
	spanTargPos(SpanMultiTermQueryWrapper(name__fname_an:name~2),1)^0.2 | <br/>
	spanTargPos(name__fname:-,1)^0.01)~0.01 <br/>

