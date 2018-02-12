(ns tests.basic
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.test :as t]
            [clojure.tools.logging :as log]
            [rdf4j.core :as c]
            [rdf4j.repository :as r]
            [search.lucene-search :as m]
            [search.vocabulary :as voc])
  (:import java.nio.file.attribute.FileAttribute
           java.nio.file.Files
           org.apache.commons.io.FileUtils))

(t/deftest test-do-main
  (t/testing "Test do-main"
    (let [tmp-dir (Files/createTempDirectory "basic_" (make-array FileAttribute 0))
          terms-file (io/file "tests/resources/terms.csv")
          mapping-file (io/file "tests/resources/weights.ttl")
          data-files (list (-> (io/file "tests/resources/dataset.ttl")
                               (.toPath)))]
      (log/debugf "Temp directory location: %s" (.toString tmp-dir))
      (m/do-main terms-file mapping-file data-files tmp-dir)
      (FileUtils/deleteDirectory (.toFile tmp-dir))))
  (t/testing "Test do-main with repeated terms"
    (let [tmp-dir (Files/createTempDirectory "basic_" (make-array FileAttribute 0))
          terms-file (io/file "tests/resources/terms2.csv")
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
      (c/load-data repository weight-file)
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

