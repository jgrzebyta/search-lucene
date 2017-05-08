(ns search.search-process
  (:require [buddy.core.codecs :refer [bytes->hex]]
            [buddy.core.hash :as bch]
            [rdf4j.repository :as r]
            [search.repository :as sr]
            [rdf4j.sparql.processor :as spr]
            [rdf4j.utils :as u]
            [clojure.string :as str])
  (:import [org.eclipse.rdf4j.model Model IRI Resource Value]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS SKOS]
           [org.eclipse.rdf4j.repository RepositoryConnection]
           [org.eclipse.rdf4j.repository.sail SailRepository]
           [search.repository RepositoryHolder]))

(declare find-mapping-rq find-data-rq normalize-score norm-matrix)

(def ^:private vf (u/value-factory))

;; Mapping vocabulary

(def NS-INST "urn:map/")

(def ^IRI MappedTerm (.createIRI vf NS-INST "MappedTerm"))

(def ^IRI SHA1 (.createIRI vf NS-INST "sha1"))


(defprotocol AsMappingRDF
  "Returns RDF representation of mapping entity"
  (asMappingRDF ^Model [this]))

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
      (.add to-return map-instance SHA1 (.createLiteral vf term-sha1) (r/context-array))
      (.add to-return map-instance SKOS/NOTATION (.createLiteral vf term) (r/context-array))
      (.add to-return map-instance SKOS/CLOSE_MATCH (.createIRI vf domain-id) (r/context-array))
      to-return)))

(def search-record-comparator
  "Define comparator for `SearchRecord` by `score` field."
  (comparator (fn [x y]
                (> (get x :score) (get y :score)))))


(defprotocol FindInRepository
  (findInData ^SearchRecord [this ^String term raw-id] "Looks for `term` in data repository.")
  (findInMapping ^SearchRecord [this ^String term raw-id] "Look for `term` in mapping resources. Loads the record if not found."))


(defrecord RepositoriesSet [^RepositoryHolder mapping-repository ^RepositoryHolder data-repository]
  FindInRepository
  
  (findInData [this term raw-id]
    (spr/with-sparql [:sparql find-data-rq :result rt :binding {:tfString term :id raw-id} (get data-repository :repository)]
      (let [records (map
                     (fn [i] ;; Function receives sinle instance of org.eclipse.rdf4j.query.BindingSet and
                             ;; converts into SearchRecord. It normalizes score in between.
                       (let [predicate (-> i
                                           (.getValue "predicate"))
                             score (-> i
                                       (.getValue "score")
                                       (.floatValue))]
                         (SearchRecord. (-> i
                                          (.getValue "idInt")
                                          (.intValue))
                                      (-> i
                                          (.getValue "subject"))
                                      term
                                      predicate
                                      (normalize-score score norm-matrix predicate)
                                      ))) rt)]
        
        (if-not (some? records)
          false
          (first (sort search-record-comparator records)) ;; returns first SearchRecord from the sorted sequence. Sorted according decreasing score.
          ))))

  
  (findInMapping [this term raw-id]
    ;; try to find `term` mapping record ...
    (spr/with-sparql [:sparql find-mapping-rq :result rt :binding {:term_str term} (get mapping-repository :repository)]
      (let [cnt (count rt)]
        (cond (= cnt 0) ;; ... if not found ...
              (when-let [record (.findInData term raw-id)] ;; ... try to find in dataset
                (sr/add-item-to-mapping-repository mapping-repository record))
              (> cnt 1) ;; ... if found more than one record throw exception.
              (throw (ex-info "Term has multiple results" {:count cnt}))
              :else
              (SearchRecord. raw-id
                             (-> (first rt)
                                 (.getValue "mapping"))
                             term
                             nil
                             nil))))))


;; Process mapping

(def norm-matrix {:name 2 :synonym 0.1 :longName 1.5})

(defn normalize-score
  "Multiplies the score by weight defined in matrix based on predicate."
  [score matrix predicate]
  (* score (get matrix (keyword (last (str/split (.stringValue predicate) #"[/#]"))) 1.0)))

(defn instantiate-reposiories
  [mapping-file data-files]
  (let [mapping-repository (sr/make-mapping-repository mapping-file)
        local-dir (apply str (list (System/getProperty "user.dir") "/rdf4j-repository"))
        data-repository (sr/make-data-repository local-dir data-file)]
    (RepositoriesSet. mapping-repository data-repository)))


;; SPARQL requests

(def ^{:private true} find-mapping-rq "
prefix skos: <http://www.w3.org/2004/02/skos/core#>
prefix xsd: <http://www.w3.org/2001/XMLSchema#>
select ?term_str ?mapping where {
bind (xsd:string(?term_str) as ?term_lit) .
?trm_pred skos:notation ?term_lit .
?trm_pred skos:closeMatch ?mapping
}")

(def ^{:private true} find-data-rq "
prefix luc: <http://www.openrdf.org/contrib/lucenesail#>
prefix xsd: <http://www.w3.org/2001/XMLSchema#>
select ?idInt ?subject ?predicate ?score where {
    bind(xsd:int(?id) :as ?idInt)
    (?tfString luc:allMatches luc:property luc:allProperties luc:score) luc:search (?subject ?predicate ?score) .
}")
