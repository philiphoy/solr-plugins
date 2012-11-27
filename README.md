# Brightsolid solr plugins

## Features
* Due to distributed search requirements the plugin disables idf.
* Enables query time boosting on position of term in text.

## Installation
1. get source.
2. build with 'ant compile'.
3. add to solr home lib directory.

## Configure 
1. in solr's schema.xml add the similarity class

```xml
   <similarity class="brightsolid.solr.plugins.BrightSolidSimilarity" />
```
and add the index time filter to analyser where desired.
```xml
   <filter class="brightsolid.solr.plugins.PositionPayloadTokenFilterFactory" />
```
2. in the solr's solrconfig.xml add the custom parser.

```xml
   <queryParser name="payload" class="brightsolid.solr.plugins.PayloadTermQueryPlugin"/>
```

## Use 
In query url include a payload position query.

```
	&q=_query_:"{!payload f=field mult=0.9 posn=2}second_word"
```	

* mult parameter is optional and will multiply the boost score by some float value.
* posn is the target position for the term in the text.

