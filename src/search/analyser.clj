(ns search.analyser
  (:require [buddy.core.codecs :refer [bytes->hex]]
            [buddy.core.hash :as bch]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [rdf4j.loader :as l :exclude [-main]]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u]
            [search.vocabulary :as v])
  (:import [search.vocabulary SearchRecord]
           [org.eclipse.rdf4j.model Model IRI Resource Value]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory]
           [org.eclipse.rdf4j.model.vocabulary RDFS SKOS RDF]
           [org.eclipse.rdf4j.query.algebra.evaluation QueryBindingSet]))


(defn normalize-score
  "Multiplies the score by weight defined in matrix based on predicate."
  [score mapping-repository predicate]
  (let [{:keys [predicate weight weight-node]} (v/search-weight mapping-repository predicate)]
    (when-not weight-node
      (log/debugf "Property '%s' has no mapping record." predicate))
    (* score weight)))


(defn process-binding
  "Converts SPARQL result into a map."
  [mapping-repository ^QueryBindingSet b]
  (let [term (-> b
                 (.getValue "tfString")
                 (.stringValue))
        subject (-> b
                    (.getValue "sub")
                    (.stringValue))
        property (-> b
                     (.getValue "property")
                     (.stringValue))
        score (-> b
                  (.getValue "score")
                  (.floatValue)
                  (normalize-score mapping-repository property))
        sub-sc (SearchRecord. term subject property score)]
    (log/debugf "Term: %s -- %s\n" term sub-sc)
    sub-sc))

(defn merge-by-domains [data]
  {:pre [coll? data]}
  (let [domains (seq (set (map #(:subject %) data)))]
    (for [d domains]
      (let [filtered (filter (comp #{d} :subject) data)
            added-score (reduce + (map #(:score %) filtered))]
        (log/tracef "filtered by domain [%s] : %s" d (seq (doall filtered)))
        (v/map->SearchRecord (merge (reduce #(merge %1 %2) filtered) {:score added-score}))
        ))))

(defn load-dataset
  "Process SPARQL results.

  This function process SPARQL query results loading them into `search.vocabulary.SearchRecord` record. 
  If there is many records in the internal map returns with the highest score. 
  "
  [mappings-repository dataset]
  (let [data-structure-seq (map #(process-binding mappings-repository %) dataset)
        added-domains (merge-by-domains data-structure-seq)
        top-one (first (sort v/search-record-comparator added-domains))]
    (when (some? top-one)
      (log/debugf "Load '%s' into mapping repository." (.toString top-one))
      (v/add-to-repository mappings-repository (.asMappingRDF top-one)))))
