(set-env! :project 'adalab/search-lucene
          :source-paths #{"src"}
          :resource-paths #{"resources"}
          :url "http://www.adalab-project.org/"
          :dependencies '[[adalab/triple-loader "0.2.1-SNAPSHOT"]
                          [org.clojure/tools.logging "0.3.1"]
                          [org.eclipse.rdf4j/rdf4j-runtime "2.3-SNAPSHOT"]
                          [buddy/buddy-core "1.2.0"]
                          [ch.qos.logback/logback-classic "1.2.3"]
                          [degree9/boot-semver "1.4.4" :scope "test"]])


;; this line prevents confusing the deployer with dependencies` pom.xml files
(alter-var-root #'boot.pod/standard-jar-exclusions (constantly (conj boot.pod/standard-jar-exclusions #"/pom\.xml$")))

(require '[degree9.boot-semver :refer :all])


(task-options!
 version {:minor 'zero :patch 'one :include true}
 pom {:project (get-env :project) }
 aot {:all true})

(deftask develop
  "Build SNAPSHOT version of jar"
  []
  (version :develop true :pre-release 'snapshot)
  identity)

(deftask testing "Attach tests/ directory to classpath." []
  (set-env! :source-paths #(conj % "tests"))
  identity)

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
   (jar :file (format "%s-standalone.jar" (get-env :project)))
   (target)))
