(ns search.vocabulary
  (:require [rdf4j.utils :as u]
            [rdf4j.repository :as r]
            [buddy.core.hash :as bch]
            [buddy.core.codecs :refer [bytes->hex]])
  (:import [java.io OutputStreamWriter]
           [org.eclipse.rdf4j.rio Rio RDFFormat]
           [org.eclipse.rdf4j.model Model IRI]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory]))


(def value-factory (u/value-factory))

(def NS-RESOURCE "http://rdf.adalab-project.org/resource/annotation/")

(def NS-ADALAB "http://rdf.adalab-project.org/ontology/adalab/")

(def NS-ADALAB-META "http://rdf.adalab-project.org/ontology/adalab-meta/")

(def ^IRI TRANSCRIPTION-FACTOR (.createIRI value-factory NS-ADALAB "transcriptionFactor"))

(def ^IRI ANNOTATED-WITH (.createIRI value-factory NS-ADALAB-META "annotatedWith"))

(def ^IRI ANNOTATION-STATEMENT (.createIRI value-factory NS-ADALAB-META "AnnotationStatement"))

(def ^IRI ANNOTATED-BY (.createIRI value-factory NS-ADALAB-META "annotatedBy"))

(defn create-annotation-subject
  "Creates IRI formated:
  <http://rdf.adalab-project.org/resource/annotation/ID> where

  ID = (SHA1 (STR SUBJ rdfs:type adalab:transcriptionFactor ANNOTATOR)).
  "
  [subj annotator]
  (let [id (->
            (str (.toString subj) (.toString RDF/TYPE) (.toString TRANSCRIPTION-FACTOR) (.toString annotator))
            (bch/sha1)
            (bytes->hex))]
    (.createIRI value-factory NS-RESOURCE id)))


(defn create-record
  "Creates all statements for a single record"
  [subj annotator label]
  (let [subj-uri (.createIRI value-factory subj)
        ^Model model (->
                      (LinkedHashModelFactory.)
                      (.createEmptyModel))
        ann-node (create-annotation-subject subj annotator)]
    (.add model subj-uri RDF/TYPE TRANSCRIPTION-FACTOR (r/context-array))
    (.add model subj-uri ANNOTATED-WITH ann-node (r/context-array))
    (.add model ann-node RDF/SUBJECT subj-uri (r/context-array))
    (.add model ann-node RDF/PREDICATE RDF/TYPE (r/context-array))
    (.add model ann-node RDF/OBJECT TRANSCRIPTION-FACTOR (r/context-array))
    (.add model ann-node ANNOTATED-BY (.createIRI value-factory annotator) (r/context-array))
    (.add model ann-node RDFS/LABEL (.createLiteral value-factory label) (r/context-array))
    model))


(defn print-model
  "Prints a model to Turtle format."
  [^Model model]
  (Rio/write model (OutputStreamWriter. System/out) RDFFormat/TURTLE))
