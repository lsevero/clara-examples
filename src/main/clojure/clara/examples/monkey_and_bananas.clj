(ns main.clojure.clara.examples.monkey-and-bananas
  "Faithful Clara Rules translation of Lisa/examples/mab.clp.

  The original CLIPS program uses ASSERT for goals and MODIFY for world
  state. In Clara, those operations are represented with
  insert-unconditional! and retract!/insert-unconditional!, respectively.

  This is important: using insert! for a Goal that is itself guarded by
  [:not [Goal ...]] creates a logical contradiction under Clara's truth
  maintenance: the rule inserts the fact that makes its own condition false,
  so Clara retracts it, making the rule true again. Goals and mutable world
  state are therefore inserted unconditionally here.

  PROBLEM
  -------
  A monkey must find and eat the bananas.

  SEMANTIC LOCATIONS
  ------------------
  :monkey-start
  :red-chest-area
  :blue-chest-area
  :grapes-area
  :green-chest-area
  :red-key-location

  Initial state:
    monkey:    :monkey-start, on :green-couch, holding :blank
    red-couch: :red-chest-area, on :floor
    big-pillow: :red-chest-area, on :red-couch
    red-chest: :red-chest-area, on :big-pillow, contains :ladder
    blue-chest: :blue-chest-area, on :ceiling, contains :bananas
    grapes:    :grapes-area, on :ceiling
    blue-couch: :green-chest-area, on :floor
    green-chest: :green-chest-area, on :ceiling, contains :blue-key
    red-key:   :red-key-location, on :floor

  Goal:
    eat :bananas"
  (:require [clara.rules :refer :all]))

(def debug false)
(defn debug! [& args]
  (when debug
    (println (apply str args))))

;; ---------------------------------------------------------------------------
;; Facts
;; ---------------------------------------------------------------------------

(defrecord Monkey [location on-top-of holding])
(defrecord Thing [name location on-top-of weight])
(defrecord Chest [name contents unlocked-by])
(defrecord Goal [action argument-1 argument-2])

(defn goal
  ([action a]
   (->Goal action a nil))
  ([action a b]
   (->Goal action a b)))

;; CLIPS assert is persistent. Clara insert! is logical/truth-maintained,
;; so goals/state transitions must use insert-unconditional!.
(defn insert-goal! [g]
  (insert-unconditional! g))

(defn replace-fact! [old new]
  (retract! old)
  (insert-unconditional! new))

;; ---------------------------------------------------------------------------
;; CHEST UNLOCKING RULES
;; ---------------------------------------------------------------------------

(defrule hold-chest-to-put-on-floor
  [Goal (= :unlock action) (= ?chest argument-1)]
  [Thing (= ?chest name) (= ?on on-top-of) (= :light weight)]
  [Monkey (= ?holding holding)]
  [:test (not= ?on :floor)]
  [:test (not= ?holding ?chest)]
  [:not [Goal (= :hold action) (= ?chest argument-1)]]
  =>
  (debug! "[RULE] hold-chest-to-put-on-floor")
  (insert-goal! (goal :hold ?chest)))

(defrule put-chest-on-floor
  [Goal (= :unlock action) (= ?chest argument-1)]
  [?monkey <- Monkey (= ?place location) (= ?on on-top-of) (= ?chest holding)]
  [?thing <- Thing (= ?chest name)]
  =>
  (debug! "[RULE] put-chest-on-floor")
  (println "Monkey throws the" ?chest "off the" ?on "onto the :floor.")
  (replace-fact! ?monkey (assoc ?monkey :holding :blank))
  (replace-fact! ?thing (assoc ?thing :location ?place :on-top-of :floor)))

(defrule get-key-to-unlock
  [Goal (= :unlock action) (= ?obj argument-1)]
  [Thing (= ?obj name) (= :floor on-top-of)]
  [Chest (= ?obj name) (= ?key unlocked-by)]
  [Monkey (= ?holding holding)]
  [:test (not= ?holding ?key)]
  [:not [Goal (= :hold action) (= ?key argument-1)]]
  =>
  (debug! "[RULE] get-key-to-unlock")
  (insert-goal! (goal :hold ?key)))

