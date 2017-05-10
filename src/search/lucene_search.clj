(ns search.lucene-search
  (:gen-class)
  (:import [java.io File]
           [java.nio.file Paths Path]
           [org.eclipse.rdf4j.sail.nativerdf NativeStore]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory])
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [rdf4j.sparql.processor :as sp]
            [rdf4j.repository :as r]
            [rdf4j.loader :as l :exclude [-main]]
            [rdf4j.utils :as u]
            [search.vocabulary :as v]
            [search.analyser :as a]
            [clojure.tools.logging :as log]))

(declare do-main)

(defn make-native-repository
  "Populates native repository with gene-annotation data. In the next step it will be done text searching on that."
    [^Path dir dataset]
  (let [dir (if-not (-> (.toFile dir)
                          (.exists))
              (u/create-dir dir)
              dir)
        repo (r/make-repository-with-lucene (NativeStore. (.toFile dir) "spoc,cspo,pocs"))]
    (l/load-multidata repo dataset)
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
  [terms repository sparql buffer]
  (dorun (pmap (fn [t]
                 (binding [*out* *err*] (printf "  Process term: %s\n" t))
                 (sp/with-sparql [:sparql sparql :result rs :binding (make-binding t) :repository repository]
                   (a/load-dataset rs buffer))) terms)))

(defn load-sparql-string ^String [^String file-location]
  (-> (io/resource file-location)
      (slurp)))


(def cli-options
  [["-h" "--help" "Print this screen" ]
   ["-t" "--terms-file FILE" "File containing terms to search. Single term in line." :default nil :parse-fn #(io/file %)]
   ["-w" "--wages-file FILE" "File containing wages for properties." :default nil :parse-fn #(io/file %)]
   ["-r" "--repository-dir DIR" "Location where the native repository should be put." :default "." :parse-fn #(io/file %)]])


(defn validate-args
  "Validates input arguments and prepare required objects."
    [args]
  (let [{:keys [options arguments summary errors]} (parse-opts args cli-options)]
    (log/debug (format "Parsing options:
  options: %s
  arguments: %s
  errors: %s\n" options arguments errors))
    (cond
      (:help options) (println summary)
      errors (println summary)
      (and (some? (get options :terms-file))
           (some? (get options :wages-file))
           (some? (get options :repository-dir))
           (> (count arguments) 0))
      {:terms (:terms-file options) :wages (:wages-file options) :repository (:repository-dir options)
       :data (apply (fn [rsc] (map #(io/file %) rsc)) [arguments])} ;; converts set of arguments into set of files 
      :else (println summary))))

(defn -main [& args]
"Processes text searching terms from term-file using SPARQL request on given data file.
All results are printed to standard output as turtle formted file. 
"
  (let [{:keys [terms-file data repository wages]} (if-let [vld (validate-args args)]
                                                     vld
                                                     (System/exit 0))]
    (do-main terms-file wages data repository)))


(defn do-main
"Do the main work. 

If was extracted to separate function for testing purposes."
  [^File terms-file wages-file data-files repo-dir]
  (log/debug (format "# do-main arguments: %s %s %s" terms-file data-files repo-dir))
  (let [terms (line-seq (io/reader terms-file))
        sparql-string (load-sparql-string "match_terms.rq")
        repo-dir (or repo-dir (apply str (list (System/getProperty "user.dir") "/rdf4j-repository")))
        repo (make-native-repository repo-dir data-files)
        buf (a/make-buffer)
        results (.createEmptyModel (LinkedHashModelFactory.))]
    (println "# Number of terms: " (count terms))
    (process-terms terms repo sparql-string buf) ;; load dataset
    (a/process-counting buf)
    (a/process-analysis results buf)
    (v/print-model results)))
  

