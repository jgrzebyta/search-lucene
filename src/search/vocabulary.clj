(ns search.vocabulary
  (:require [rdf4j.utils :as u]
            [rdf4j.repository :as r]
            [buddy.core.hash :as bch]
            [buddy.core.codecs :refer [bytes->hex]])
  (:import [java.io OutputStreamWriter]
           [org.eclipse.rdf4j.rio Rio RDFFormat]
           [org.eclipse.rdf4j.model Model IRI]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS SKOS]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory]))


(def vf (u/value-factory))

;; Mapping vocabulary

(def NS-INST "urn:map/")

(def ^IRI MappedTerm (.createIRI vf NS-INST "MappedTerm"))

(def ^IRI SHA1 (.createIRI vf NS-INST "sha1"))

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
    (.add to-return map-instance SKOS/NOTATION (.createLiteral vf term) (r/context-array))
    (.add to-return map-instance SKOS/CLOSE_MATCH (.createIRI vf subj) (r/context-array))
to-return))


(defn print-model
  "Prints a model to Turtle format."
  [^Model model]
  (Rio/write model (OutputStreamWriter. System/out) RDFFormat/TURTLE))
