(ns main.clojure.clara.examples.derive 
  (:require [clara.rules :refer [insert! insert-all mk-session fire-rules query defquery defrule]]))

(defrule pet-dog-or
  [:or
   [::spitz]
   [::samoyeda]]
  =>
  (println "you petted a spitz or a samoyeda")
  (insert! (assoc {}
                  :fact/type ::petted-dog
                  :id (random-uuid))))

(defrule pet-dog
  [?dog <- ::dog]
  =>
  (println "you petted any dog")
  (insert! (assoc ?dog 
                  :fact/type ::petted-dog
                  :id (random-uuid))))

(derive ::spitz ::dog)
(derive ::samoyeda ::dog)

(defquery petted-dogs
  []
  [::petted-dog (= ?id (:id this))])

(defn run-examples
  []
  (println "OR example"
           (-> (mk-session [pet-dog-or petted-dogs] :fact-type-fn :fact/type)
               (insert-all [{:fact/type ::samoyeda}
                            {:fact/type ::spitz}])
               fire-rules
               (query petted-dogs)))

  (println "derived example"
           (-> (mk-session [pet-dog petted-dogs] :fact-type-fn :fact/type)
               (insert-all [{:fact/type ::samoyeda}
                            {:fact/type ::spitz}])
               fire-rules
               (query petted-dogs))))
