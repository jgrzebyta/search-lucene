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
(declare dump-repository)

;; Define protocol for manging state
(defprotocol State
  (getState [this field])
  (setState [this field value]))


(defprotocol Closeable
  (close [this]))

;; Bring together instance of `SailRepository` with
;; the application specific metadata
(defrecord RepositoryHolder [repository state-atom]
  State
  (getState [this field] (get @state-atom field))
  (setState [this field value] (swap! state-atom assoc field value)))

(extend-type RepositoryHolder
  Closeable
  (close [this]
    (let [state (getState this :changed)
          file (getState this :file)]
      (when (and state file)
        (dump-repository this)))))

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
    (RepositoryHolder. repo (atom {:changed false :file mapping-file}))))

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
               (setState repository :changed true)
               true))))

(defn dump-repository
  "Recreate mapping repository file using the latest data."
  [^RepositoryHolder repository]
  (when (getState repository :changed)
    (let [out-file (getState repository :file)]
      (log/debug (format "Dump mapping into file: %s" (.getAbsolutePath out-file)))
      (d/with-rdf-writer [wrt (.toPath out-file)]
        (r/with-open-repository [cnx (get repository :repository)]
          (try
            (.begin cnx)
            (.export cnx wrt (r/context-array))
            (finally (.commit cnx))))))))
