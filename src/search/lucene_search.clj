(ns search.lucene-search
  (:gen-class)
  (:import [java.io File]
           [java.nio.file Paths Path]
           [java.util Collection]
           [org.eclipse.rdf4j.repository.sail SailRepository]
           [org.eclipse.rdf4j.sail.nativerdf NativeStore])
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [rdf4j.loader :as l :exclude [-main]]
            [rdf4j.repository :as r]
            [rdf4j.sparql.processor :as sp]
            [rdf4j.utils :as u]
            [search.analyser :as a]
            [search.vocabulary :as v]))

(declare do-main load-sparql-string)

(defn make-native-repository
 "Populates native repository with gene-annotation data. In the next step it will be done text searching on that.

  `NativeRepository` detects internally if `dir` directory exists and contains valid files.

  This method detects also if repository is empty -- than loads data files from `dataset` otherwise just returns
  initialised repository.   
"
  [^Path dir ^Collection dataset]
  (let [dir (if-not (-> (.toFile dir)
                        (.exists))
              (u/create-dir dir)
              dir)
        ^SailRepository repo (r/make-repository-with-lucene (NativeStore. (.toFile dir) "spoc,cspo,pocs"))]
    ;; detects if data are loaded;
    (if-not (empty? (r/get-all-statements repo))
      (try
        (l/load-multidata repo dataset)
        (let [cnt (count (r/get-all-statements repo))]
          (log/debugf "Loaded [%d] statements\n" cnt)
          (assert (> cnt 0)))
        (catch Expection x (throws (ex-info "Data cannot be loaded into repository. " {:reason x :repository-dir (.getDataDir repo)}))))
      (when-not (.isActive repo)
        (.initialize repo)))
    repo))

(defn make-mapping-repository ^SailRepository [^File file]
  (let [mem-repo (r/make-mem-repository)]
    (l/load-data mem-repo file)
    mem-repo))

(defn process-terms
  "
  Process collection of `terms` using `pmap`. 
  In the first instance try to find mapping in the `mapping-repository`. 
  If mapping was found than do nothing.

  Else ...
  Do text searching in the `data-repository` and load result into atomised `Map`-based
  buffer `a/make-buffer`.
  "
  [terms mapping-repository data-repository]
  {:pre [(seq? terms)]}
  
  (let [sparql (load-sparql-string "match_terms.rq")]
    (dorun (pmap (fn [t]
                   (log/infof "  Process term: '%s'" t)
                   ;; in the first instance try to find term in mapping repository
                   (when-not (v/search-mapping mapping-repository t)
                     (sp/with-sparql [:sparql sparql :result rs :binding {:tf_term t} :repository data-repository]
                       (if (= 0 (count rs))
                         (log/infof "  No data for term '%s'" t)
                         (a/load-dataset mapping-repository rs)
                         )))) terms))))

(defn load-sparql-string ^String [^String file-location]
  (-> (io/resource file-location)
      (slurp)))


(def cli-options
  [["-h" "--help" "Print this screen" ]
   ["-t" "--terms-file FILE" "File containing terms to search. Single term in line." :default nil :parse-fn #(io/file %)]
   ["-m" "--mapping-file FILE" "File containing mapping data including wages." :default nil :parse-fn #(io/file %)]
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
           (some? (get options :mapping-file))
           (some? (get options :repository-dir))
           (> (count arguments) 0))
      ;; converts set of arguments into set of files 
      {:terms (:terms-file options)  :repository (:repository-dir options)
       :data (apply (fn [rsc] (map #(->(io/file %)
                                       (.toPath)) rsc)) [arguments]) :mapping (:mapping-file options)} 
      :else (println summary))))

(defn -main [& args]
"Processes text searching terms from term-file using SPARQL request on given data file.
All results are printed to standard output as turtle formated file. 
"
  (let [{:keys [terms data repository mapping]} (if-let [vld (validate-args args)]
                                                             vld
                                                             (System/exit 0))]
    (do-main terms mapping data repository)
    (System/exit 0)))


(defn do-main
"Do the main work. 

If was extracted to separate function for testing purposes."
  [^File terms-file ^File mapping-file ^Collection data-files ^File repo-dir]
  (log/debug (format "# do-main arguments: %s %s %s" terms-file data-files repo-dir))
  (let [terms (seq (set (line-seq (io/reader terms-file))))     ;; reads terms from file. Loads terms into a set to remove replications.
        mapping-repo (make-mapping-repository mapping-file)
        repo-dir (or repo-dir (apply str (list (System/getProperty "user.dir") "/rdf4j-repository")))
        repo-dir-path (cond
                        (instance? File repo-dir) (.toPath repo-dir)
                        (instance? Path repo-dir) repo-dir
                        (instance? String repo-dir) (.toPath (io/file repo-dir)))
        repo (make-native-repository repo-dir-path data-files)]
    (log/infof "Number of terms: %d" (count terms))
    (process-terms terms mapping-repo repo) ;; process searching in mapping repository and data repository
    (v/print-repository mapping-repo)))
  

