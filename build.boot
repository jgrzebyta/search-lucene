(set-env! :project 'adalab/search-lucene
          :source-paths #{"src"}
          :resource-paths #{"resources"}
          :url "http://www.adalab-project.org/"
          :dependencies '[[adalab/triple-loader "0.2.2-SNAPSHOT"]
                          [org.slf4j/slf4j-api "1.7.25"]
                          [org.clojure/tools.logging "0.3.1"]
                          [org.clojure/tools.cli "0.3.5"]
                          [org.eclipse.rdf4j/rdf4j-runtime "2.3-SNAPSHOT"]
                          [buddy/buddy-core "1.4.0"]
                          [ch.qos.logback/logback-classic "1.2.3"]
                          [degree9/boot-semver "1.7.0" :scope "test"]])


;; this line prevents confusing the deployer with dependencies` pom.xml files
(alter-var-root #'boot.pod/standard-jar-exclusions (constantly (conj boot.pod/standard-jar-exclusions #"/pom\.xml$")))

(require '[degree9.boot-semver :refer :all]
         '[clojure.test :as test])

(def version-namespace (symbol "search.version"))

(task-options!
 version {:minor 'zero :patch 'three :include false :generate version-namespace}
 pom {:project (get-env :project) }
 aot {:all true}
 jar {:main 'search.lucene-search})

(deftask develop
  "Build SNAPSHOT version of jar"
  []
  (version :develop true :pre-release 'snapshot))

(deftask testing "Attach tests/ directory to classpath." []
  (set-env! :source-paths #(conj % "tests/src" "tests/resources"))
  identity)


(deftask run-test "Run unit tests"
  [t test-name NAME str "Test to execute. Run all tests if not given."]
  *opts*
  (testing)
  (println (format "Repositories: %s" (get-env :repositories)))
  (use '[tests.basic]
       '[tests.core])
  (if (nil? (:test-name *opts*))
    (do
      (println "Run all tests")
      (test/run-all-tests))
    (do
      (println (format "Run test: %s" (:test-name *opts*)))
      (test/test-var (resolve (symbol (:test-name *opts*)))))))



(deftask build
  "Build without dependencies" []
  (comp
   (pom)
   (aot)
   (jar)
   (target)))

(deftask build-standalone
  "Build standalone version"
  []
  (comp
   (pom)
   (aot)
   (uber)
   (jar :file (format "%s-standalone.jar" (-> (get-env :project)
                                              (str)
                                              (.split "/")
                                              (get 1)
                                           )))
   (target)))
