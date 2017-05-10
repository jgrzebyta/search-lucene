(ns search.lucene-search
  (:gen-class)
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [rdf4j.loader :as l]
            [search.repository :as r]
            [search.search-process :as sp]))


(declare process-term)

(defn- prepare-terms
"
Adds number of lines. Converts each term from `terms` into map {:id NUMBER :value term}.
"
  [terms]
  (let [r (range 0 (count terms))]
    (map (fn [id val] {:id id :value val}) r terms)))


(def cli-options
  [["-h" "--help" "Print this screen"]
   ["-t" "--terms-file FILE" "File containing terms to search. Single term in line." :default nil :parse-fn #(io/file %)]
   ["-m" "--mapping-file FILE" "File containing recent mapping terms." :default nil :parse-fn #(io/file %)]])

(defn validate-args
  "Validates input arguments and prepare required objects."
    [args]
  (let [{:keys [options arguments summary errors]} (parse-opts args cli-options)]
    (cond
      (:help options) (println summary)
      errors (println summary)
      (and (some? (get options :terms-file))
           (some? (get options :mapping-file))
           (> (count arguments) 0))
      {:terms (:terms-file options) :mapping (:mapping-file options)
       :data (apply (fn [rsc] (map #(io/file %) rsc)) [arguments])} ;; converts set of arguments into set of files 
      :else (println summary)))
  )


(defn -main [& args]
  (let [{:keys [terms mapping data]} (validate-args args)
        terms-list (-> terms
                       (io/reader)
                       (line-seq)
                       (prepare-terms))]
    (sp/instantiate-reposiories mapping data)
    
    ))

(defn- process-term
  "Processes single term. That function should be used in `map`."
  [mapping-repository data-repository term]
  (if-let [mapped-target (sp/find-in-mapping mapping-repository term)]
    (get mapped-target :domain-id)
    ()
    )
  )


#_(defn -main [& args]
  [t terms-file PATH str "File containing terms to search. Single term in line."]
  [m mapping-file PATH str "File containing recent mapping terms."]
  (let [terms (-> (:terms-file *opts*)
                  (io/reader)
                  (line-seq)
                  (prepare-terms))
        repo-dir (apply str (list (System/getProperty "user.dir") "/rdf4j-repository"))
        repo (r/make-native-repository repo-dir *args*)
        mapping-repo (-> (MemoryStore.)
                         (r/make-repository))]
    ;; populate mapping repository
    (l/load-data (io/file terms-file))
    (print "test: " (system/getenv "pwd"))
    ))
