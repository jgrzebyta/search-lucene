(ns search.repository
  (:require
   [rdf4j.loader :as l :exclude [-main]]
   [rdf4j.repository :as r]
   [rdf4j.utils :as u]
   [rdf4j.dump :as d]
   [clojure.tools.logging :as log])
  (:import [java.io File]
           [org.eclipse.rdf4j.sail.memory MemoryStore]
           [org.eclipse.rdf4j.sail.nativerdf NativeStore]
           [org.eclipse.rdf4j.repository.sail SailRepository]))

;; inform about state of mapping repository
(def ^{:private true} mapping-changed (atom false)) 

;; Define protocol for manging state
(defprotocol State
  (getState [this field])
  (setState [this field value]))

;; Bring together instance of `SailRepository` with
;; the application specific metadata
(defrecord RepositoryHolder [repository state-atom]
  State
  (getState [this field] (get @state-atom field))
  (setState [this field value] (swap! state-atom assoc field value)))

(defn make-data-repository
  "Populates native repository with gene-annotation data. In the next step it will be done text searching on that."
    [dir dataset]
  (let [dir (u/create-dir dir)
        repo (r/make-repository-with-lucene (NativeStore. (.toFile dir) "spoc,cspo,pocs"))]
    (log/debug "# Make native repository at: " (.toString dir))
    (l/load-multidata repo dataset)     
    (let [cnt (count (r/get-all-statements repo))]
      (binding [*out* *err*] (printf "Loaded [%d] statements\n" cnt))
      (assert (> cnt 0)))
    (RepositoryHolder. repo (atom {}))))

(defn make-mapping-repository
  "Prepares and loads `MemoryStore`-based repository."
  [^File mapping-file]
  (let [repo (-> (MemoryStore.)
                 (r/make-repository))]
    (l/load-data repo mapping-file)
    (RepositoryHolder. repo (atom {:changed false}))))

(defn add-item-to-mapping-repository
  "Add record to mapping repository."
  [^RepositoryHolder repository item]
  (r/with-open-repository [cnx (get repository :repository)]
    (try
      (.begin cnx)
      (let [model (.asMappingRDF item)]
        (doseq [triple model] (.add cnx triple (r/context-array))))
      (catch Exception e
        (.rollback cnx)
        (log/error "Error occured: " (.getMessage e))
        false)
      (finally (.commit cnx)
               (.setState repository :changed true)
               true))))

(defn dump-mapping
  "Recreate mapping repository file using the latest data."
  [^File file ^RepositoryHolder repository]
  (d/with-rdf-writer [wrt (.toPath file)]
    (r/with-open-repository [cnx (get repository :repositor)]
      (try
        (.begin cnx)
        (.export cnx wrt (r/context-array))
        (finally (.commit cnx))))))
