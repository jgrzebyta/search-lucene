(ns search.lucene-search
  (:require [boot.cli :as cli]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [repository :as r]
            [rdf4j.loader :as l])
  (:import [org.eclipse.rdf4j.sail.memory MemoryStore]))


(defn prepare-terms
"
Wraps sequence in number of lines
"
  [terms]
  (let [r (range 0 (count terms))]
    (map (fn [val id] {:id id :value val}) terms r)))



(cli/defclifn -main
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
