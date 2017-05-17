(ns tests.core
  (:require [clojure.set :as s]
            [clojure.pprint :as pp]
            [clojure.test :as t]
            [clojure.tools.logging :as log]
            [search.vocabulary :as voc]
            [search.analyser :as a])
  (:import [search.vocabulary SearchRecord]
           [java.util Collection]))


(def testing-record-map [{:term "term1" :subject "subj1a" :score 1.0}
                         {:term "term2" :subject "subj2a" :score 1.0}
                         {:term "term2" :subject "subj2a" :score 2.0}
                         {:term "term3" :subject "subj3a" :score 1.0}
                         {:term "term3" :subject "subj3b" :score 1.2}
                         {:term "term4" :subject "subj4a" :score 1.0}
                         {:term "term4" :subject "subj4a" :score 0.1}
                         {:term "term4" :subject "subj4b" :score 0.1}])

(def testing-record (doall (map #(voc/map->SearchRecord %) testing-record-map)))


(def expected-merge-by-domains (doall (map #(voc/map->SearchRecord %)
                                           [{:term "term1" :subject "subj1a" :score 1.0}
                                            {:term "term2" :subject "subj2a" :score 3.0}
                                            {:term "term3" :subject "subj3b" :score 1.2}
                                            {:term "term4" :subject "subj4a" :score 1.1}])))


(t/deftest core-simple-test
  (t/testing "Access test"
    (log/debug (with-out-str  (pp/pprint testing-record)))
    (t/is (instance? Collection testing-record))))

(defn emulate-load-dataset
  "Emulates part of `search.analyser/load-dataset`.
  `dataset` is a records collection with the same term."
  [dataset]
  (let [merged (a/merge-by-domains dataset)
        top-one (first (sort voc/search-record-comparator merged))]
    (when (some? top-one)
      top-one)))


(t/deftest core-merge-by-domains
  (let [terms (-> (map #(:term %) testing-record)
                  (set)
                  (seq))]
    #_(t/testing "Test `merge-by-domains` for different terms"
      (doseq [tested-term terms]
        (log/debugf "Test term: %s" tested-term)
        (let [by-term (filter (comp #{tested-term} :term) testing-record)
              result (set (doall (a/merge-by-domains by-term)))]
          (log/debugf "+++ \n\tResult: %s" result))))
    (t/testing "Tests domains with the best score."
      (doseq [tested-term terms]
        (let [by-term (filter (comp #{tested-term} :term) testing-record)
              expected-by-term (first (doall (filter (comp #{tested-term} :term) expected-merge-by-domains)))
              result (emulate-load-dataset by-term)]
          (t/is (= result expected-by-term))
          (log/debugf "test: \n\t%s\nExpected: %s" result expected-by-term)
          )
      ))))

