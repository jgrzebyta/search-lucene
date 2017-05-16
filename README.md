# search-lucene
Clojure-based software for text searching and mapping terms with IRIs.

## Mapping file

Mapping file consists two blocks:
 - mapping terms with subjects. Single subject for single term


 Example:
 
 ```
 @prefix map: <http://rdf.adalab-project/ontology/mapping/> .
 @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
 
 map:d586122a836de73dec223eb0780d25f40bb51269 a map:MappedTerm ;
	map:sha1 "d586122a836de73dec223eb0780d25f40bb51269" ;
	skos:notation "term1" ;
	skos:closeMatch <http://example.org/data#record1> .
 ```
 
 - declare weights for properties


Example:

```
 @prefix map: <http://rdf.adalab-project/ontology/mapping/> .

 :current_weights a map:WeightSet ;
   map:weights [ rdf:predicate :term ; map:weight "1.5"^^xsd:float ] ,
               [ rdf:predicate :term2 ; map:weight "0.5"^^xsd:float ] ,
               [ rdf:predicate :term50 ; map:weight "3"^^xsd:float ] .
```

The text searching feature is delivered by **Apache Lucene** framework.
