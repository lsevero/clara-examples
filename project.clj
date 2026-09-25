(defproject com.cerner/clara-examples "0.2.0-SNAPSHOT"
  :description "Clara Example Rules"
  :url "https://github.com/cerner/clara-examples"
  :license {:name "Apache License Version 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[com.google.guava/guava "15.0"] ; Explicitly pull new Guava version for dependency conflicts.
                 [org.clojure/clojure "1.12.6"]
                 [com.cerner/clara-rules "0.24.0"]

                 ;; Dependencies for ClojureScript example.
                 [prismatic/dommy "1.1.0"]
                 [hipo "0.5.2"]
                 [org.clojure/clojurescript "1.12.145"]

                 ;; Dependency for time-based rules example.
                 [clj-time "0.15.2"]

                 ;; Dependency for rule DSL example.
                 [instaparse "1.5.0"]]

  :plugins [[lein-cljsbuild "1.1.8"]]
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :java-source-paths ["src/main/java"]
  :main clara.examples
  :hooks [leiningen.cljsbuild]
  :cljsbuild {:builds [{:source-paths ["src/main/clojurescript"]
                        :jar true
                        :compiler {:output-to "resources/public/js/examples.js"
                                   :optimizations :advanced}}]}

  :scm {:name "git"
        :url "https://github.com/cerner/clara-examples.git"}
  :pom-addition [:developers [:developer {:id "rbrush"}
                              [:name "Ryan Brush"]
                              [:url "http://www.clara-rules.org"]]]
  :repositories [["snapshots" {:url "https://oss.sonatype.org/content/repositories/snapshots/"}]]
  :deploy-repositories [["snapshots" {:url "https://oss.sonatype.org/content/repositories/snapshots/"
                                      :creds :gpg}]])