(defrule move-to-chest-with-key
  [Goal (= :unlock action) (= ?chest argument-1)]
  [Thing (= ?chest name) (= ?place location) (= :floor on-top-of)]
  [Monkey (= ?current location) (= ?key holding)]
  [Chest (= ?chest name) (= ?key unlocked-by)]
  [:test (not= ?current ?place)]
  [:not [Goal (= :walk-to action) (= ?place argument-1)]]
  =>
  (debug! "[RULE] move-to-chest-with-key")
  (insert-goal! (goal :walk-to ?place)))

(defrule unlock-chest-with-key
  [?goal <- Goal (= :unlock action) (= ?name argument-1)]
  [?chest <- Chest (= ?name name) (= ?contents contents) (= ?key unlocked-by)]
  [Thing (= ?name name) (= ?place location) (= ?on on-top-of)]
  [Monkey (= ?place location) (= ?on on-top-of) (= ?key holding)]
  =>
  (debug! "[RULE] unlock-chest-with-key")
  (println "Monkey opens the" ?name "with the" ?key
           "revealing the" ?contents ".")
  (replace-fact! ?chest (assoc ?chest :contents nil))
  (insert-unconditional!
   (->Thing ?contents ?place ?name :light))
  (retract! ?goal))

;; ---------------------------------------------------------------------------
;; HOLD OBJECT RULES
;; ---------------------------------------------------------------------------

(defrule unlock-chest-to-hold-object
  [Goal (= :hold action) (= ?obj argument-1)]
  [Chest (= ?chest name) (= ?obj contents)]
  [:not [Goal (= :unlock action) (= ?chest argument-1)]]
  =>
  (debug! "[RULE] unlock-chest-to-hold-object")
  (insert-goal! (goal :unlock ?chest)))

;; We intentionally split the old CLIPS rule into two cases.
;;
;; 1. Before the ladder has been taken out of the red chest, find the chest
;;    whose contents are the ladder and create the move goal.
;; 2. Once the ladder exists as a Thing, create the move goal only when the
;;    ladder is somewhere other than the desired destination.
;;
;; This avoids a Clara agenda/negative-node reactivation loop: the old
;; [:not [Thing ...]] condition remains true even after an already-completed
;; move if an activation was queued before the Thing was inserted.

(defrule use-ladder-to-hold-find-ladder
  [Goal (= :hold action) (= ?obj argument-1)]
  [Thing (= ?obj name) (= ?place location) (= :ceiling on-top-of) (= :light weight)]
  [Chest (= ?chest name) (= :ladder contents)]
  [:not [Goal (= :move action) (= :ladder argument-1) (= ?place argument-2)]]
  =>
  (debug! "[RULE] use-ladder-to-hold-find-ladder")
  (insert-goal! (goal :move :ladder ?place)))

(defrule use-ladder-to-hold-move-known-ladder
  [Goal (= :hold action) (= ?obj argument-1)]
  [Thing (= ?obj name) (= ?place location) (= :ceiling on-top-of) (= :light weight)]
  [Thing (= :ladder name) (= ?ladder-place location)]
  [:test (not= ?ladder-place ?place)]
  [:not [Goal (= :move action) (= :ladder argument-1) (= ?place argument-2)]]
  =>
  (debug! "[RULE] use-ladder-to-hold-move-known-ladder")
  (insert-goal! (goal :move :ladder ?place)))

(defrule climb-ladder-to-hold
  [Goal (= :hold action) (= ?obj argument-1)]
  [Thing (= ?obj name) (= ?place location) (= :ceiling on-top-of) (= :light weight)]
  [Thing (= :ladder name) (= ?place location) (= :floor on-top-of)]
  [Monkey (= ?on on-top-of)]
  [:test (not= ?on :ladder)]
  [:not [Goal (= :on action) (= :ladder argument-1)]]
  =>
  (debug! "[RULE] climb-ladder-to-hold")
  (insert-goal! (goal :on :ladder)))

