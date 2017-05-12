(ns basic
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [org.apache.commons.io FileUtils])
  (:require [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [clojure.test :as t]
            [clojure.tools.logging :as log]
            [rdf4j.loader :as l]
            [rdf4j.repository :as r]
            [search.vocabulary :as voc]
            [search.lucene-search :as m]
            [clojure.string :as str]))

(t/deftest test-do-main
  (t/testing "Test do-main"
    (let [tmp-dir (Files/createTempDirectory "basic_" (make-array FileAttribute 0))
          terms-file (io/file "tests/resources/terms.cvs")
          mapping-file (io/file "tests/resources/weights.ttl")
          data-files (list (-> (io/file "tests/resources/dataset.ttl")
                               (.toPath)))]
      (log/debugf "Temp directory location: %s" (.toString tmp-dir))
      (m/do-main terms-file mapping-file data-files tmp-dir)
      (FileUtils/deleteDirectory (.toFile tmp-dir)))))


(t/deftest test-weights
  ;; prepare testing application context
  (let [weight-file (io/file "tests/resources/weights.ttl")
        repository (r/make-mem-repository)]
    (t/testing "Searching weights"
      (l/load-data repository weight-file)
      (log/info "Data loaded")
      (let [mapping (voc/search-weight repository "http://example.org/term")]
        (t/is (map? mapping))
        (t/is (= (:weight mapping) 1.5))
        (log/info (with-out-str (pp/pprint mapping))))
      )
    (t/testing "Find weights subjects"
      (let [subj (voc/find-weights-subjects repository)]
        (log/debugf "Subjects: %s" (apply list subj))
        (t/is (= 1 (count subj)))
        (t/is (-> (first subj)
                  (str/includes? "current_weights"))))
      )))
