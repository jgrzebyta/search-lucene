(ns search.vocabulary
  (:require [clojure.tools.logging :as log]
            [rdf4j.utils :as u]
            [rdf4j.repository :as r]
            [buddy.core.hash :as bch]
            [buddy.core.codecs :refer [bytes->hex]])
  (:import [java.io OutputStreamWriter]
           [org.eclipse.rdf4j.rio Rio RDFFormat]
           [org.eclipse.rdf4j.model.util Models]
           [org.eclipse.rdf4j.model Model IRI]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS SKOS]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory LinkedHashModel]))


(def vf (u/value-factory))

;; Mapping vocabulary

(def NS-INST "http://rdf.adalab-project/ontology/mapping/")

(def ^IRI MappedTerm (.createIRI vf NS-INST "MappedTerm"))

(def ^IRI SHA1 (.createIRI vf NS-INST "sha1"))

(def ^IRI WAGE (.createIRI vf NS-INST "wage"))

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

(defn search-mapping [^LinkedHashModel mapping term]
  (let [vf (.getValueFactory mapping)
        literal (.createLiteral vf term)
        mod (.filter mapping nil SKOS/NOTATION literal (r/context-array))
        subj (Models/subjectIRIs mod)
        cnt (count subj)]
    (log/debug (format "Number of items: %d" (count mod)))
    (cond
      (> cnt 1)
      (throw (ex-info "Multiple machings for term" {:term term :matches subj}))
      (= cnt 0)
      (throw (ex-info "No matches for term" {:term term})))
    (log/debug (format "Fount subject for term: %s -- $s" term (first subj)))
    (->
     (.filter (first subj) SKOS/CLOSE_MATCH nil (r/context-array))
     (Models/objectIRIs)
     (fn [x] (let [cnt1 (count x)]
               (if (= cnt1 1)
                 (first x)
                 (throw (ex-info "Wrong triple for object" {:objects x :count cnt1})))))
    )))

