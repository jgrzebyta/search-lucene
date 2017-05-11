(ns basic
  (:import [org.apache.commons.io FileUtils]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.test :as t]
            [search.lucene-search :as m]))

(t/deftest test-do-main
  (t/testing "Test do-main"
    (let [tmp-dir (Files/createTempDirectory "basic_" (make-array FileAttribute 0))
          terms-file (io/file "tests/resources/terms.cvs")
          mapping-file (io/file "tests/resources/wages.ttl")
          data-files (list (-> (io/file "tests/resources/dataset.ttl")
                               (.toPath)))]
      (log/info (format "Temp directory location: %s" (.toString tmp-dir)))
      (m/do-main terms-file mapping-file data-files tmp-dir)
      (FileUtils/deleteDirectory (.toFile tmp-dir)))))