(defrule grab-object-from-ladder
  [?goal <- Goal (= :hold action) (= ?name argument-1)]
  [?thing <- Thing (= ?name name) (= ?place location)
                 (= :ceiling on-top-of) (= :light weight)]
  [Thing (= :ladder name) (= ?place location)]
  [?monkey <- Monkey (= ?place location) (= :ladder on-top-of)
                 (= :blank holding)]
  =>
  (debug! "[RULE] grab-object-from-ladder")
  (println "Monkey grabs the" ?name ".")
  (replace-fact! ?thing (assoc ?thing :location :held :on-top-of :held))
  (replace-fact! ?monkey (assoc ?monkey :holding ?name))
  (retract! ?goal))

(defrule climb-to-hold
  [Goal (= :hold action) (= ?obj argument-1)]
  [Thing (= ?obj name) (= ?place location) (= ?on on-top-of)
         (= :light weight)]
  [Monkey (= ?place location) (= ?monkey-on on-top-of)]
  [:test (not= ?on :ceiling)]
  [:test (not= ?monkey-on ?on)]
  [:not [Goal (= :on action) (= ?on argument-1)]]
  =>
  (debug! "[RULE] climb-to-hold")
  (insert-goal! (goal :on ?on)))

(defrule walk-to-hold
  [Goal (= :hold action) (= ?obj argument-1)]
  [Thing (= ?obj name) (= ?place location) (= ?on on-top-of)
         (= :light weight)]
  [Monkey (= ?current location)]
  [:test (and (not= ?current ?place)
              (not= ?on :ceiling))]
  [:not [Goal (= :walk-to action) (= ?place argument-1)]]
  =>
  (debug! "[RULE] walk-to-hold")
  (insert-goal! (goal :walk-to ?place)))

(defrule drop-to-hold
  [Goal (= :hold action) (= ?obj argument-1)]
  [Thing (= ?obj name) (= ?place location) (= ?on on-top-of)
         (= :light weight)]
  [Monkey (= ?place location) (= ?on on-top-of) (= ?holding holding)]
  [:test (not= ?holding :blank)]
  [:not [Goal (= :hold action) (= :blank argument-1)]]
  =>
  (debug! "[RULE] drop-to-hold")
  (insert-goal! (goal :hold :blank)))

(defrule grab-object
  [?goal <- Goal (= :hold action) (= ?name argument-1)]
  [?thing <- Thing (= ?name name) (= ?place location) (= ?on on-top-of)
         (= :light weight)]
  [?monkey <- Monkey (= ?place location) (= ?on on-top-of)
         (= :blank holding)]
  =>
  (debug! "[RULE] grab-object")
  (println "Monkey grabs the" ?name ".")
  (replace-fact! ?thing (assoc ?thing :location :held :on-top-of :held))
  (replace-fact! ?monkey (assoc ?monkey :holding ?name))
  (retract! ?goal))

(defrule drop-object
  [?goal <- Goal (= :hold action) (= :blank argument-1)]
  [?monkey <- Monkey (= ?place location) (= ?on on-top-of)
         (= ?name holding)]
  [?thing <- Thing (= ?name name)]
  [:test (not= ?name :blank)]
  =>
  (debug! "[RULE] drop-object")
  (println "Monkey drops the" ?name ".")
  (replace-fact! ?monkey (assoc ?monkey :holding :blank))
  (replace-fact! ?thing (assoc ?thing :location ?place :on-top-of ?on))
  (retract! ?goal))

;; ---------------------------------------------------------------------------
;; MOVE OBJECT RULES
;; ---------------------------------------------------------------------------

(defrule unlock-chest-to-move-object
  [Goal (= :move action) (= ?obj argument-1)]
  [Chest (= ?chest name) (= ?obj contents)]
  [:not [Goal (= :unlock action) (= ?chest argument-1)]]
  =>
  (debug! "[RULE] unlock-chest-to-move-object")
  (insert-goal! (goal :unlock ?chest)))

(defrule hold-object-to-move
  [Goal (= :move action) (= ?obj argument-1) (= ?place argument-2)]
  [Thing (= ?obj name) (= ?current location) (= :light weight)]
  [Monkey (= ?holding holding)]
  [:test (not= ?current ?place)]
  [:test (not= ?holding ?obj)]
  [:not [Goal (= :hold action) (= ?obj argument-1)]]
  =>
  (debug! "[RULE] hold-object-to-move")
  (insert-goal! (goal :hold ?obj)))

