(ns search.lucene-search
  (:gen-class)
  (:import [org.eclipse.rdf4j.sail.nativerdf NativeStore]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory])
  (:require [clojure.java.io :as io]
            [rdf4j.sparql.processor :as sp]
            [rdf4j.repository :as r]
            [rdf4j.loader :as l :exclude [-main]]
            [rdf4j.utils :as u]
            [search.vocabulary :as v]
            [search.analyser :as a]))



(defn make-native-repository
  "Populates native repository with gene-annotation data. In the next step it will be done text searching on that."
    [dir dataset]
  (let [dir (u/create-dir dir)
        repo (r/make-repository-with-lucene (NativeStore. (.toFile dir) "spoc,cspo,pocs"))]
    (println "# Make native repository at: " (.toString dir))
    (l/load-data repo dataset)
    (let [cnt (count (r/get-all-statements repo))]
      (binding [*out* *err*] (printf "Loaded [%d] statements\n" cnt))
      (assert (> cnt 0)))
    repo))


(defn make-binding [term]
  {:tf_term term})

(defn process-terms
  "Process terms using pmap: Do text searching on thee native repository and load result statements
into memory repository managed by nalyser package.
"
  [terms repository sparql-file buffer]
  (dorun (pmap (fn [t]
                 (binding [*out* *err*] (printf "  Process term: %s\n" t))
                 (sp/with-sparql [:sparql sparql-file :result rs :binding (make-binding t) :repository repository]
                   (a/load-dataset rs buffer))) terms)))


(cli/defclifn -main
"Processes text searching terms from term-file using SPARQL request on given data file.
All results are printed to standard output as turtle formted file. 

The result records are returned as <http://rdf.adalab-project.org/ontology/adalab-meta/AnnotationStatement> where
predicate <http://rdf.adalab-project.org/ontology/adalab-meta/annotatedBy> is given in annotator option.

USAGE:
annotateTF.sh [options] data-file
"
  [t terms-file PATH str "Terms contained file"
   a annotator URI str "URI of annotator"
   ]
  (let [terms-file (:terms-file *opts*)
        terms (line-seq (io/reader terms-file))
        sparql-file (:sparql-file *opts*)
        data-file (nth *args* 0)
        repo-dir (apply str (list (System/getProperty "user.dir") "/rdf4j-repository"))
        repo (make-native-repository repo-dir data-file)
        buf (a/make-buffer)
        results (.createEmptyModel (LinkedHashModelFactory.))]
    (println "# Number of terms: " (count terms))
    (process-terms terms repo sparql-file buf) ;; load dataset
    (a/process-counting buf)
    (a/process-analysis results buf (:annotator *opts*))
    (v/print-model results))
  )
  

