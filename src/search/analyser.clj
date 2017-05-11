(ns search.analyser
  (:import [org.eclipse.rdf4j.model.vocabulary RDFS]
           [org.eclipse.rdf4j.query.algebra.evaluation QueryBindingSet])
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [rdf4j.loader :as l :exclude [-main]]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u]
            [search.vocabulary :as v]))

(defn make-buffer
 "Creates atomised map."
  []
  (atom {}))

(def value-factory (u/value-factory))

(def norm-matrix {:name 2 :synonym 0.1 :longName 1.5})

(defn normalize-score
  "Multiplies the score by weight defined in matrix based on predicate."
  [score matrix predicate]
  (* score (get matrix (keyword predicate) 1.0)))

(defn process-binding [^QueryBindingSet b buffer]
  (let [term (-> b
                 (.getValue "tfString")
                 (.stringValue))
        subject (-> b
                    (.getValue "sub")
                    (.stringValue))
        property (-> b
                     (.getValue "property")
                     (.stringValue)
                     (str/replace #"\S*/" "") ;; returns string after last / character
                     )
        score (-> b
                  (.getValue "score")
                  (.stringValue)
                  (read-string)
                  (normalize-score norm-matrix property))
        sub-sc (assoc {} :score score :term term :property property)]
    (binding [*out* *err*] (printf "Term: %s -- %s\n" term sub-sc))
    (swap! buffer #(update-in % [term subject] (fnil conj (hash-set)) sub-sc))))

(defn load-dataset
  "Populates buffer map "
  [dataset buffer]
  (loop [rc dataset]
    (when-let [r (first rc)]
      (process-binding r buffer)
      (recur (rest rc)))))


(defn- count-merget
  "Process f operation on values of kw keywords of each map of list-of-maps."
  [lom kw f]
  (if (= 1 (count lom))
    (-> lom
        (first)
        (get kw))
    (reduce f (map #(get % kw) lom))))


(defn process-counting
  "Process temporary buffer."
  [buffer]
  (doseq [trm (keys @buffer)
          :let [sub (keys (get @buffer trm))]]
    ;;(printf "record [%s] [%s]: %s\n" trm sub records)
    (doseq [sb sub]
      (swap! buffer #(update-in % [trm sb] count-merget :score +) ))))


(defn process-analysis
  "The main counting process."
  [mapping-repo results-model buffer]
  ;;(log/debug "Buffer: \n" (with-out-str (pp/pprint @buffer)))
  (doseq [trm (keys @buffer)
          :let [term trm
                subjs (keys (get @buffer trm))]]
    (if (= 1 (count subjs))
      (let [domain-uri (first subjs)]
        (.addAll results-model (v/create-record domain-uri term)))
      (.addAll results-model (v/create-record (-> (apply max-key val (get @buffer trm))
                                                  (key))
                                              term)))))