(defrule move-object-to-place
  [Goal (= :move action) (= ?obj argument-1) (= ?place argument-2)]
  [Monkey (= ?current location) (= ?obj holding)]
  [:test (not= ?current ?place)]
  [:not [Goal (= :walk-to action) (= ?place argument-1)]]
  =>
  (debug! "[RULE] move-object-to-place")
  (insert-goal! (goal :walk-to ?place)))

(defrule drop-object-once-moved
  [?goal <- Goal (= :move action) (= ?name argument-1) (= ?place argument-2)]
  [?monkey <- Monkey (= ?place location) (= ?obj holding)]
  [?thing <- Thing (= ?name name) (= :light weight)]
  =>
  (debug! "[RULE] drop-object-once-moved")
  (println "Monkey drops the" ?name ".")
  (replace-fact! ?monkey (assoc ?monkey :holding :blank))
  (replace-fact! ?thing (assoc ?thing :location ?place :on-top-of :floor))
  (retract! ?goal))

(defrule already-moved-object
  [?goal <- Goal (= :move action) (= ?obj argument-1) (= ?place argument-2)]
  [Thing (= ?obj name) (= ?place location)]
  =>
  (debug! "[RULE] already-moved-object")
  (retract! ?goal))

;; ---------------------------------------------------------------------------
;; WALK TO PLACE RULES
;; ---------------------------------------------------------------------------

(defrule already-at-place
  [?goal <- Goal (= :walk-to action) (= ?place argument-1)]
  [Monkey (= ?place location)]
  =>
  (debug! "[RULE] already-at-place")
  (retract! ?goal))

(defrule get-on-floor-to-walk
  [Goal (= :walk-to action) (= ?place argument-1)]
  [Monkey (= ?current location) (= ?on on-top-of)]
  [:test (and (not= ?current ?place) (not= ?on :floor))]
  [:not [Goal (= :on action) (= :floor argument-1)]]
  =>
  (debug! "[RULE] get-on-floor-to-walk")
  (insert-goal! (goal :on :floor)))

(defrule walk-holding-nothing
  [?goal <- Goal (= :walk-to action) (= ?place argument-1)]
  [?monkey <- Monkey (= ?current location) (= :floor on-top-of)
         (= :blank holding)]
  [:test (not= ?current ?place)]
  =>
  (debug! "[RULE] walk-holding-nothing")
  (println "Monkey walks to" ?place ".")
  (replace-fact! ?monkey (assoc ?monkey :location ?place))
  (retract! ?goal))

(defrule walk-holding-object
  [?goal <- Goal (= :walk-to action) (= ?place argument-1)]
  [?monkey <- Monkey (= ?current location) (= :floor on-top-of)
         (= ?obj holding)]
  [Thing (= ?obj name)]
  [:test (not= ?current ?place)]
  =>
  (debug! "[RULE] walk-holding-object")
  (println "Monkey walks to" ?place "holding the" ?obj ".")
  (replace-fact! ?monkey (assoc ?monkey :location ?place))
  (retract! ?goal))

;; ---------------------------------------------------------------------------
;; GET ON OBJECT RULES
;; ---------------------------------------------------------------------------

(defrule jump-onto-floor
  [?goal <- Goal (= :on action) (= :floor argument-1)]
  [?monkey <- Monkey (= ?on on-top-of)]
  [:test (not= ?on :floor)]
  =>
  (debug! "[RULE] jump-onto-floor")
  (println "Monkey jumps off the" ?on "onto the :floor.")
  (replace-fact! ?monkey (assoc ?monkey :on-top-of :floor))
  (retract! ?goal))

(defrule walk-to-place-to-climb
  [Goal (= :on action) (= ?obj argument-1)]
  [Thing (= ?obj name) (= ?place location)]
  [Monkey (= ?current location)]
  [:test (not= ?current ?place)]
  [:not [Goal (= :walk-to action) (= ?place argument-1)]]
  =>
  (debug! "[RULE] walk-to-place-to-climb")
  (insert-goal! (goal :walk-to ?place)))

