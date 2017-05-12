(ns search.vocabulary
  (:require [buddy.core.codecs :refer [bytes->hex]]
            [buddy.core.hash :as bch]
            [clojure.tools.logging :as log]
            [rdf4j.repository :as r]
            [rdf4j.sparql.processor :as s]
            [rdf4j.utils :as u])
  (:import [java.io OutputStreamWriter]
           [org.eclipse.rdf4j.model Model IRI Value BNode]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory LinkedHashModel]
           [org.eclipse.rdf4j.model.util Models]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS SKOS XMLSchema]
           [org.eclipse.rdf4j.repository.sail SailRepository]
           [org.eclipse.rdf4j.rio Rio RDFFormat]))

(declare find-mapping-rq find-weights-rq)

(def vf (u/value-factory))

;; Mapping vocabulary

(def NS-INST "http://rdf.adalab-project/ontology/mapping/")

(def ^IRI MappedTerm (.createIRI vf NS-INST "MappedTerm"))

(def ^IRI SHA1 (.createIRI vf NS-INST "sha1"))

(def ^IRI WEIGHT (.createIRI vf NS-INST "weight"))

(def ^IRI WEIGHT-SET (.createIRI vf NS-INST "WeightSet"))

(defn create-record
  "Creates all mapping statements for a single record: `subj` and `term`.

Where `subj` is string representation of domain object URI and `term` is a string representation of term. 
"
  [subj term]
  (let [term-sha1 (-> term
                        (bch/sha1)
                        (bytes->hex))
          map-instance (.createIRI vf NS-INST term-sha1)
          to-return (-> (LinkedHashModelFactory.)
                        (.createEmptyModel))]
    (.add to-return map-instance RDF/TYPE MappedTerm (r/context-array))
    (.add to-return map-instance SHA1 (.createLiteral vf term-sha1) (r/context-array))
    (.add to-return map-instance SKOS/NOTATION (.createLiteral vf term XMLSchema/STRING) (r/context-array))
    (.add to-return map-instance SKOS/CLOSE_MATCH (.createIRI vf subj) (r/context-array))
to-return))


(defn print-model
  "Prints a model to Turtle format."
  [^Model model]
  (Rio/write model (OutputStreamWriter. System/out) RDFFormat/TURTLE))


(defn search-mapping ^Value [^SailRepository mapping term]
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
        (= (count rs) 0) 1.0
        :default {:predicate (-> (first rs)
                                  (.getValue "prop"))
                  :weight (-> (first rs)
                              (.getValue "weight")
                              (.floatValue))
                  :wage-node (-> (first rs)
                                 (.getValue "weight_node"))}))))

(defn copy-to-model
"Copy all triples from `from` Repository to `to` Model with `subject`.

The relevant SPARQL query is:

create {
 ?subject ?x ?y
} where {
 ?subject ?x ?y
  }"
  [subject ^SailRepository from ^Model to]

  (log/debugf "Copy statements with subject : %s" subject)
  (r/with-open-repository [cnx from]
    (let [vf (u/value-factory cnx)
          subj-iri (if (string? subject)
                     (.createIRI vf subject) subject)]
      (doall (map #(do
                     (log/tracef "\t\tAdd statement: %s" %)
                     (.add to %)                               ;; Simple add statement to model `to`
                     (when (instance? BNode (.getObject %))    ;; Process deep copy: i.e. if object is a `BNode` than recursively call the function 
                       (copy-to-model (.getObject %) from to)))
                  (-> (.getStatements cnx subj-iri nil nil false (r/context-array))
                      (u/iter-seq))))
      )))

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


(defn make-empty-model []
  (doto
   (.createEmptyModel (LinkedHashModelFactory.))
   (.setNamespace SKOS/NS)
   (.setNamespace "map" NS-INST)
   (.setNamespace RDF/NS)
   (.setNamespace XMLSchema/NS)))
