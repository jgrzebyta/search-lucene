(ns search.search-process
  (:require [rdf4j.repository :as r]
            [rdf4j.utils :as u]
            [rdf4j.sparql.processor :as spr]
            [buddy.core.hash :as bch]
            [buddy.core.codecs :refer [bytes->hex]])
  (:import [org.eclipse.rdf4j.repository RepositoryConnection]
           [org.eclipse.rdf4j.repository.sail SailRepository]
           [org.eclipse.rdf4j.model Model IRI Resource Value]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS SKOS]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory]))

(def ^:private vf (u/value-factory))

;; Mapping vocabulary

(def SKOS-NS "http://www.w3.org/2004/02/skos/core#")

(def NS-INST "urn:map/")

(def ^IRI MappedTerm (.createIRI vf NS-INST "MappedTerm"))

(def ^IRI closeMatch (.createIRI vf SKOS-NS "closeMatch"))

(def ^IRI sha1 (.createIRI vf NS-INST "sha1"))

(def ^IRI notation (.createIRI vf SKOS-NS "notation"))


(defprotocol AsMappingRDF
  "Returns RDF representation of mapping entity"
  (asMappingRDF [this]))

(defrecord SearchRecord [raw-id domain-id term predicate score]
  AsMappingRDF
  (asMappingRDF [this]
    (let [term-sha1 (-> term
                        (bch/sha1)
                        (bytes->hex))
          map-instance (.createIRI vf NS-INST term-sha1)
          to-return (-> (LinkedHashModelFactory.)
                               (.createEmptyModel))]
      (.add to-return map-instance RDF/TYPE MappedTerm (r/context-array))
      (.add to-return map-instance sha1 (.createLiteral vf term-sha1))
      (.add to-return map-instance notation (.createLiteral term))
      (.add to-return map-instance closeMatch (.createIRI vf domain-id))
      to-return)))

(def search-record-comparator
  "Define comparator for `SearchRecord` by `score` field."
  (comparator (fn [x y]
                (> (get x :score) (get y :score)))))

(def find-mapping-rq "
prefix skos: <http://www.w3.org/2004/02/skos/core#>
prefix xsd: <http://www.w3.org/2001/XMLSchema#>
select ?term_str ?mapping where {
bind (xsd:string(?term_str) as ?term_lit) .
?trm_pred skos:notation ?term_lit .
?trm_pred skos:closeMatch ?mapping
}")


(defn find-mapping
  "Look for `term` in mapping resources provided by given `SailRepository`."
  [^SailRepository mapping-repo ^String term]
  (spr/with-sparql [:sparql find-mapping-rq :result rt :binding {:term_str term} :repository mapping-repo]
    (let [cnt (count rt)]
      (cond (= cnt 0)
            nil
            (> cnt 1)
            (throw (ex-info "Term has multiple resuts" {:count cnt}))
            :else
            (-> (first rt)
                (.getValue "mapping"))))))