(defrule drop-to-climb
  [Goal (= :on action) (= ?obj argument-1)]
  [Thing (= ?obj name) (= ?place location)]
  [Monkey (= ?place location) (= ?holding holding)]
  [:test (not= ?holding :blank)]
  [:not [Goal (= :hold action) (= :blank argument-1)]]
  =>
  (debug! "[RULE] drop-to-climb")
  (insert-goal! (goal :hold :blank)))

(defrule climb-indirectly
  [Goal (= :on action) (= ?obj argument-1)]
  [Thing (= ?obj name) (= ?place location) (= ?on on-top-of)]
  [Monkey (= ?place location) (= ?monkey-on on-top-of) (= :blank holding)]
  [:test (and (not= ?monkey-on ?on)
              (not= ?monkey-on ?obj))]
  [:not [Goal (= :on action) (= ?on argument-1)]]
  =>
  (debug! "[RULE] climb-indirectly")
  (insert-goal! (goal :on ?on)))

(defrule climb-directly
  [?goal <- Goal (= :on action) (= ?obj argument-1)]
  [Thing (= ?obj name) (= ?place location) (= ?on on-top-of)]
  [?monkey <- Monkey (= ?place location) (= ?on on-top-of)
         (= :blank holding)]
  =>
  (debug! "[RULE] climb-directly")
  (println "Monkey climbs onto the" ?obj ".")
  (replace-fact! ?monkey (assoc ?monkey :on-top-of ?obj))
  (retract! ?goal))

(defrule already-on-object
  [?goal <- Goal (= :on action) (= ?obj argument-1)]
  [Monkey (= ?obj on-top-of)]
  =>
  (debug! "[RULE] already-on-object")
  (retract! ?goal))

;; ---------------------------------------------------------------------------
;; EAT OBJECT RULES
;; ---------------------------------------------------------------------------

(defrule hold-to-eat
  [Goal (= :eat action) (= ?obj argument-1)]
  [Monkey (= ?holding holding)]
  [:test (not= ?holding ?obj)]
  [:not [Goal (= :hold action) (= ?obj argument-1)]]
  =>
  (debug! "[RULE] hold-to-eat")
  (insert-goal! (goal :hold ?obj)))

(defrule satisfy-hunger
  [?goal <- Goal (= :eat action) (= ?name argument-1)]
  [?monkey <- Monkey (= ?name holding)]
  [?thing <- Thing (= ?name name)]
  =>
  (debug! "[RULE] satisfy-hunger")
  (println "Monkey eats the" ?name ".")
  (replace-fact! ?monkey (assoc ?monkey :holding :blank))
  (retract! ?goal)
  (retract! ?thing))

;; ---------------------------------------------------------------------------
;; INITIAL STATE
;; ---------------------------------------------------------------------------

(defn initial-facts []
  [(->Monkey :monkey-start :green-couch :blank)

   (->Thing :green-couch :monkey-start :floor :heavy)
   (->Thing :red-couch :red-chest-area :floor :heavy)
   (->Thing :big-pillow :red-chest-area :red-couch :light)
   (->Thing :red-chest :red-chest-area :big-pillow :light)
   (->Chest :red-chest :ladder :red-key)

   (->Thing :blue-chest :blue-chest-area :ceiling :light)
   (->Thing :grapes :grapes-area :ceiling :light)
   (->Chest :blue-chest :bananas :blue-key)

   (->Thing :blue-couch :green-chest-area :floor :heavy)
   (->Thing :green-chest :green-chest-area :ceiling :light)
   (->Chest :green-chest :blue-key :red-key)

   (->Thing :red-key :red-key-location :floor :light)

   (goal :eat :bananas)])

;; ---------------------------------------------------------------------------
;; ENTRY POINT
;; ---------------------------------------------------------------------------

(defn run-mab []
  (debug! "[RUN] creating session")
  (let [session (mk-session)]
    (debug! "[RUN] inserting initial facts")
    (let [session (insert-all session (initial-facts))]
      (debug! "[RUN] firing rules")
      (let [session (fire-rules session)]
        (debug! "[RUN] fire-rules returned")
        session))))

(comment
  (run-mab))
