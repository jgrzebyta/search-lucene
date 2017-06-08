(ns search.logger
  (:import [ch.qos.logback.classic Logger Level]
           [org.slf4j LoggerFactory]))



(def app-main-logger-name "adalab.search-lucene.main")

(def ^Logger main-logger (doto
                             (LoggerFactory/getLogger app-main-logger-name)
                           (.setLevel Level/INFO)))
