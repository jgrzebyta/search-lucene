(ns test-patterns
  (:require [clojure.java.io :as io]
            [search.lucene-search :as m])
  (:import [java.io File]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [org.apache.commons.io FileUtils]))


(defn with-do-main-files
  "Run `search.lucene-search/do-main` with different arguments."
  [^File term-file ^File mapping-file ^File data-file]
  (let [tmp_dir (Files/createTempDirectory "test_dir_" (make-array FileAttribute 0))]
    (m/do-main terms mapping data tmp_dir)
    (FileUtils/deleteDirectory (.toFile tmp-dir))))
