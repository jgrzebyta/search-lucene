(ns search.repository
  (:require
   [rdf4j.utils :as u]
   [rdf4j.repository :as r]
   [rdf4j.sparql.processor :as sp]
   [rdf4j.loader :as l :exclude [-main]]
   [search-process :as srp])
  (:import [org.eclipse.rdf4j.sail.nativerdf NativeStore]))

(def search-term-sparql "
prefix luc: <http://www.openrdf.org/contrib/lucenesail#>
prefix xsd: <http://www.w3.org/2001/XMLSchema#>
select ?idInt ?subject ?predicate ?score where {
    bind(xsd:int(?id) :as ?idInt)
    (?tfString luc:allMatches luc:property luc:allProperties luc:score) luc:search (?subject ?predicate ?score) .
}
")

(def norm-matrix {:name 2 :synonym 0.1 :longName 1.5})

(defn normalize-score
  "Multiplies the score by weight defined in matrix based on predicate."
  [score matrix predicate]
  (* score (get matrix (keyword predicate) 1.0)))

(defn make-native-repository
  "Populates native repository with gene-annotation data. In the next step it will be done text searching on that."
    [dir dataset]
  (let [dir (u/create-dir dir)
        repo (r/make-repository-with-lucene (NativeStore. (.toFile dir) "spoc,cspo,pocs"))]
    (println "# Make native repository at: " (.toString dir))
    (l/load-multidata repo dataset)     
    (let [cnt (count (r/get-all-statements repo))]
      (binding [*out* *err*] (printf "Loaded [%d] statements\n" cnt))
      (assert (> cnt 0)))
    repo))

(defn- make-binding
  "Prepares binding for SPARQL request."
  [term raw-id]
  {:tfString term :id raw-id})

(defn process-search
  ""
  [repository terms]
  (let [index (-> (r/make-mem-repository)
                  (.initialize))
        vf (u/value-factory repository)]
    (dorun (pmap (fn [term]
                   (let [id (:id term)
                         trm-string (:value term)]
                     (sp/with-sparql [:sparql search-term-sparql :result rs :binding (make-binding trm-string id) :repository repository]
                       
                       )))
                 terms))))

