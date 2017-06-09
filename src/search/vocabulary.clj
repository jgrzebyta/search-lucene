(ns search.vocabulary
  (:require [buddy.core.codecs :refer [bytes->hex]]
            [buddy.core.hash :as bch]
            [clojure.tools.logging :as log]
            [clojure.string :as cs]
            [rdf4j.repository :as r]
            [rdf4j.sparql.processor :as s]
            [rdf4j.utils :as u])
  (:import [java.lang Iterable]
           [java.io OutputStreamWriter]
           [org.eclipse.rdf4j.model Model IRI Value BNode Statement Namespace]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory LinkedHashModel]
           [org.eclipse.rdf4j.model.util Models]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS SKOS XMLSchema]
           [org.eclipse.rdf4j.repository.sail SailRepository]
           [org.eclipse.rdf4j.rio Rio RDFFormat WriterConfig]
           [org.eclipse.rdf4j.rio.helpers BasicWriterSettings]))

(declare find-mapping-rq find-weights-rq)

(def vf (u/value-factory))

;; Mapping vocabulary

(def NS-INST "http://rdf.adalab-project/ontology/mapping/")

(def ^IRI MappedTerm (.createIRI vf NS-INST "MappedTerm"))

(def ^IRI SHA1 (.createIRI vf NS-INST "sha1"))

(def ^IRI WEIGHT (.createIRI vf NS-INST "weight"))

(def ^IRI WEIGHT-SET (.createIRI vf NS-INST "WeightSet"))



(defprotocol AsMappingRDF
  "Returns RDF representation of mapping entity"
  (asMappingRDF ^Model [this]))


(defrecord SearchRecord [^String term ^String subject ^String property ^String value ^Float score]

  AsMappingRDF
  (asMappingRDF [this]
    (let [term-sha1 (-> term
                        (bch/sha1)
                        (bytes->hex))
          map-instance (.createIRI vf NS-INST term-sha1)
          to-return (-> (LinkedHashModelFactory.)
                        (.createEmptyModel))]
      (.add to-return map-instance RDF/TYPE MappedTerm (r/context-array))
      (.add to-return map-instance SHA1 (.createLiteral vf term-sha1) (r/context-array))
      (.add to-return map-instance SKOS/NOTATION (.createLiteral vf term XMLSchema/STRING) (r/context-array))
      (.add to-return map-instance SKOS/CLOSE_MATCH (.createIRI vf subject) (r/context-array))
      to-return))
  Object
  (toString [this]
    (pr-str this)))


(def search-record-comparator
  "Define comparator for `SearchRecord` by `score` field."
  (comparator (fn [x y]
                (> (get x :score) (get y :score)))))


(defn record-valuep?
  "Predicate checks if record contains given `value`. 

  Useful for filtering."
  [^String value ^SearchRecord r]
  (= (cs/lower-case value)
     (cs/lower-case (get r :value))))


(defn print-repository
  "Prints a model to Turtle format."
  [^SailRepository repository]
  (let [model (-> (LinkedHashModelFactory.)
                  (.createEmptyModel))                                      ;; It must go through the Model because set of                                                                         ;; Rio#write(Iterable<Statement>, ...) methods
        writer-config (doto                                                 ;; checks if the iterable is Method.
                          (WriterConfig.)
                        (.set BasicWriterSettings/PRETTY_PRINT true)
                        (.set BasicWriterSettings/RDF_LANGSTRING_TO_LANG_LITERAL false))]
    ;; copy all namespaces from repository
    (doseq [^Namespace ns (r/get-all-namespaces repository)]
      (.setNamespace model ns))
    ;; add default namespaces to model
    (.setNamespace model SKOS/NS)
    (.setNamespace model RDF/NS)
    (.setNamespace model XMLSchema/NS)
    (.setNamespace model "map" NS-INST)
    (.addAll model (r/get-all-statements repository))
  (Rio/write model (OutputStreamWriter. System/out) RDFFormat/TURTLE writer-config)))


(defn search-mapping ^Value [^SailRepository mapping ^String term]
  (s/with-sparql [:sparql find-mapping-rq :result rs :repository mapping :binding {:in_term term}]
    (cond
      (> (count rs) 1) (throw (ex-info "Multiple mappings for single term." {:term term :mappings (doall rs)}))
      (= (count rs) 0) nil
      :default {:subject (-> (first rs)
                             (.getValue "subj")
                             (.stringValue))
                :mapping-node (-> (first rs)
                                  (.getValue "node"))
                :term (-> (first rs)
                          (.getValue "term")
                          (.stringValue))})))

(defn search-weight [^SailRepository mapping property]
  (let [prop-str (if (instance? Value property) (.stringValue property) property)]
    (s/with-sparql [:sparql find-weights-rq :result rs :repository mapping :binding {:in_prop prop-str}]
      (cond
        (> (count rs) 1) (throw (ex-info "Multiple weights for single property." {:property prop-str :mappings (doall rs)}))
        (= (count rs) 0) {:weight 1.0 :predicate property}
        :default {:predicate (-> (first rs)
                                  (.getValue "prop"))
                  :weight (-> (first rs)
                              (.getValue "weight")
                              (.floatValue))
                  :weight-node (-> (first rs)
                                 (.getValue "weight_node"))}))))


(defn add-to-repository
  "Add `statements` from `Iterable`<`Statement`> type into `SailRepository`.
   Method does loading within transaction.
"
  [^SailRepository repository ^Iterable statements]
  {:pre [(instance? Iterable statements)]}
  
  (r/with-open-repository [cnx repository]
    (try
      (.begin cnx)
      (.add cnx statements (r/context-array))
      (catch Exception e
        (do
          (.rollback cnx)
          (throw (ex-info "Some error" {:cause e}))))
      (finally (.commit cnx)))))

(defn find-weights-subjects
  "Returns a collection of subjects following rule:

  ?subject rdf:type map:WeightSet

  "
  [^SailRepository mapping-repository]
  {:pre (instance? SailRepository mapping-repository)}
  (r/with-open-repository [cnx mapping-repository]
    (doall
     (map #(-> %
               (.getSubject)
               (.stringValue)) (-> (.getStatements cnx nil RDF/TYPE WEIGHT-SET true (r/context-array))
                                   (u/iter-seq)
                                   )))))


(def ^:private find-mapping-rq
"prefix skos: <http://www.w3.org/2004/02/skos/core#>
 prefix map: <http://rdf.adalab-project/ontology/mapping/>
 prefix xsd: <http://www.w3.org/2001/XMLSchema#>

select ?subj ?term ?node where {
bind (str(?in_term) as ?term)
?node a map:MappedTerm ;
skos:notation ?term ;
skos:closeMatch ?subj .
}
")


(def ^:private find-weights-rq
"prefix map: <http://rdf.adalab-project/ontology/mapping/>
 prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

select ?weight_node ?prop ?weight {
bind (iri(?in_prop) as ?prop) .

?weight_node a map:WeightSet ;
  map:weights [ rdf:predicate ?prop ;
                map:weight ?weight
              ] .
} 
")


(def match-term-rq
"prefix luc: <http://www.openrdf.org/contrib/lucenesail#>

  select ?tfString ?sub ?score ?property ?value where {
  bind (str(?tf_term) as ?tfString) .
  (?tfString luc:allMatches luc:property luc:allProperties luc:score) luc:search (?sub ?property ?score) .
  ?sub ?property ?value
}")
