(ns dativity.core
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [is]])
            [dativity.define :as define]
            [dativity.graph :as graph]
            [clojure.set :refer [union intersection difference]]))

; Not needed in the public api. maybe not in the private either?
(defn- get-data-from-case
  "Returns the data for a given data key."
  {:test (fn []
           (is (= (get-data-from-case {:dativity/commits {:a true}
                                       :a                "so-true"} :a) "so-true")))}
  [case key]
  (key case))

(defn test-process
  "test graph for unit testing purposes, does not make sense really, but is simple."
  []
  (-> (define/empty-case-model)
      (define/add-entity-to-model (define/action :a))
      (define/add-entity-to-model (define/action :d))
      (define/add-entity-to-model (define/action :f))
      (define/add-entity-to-model (define/action :g))
      (define/add-entity-to-model (define/action :i))
      (define/add-entity-to-model (define/data :j))
      (define/add-entity-to-model (define/role :b))         ; doesn't make sense but needs to be some kind of entity
      (define/add-entity-to-model (define/role :c))
      (define/add-entity-to-model (define/role :e))
      (define/add-entity-to-model (define/role :h))
      (define/add-relationship-to-model (define/action-requires :a :b))
      (define/add-relationship-to-model (define/action-requires :a :e))
      (define/add-relationship-to-model (define/action-requires :b :e))
      (define/add-relationship-to-model (define/action-requires :d :e))
      (define/add-relationship-to-model (define/action-requires :i :b))
      (define/add-relationship-to-model (define/action-requires-conditional :i :j (fn [b-data] (> (:power-level b-data) 9000)) :b))
      (define/add-relationship-to-model (define/action-produces :b :c))
      (define/add-relationship-to-model (define/action-produces :d :a))
      (define/add-relationship-to-model (define/action-produces :f :c))
      (define/add-relationship-to-model (define/action-produces :g :e))
      (define/add-relationship-to-model (define/action-produces :g :h))
      (define/add-relationship-to-model (define/role-performs :a :c))
      (define/add-relationship-to-model (define/role-performs :c :d))))

(comment (dativity.visualize/generate-png (test-process)))

(defn add-data                                              ;; TODO: Should subsequent actions be invalidated if the data was already there?
  "Adds data to a case and commits it. Same api as clojure.core/assoc."
  {:test (fn []
           (is (= (add-data {} :case-id 3)
                  {:dativity/commits {:case-id true}
                   :case-id          3}))
           (is (= (add-data {:dativity/commits {:case-id    true
                                                :custmer-id true}
                             :case-id          3
                             :customer-id      "920904"}
                            :loan-number "90291234567")
                  {:case-id          3
                   :customer-id      "920904"
                   :loan-number      "90291234567"
                   :dativity/commits {:case-id     true
                                      :custmer-id  true
                                      :loan-number true}}))
           (is (= (add-data {:dativity/commits {:case-id     true
                                                :custmer-id  true
                                                :loan-number true}
                             :case-id          3
                             :customer-id      "920904"
                             :loan-number      "90291234567"}
                            :loan-details {:amount "1000000" :product "Bolån"})
                  {:dativity/commits {:case-id      true
                                      :custmer-id   true
                                      :loan-number  true
                                      :loan-details true}
                   :case-id          3
                   :customer-id      "920904"
                   :loan-number      "90291234567"
                   :loan-details     {:product "Bolån" :amount "1000000"}})))}
  [case key value]
  (-> (assoc case key value)
      (assoc-in [:dativity/commits key] true)))


(defn all-actions
  "Returns all actions in a case-model."
  {:test (fn []
           (is (= (all-actions (test-process)) #{:a :d :f :g :i})))}
  [process-definition]
  (->> (graph/nodes process-definition)
       (filter (fn [node] (= :action (graph/attr process-definition node :type))))
       (set)))

(defn case-has-data?
  "Returns truthy if the given data node exists regardless if it is committed or not.
   Treats values that are empty seqables as not having data"
  {:test (fn []
           (is (not (case-has-data? {:a {}} :a)))
           (is (not (case-has-data? {:a #{}} :a)))
           (is (not (case-has-data? {:a []} :a)))
           (is (not (case-has-data? {:a ""} :a)))
           (is (not (case-has-data? {:a nil} :a)))
           (is (not (case-has-data? {} :a)))
           (is (case-has-data? {:a "hej"} :a))
           (is (case-has-data? {:a 123} :a))
           (is (case-has-data? {:a 0} :a))
           (is (case-has-data? {:a [1 2 3]} :a))
           (is (case-has-data? {:a #{"a" "b" "c"}} :a))
           (is (case-has-data? {:a {:marco "polo"}} :a))
           (is (case-has-data? {:a {:marco nil}} :a))
           (is (case-has-data? {:a true} :a))
           (is (case-has-data? {:a false} :a)))}
  [case data-key]
  (let [value (data-key case)]
    (if (seqable? value)
      (not-empty value)
      (some? value))))

(defn has-committed-data?
  "Returns true if the given data node exists and is committed"
  {:test (fn []
           (is (has-committed-data? {:dativity/commits {:a true :b true}
                                     :a                "hejhopp"
                                     :b                "yoloswag"}
                                    :a))
           (is (has-committed-data? {:dativity/commits {:a true :b true}
                                     :a                "hejhopp"
                                     :b                "yoloswag"}
                                    :b))
           (is (not (has-committed-data? {:dativity/commits {:a false :b true}
                                          :a                "hejhopp"
                                          :b                "yoloswag"}
                                         :a)))
           (is (not (has-committed-data? {:dativity/commits {:b true}
                                          :b                "yoloswag"}
                                         :a))))}
  [case data-key]
  (and (case-has-data? case data-key)
       (get-in case [:dativity/commits data-key])))

(defn case-has-uncommitted-data?
  "Returns true if the given data node exists on the case and is uncommitted"
  {:test (fn []
           (is (case-has-uncommitted-data? {:a                "dank"
                                            :dativity/commits {:a false}} :a))
           (is (not (case-has-uncommitted-data? (add-data {} :a "yoloswaggins") :a)))
           (is (not (case-has-uncommitted-data? {} :a)))
           )}
  [case data-key]
  (and (case-has-data? case data-key)
       (not (has-committed-data? case data-key))))

(defn- get-conditionally-required-data-nodes-for-action
  {:test (fn []
           (is (= (get-conditionally-required-data-nodes-for-action (test-process) {} :a) #{}))
           (is (= (get-conditionally-required-data-nodes-for-action
                    (test-process)
                    (add-data {} :b {:power-level 9001})
                    :i)
                  #{:j}))
           (is (= (get-conditionally-required-data-nodes-for-action
                    (test-process)
                    (add-data {} :b {:power-level 8999})
                    :i)
                  #{}))
           (is (= (get-conditionally-required-data-nodes-for-action (test-process) {} :i) #{})))}
  [process-definition case action]
  (->> (graph/find-edges process-definition {:src         action
                                             :association :requires-conditional})
       (filter (fn [conditional-requirement]
                 (let [src (:src conditional-requirement)
                       dest (:dest conditional-requirement)
                       condition (graph/attr process-definition src dest :condition)
                       parameter (graph/attr process-definition src dest :data-parameter)]
                   (if (case-has-data? case parameter)
                     (condition (get-data-from-case case parameter))
                     false))
                 ))
       (map :dest)
       (set)))

(defn data-prereqs-for-action                               ;; depends on case to determine conditional requirements
  "Returns data nodes that are required by action nodes. Conditional requirements are included
  if and only if the conditions are true."
  {:test (fn []
           (is (= (data-prereqs-for-action (test-process) {} :a) #{:b :e}))
           (is (= (data-prereqs-for-action (test-process) {} :b) #{:e}))
           (is (= (data-prereqs-for-action (test-process) {} :b) #{:e}))
           (is (= (data-prereqs-for-action (test-process) {} :c) #{}))
           (is (= (data-prereqs-for-action (test-process) {} :i) #{:b}))
           (is (= (data-prereqs-for-action (test-process) (add-data {} :b {:power-level 9001}) :i) #{:j :b}))
           (is (= (data-prereqs-for-action (test-process) (add-data {} :b {:power-level 8999}) :i) #{:b})))}
  [process-definition case action]
  (->> (graph/find-edges process-definition {:src         action
                                             :association :requires})
       (map :dest)
       (set)
       (union (get-conditionally-required-data-nodes-for-action process-definition case action))))

(defn data-produced-by-action
  "Returns all datas that a given action produces."
  {:test (fn []
           (is (= (data-produced-by-action (test-process) :d) #{:a}))
           (is (= (data-produced-by-action (test-process) :b) #{:c}))
           (is (= (data-produced-by-action (test-process) :a) #{})))}
  [process-definition action]
  (->> (graph/find-edges process-definition {:src         action
                                             :association :produces})
       (map :dest)
       (set)))

(defn actions-performed-by-role
  "Returns all actions that a role performs."
  [process-definition role]
  (->> (graph/find-edges process-definition {:src         role
                                             :association :performs})
       (map :dest)
       (set)))

(defn actions-that-require-data
  "Returns all actions that has a dependency to a given data."
  {:test (fn []
           (is (= (actions-that-require-data (test-process) :e) #{:b :d :a}))
           (is (= (actions-that-require-data (test-process) :b) #{:a :i})))}
  [process-definition data]
  (->> (graph/find-edges process-definition {:dest        data
                                             :association :requires})
       (map :src)
       (set)))

(defn- actions-that-can-be-performed-after-action
  {:test (fn []
           (is (= (actions-that-can-be-performed-after-action (test-process) :g) #{:a :b :d})))}
  [process-definition action]
  (apply union (map (fn [data] (actions-that-require-data process-definition data))
                    (data-produced-by-action process-definition action))))

(defn- uncommit-data
  {:test (fn []
           (is (not (-> (add-data {} :a "swell")
                        (uncommit-data :a)
                        (has-committed-data? :a))))
           (is (-> (add-data {} :a "swell")
                   (add-data :b "turnt")
                   (uncommit-data :a)
                   (has-committed-data? :b)))
           (is (= (uncommit-data {} :a) {})))}
  [case key]
  (if (case-has-data? case key)
    (assoc-in case [:dativity/commits key] false)
    case))

(defn- actions-with-prereqs-present
  {:test (fn []
           (is (= (actions-with-prereqs-present (test-process) {}) #{:f :g}))
           (is (= (actions-with-prereqs-present (test-process) (add-data {} :e "yeah")) #{:d :f :g}))
           (is (= (actions-with-prereqs-present (test-process) (add-data {} :b {:power-level 9001})) #{:f :g}))
           (is (= (actions-with-prereqs-present (test-process) (-> {}
                                                                   (add-data :e "yeah")
                                                                   (add-data :b {:power-level 9001})))
                  #{:a :d :f :g}))
           (is (= (actions-with-prereqs-present (test-process) (add-data {} :e "total")) #{:d :f :g}))
           (is (= (actions-with-prereqs-present (test-process) (-> {}
                                                                   (add-data :e "total")
                                                                   (uncommit-data :e)))
                  #{:f :g}))
           (is (= (actions-with-prereqs-present (test-process) (-> {}
                                                                   (add-data :b {:power-level 9001})
                                                                   (add-data :j "radical")))
                  #{:i :f :g}))
           (is (= (actions-with-prereqs-present (test-process) (-> {}
                                                                   (add-data :b {:power-level 9001})
                                                                   (add-data :j "radical")
                                                                   (uncommit-data :j)))
                  #{:f :g}))
           (is (= (actions-with-prereqs-present (test-process) (-> {}
                                                                   (add-data :b {:power-level 9001})))
                  #{:f :g}))
           (is (= (actions-with-prereqs-present (test-process) (-> {}
                                                                   (add-data :b {:power-level 8900})))
                  #{:i :f :g})))}
  [process-definition case]
  (->> (all-actions process-definition)
       (reduce (fn [acc action]
                 (let [prereqs (data-prereqs-for-action process-definition case action)]
                   (if (every? true? (map (fn [prereq] (has-committed-data? case prereq)) prereqs))
                     (conj acc action)
                     acc)))
               #{})))

(defn actions-performed
  "Returns all actions that were performed on a case"
  {:test (fn []
           (is (= (actions-performed (test-process) (add-data {} :a "swag")) #{:d}))
           (is (= (actions-performed (test-process) (-> {}
                                                        (add-data :a "swag")
                                                        (add-data :c "yolo")))
                  #{:d :f}))
           (is (= (actions-performed (test-process) (add-data {} :c "yolo")) #{:f}))
           (is (= (actions-performed (test-process) {}) #{})))}
  [process-definition case]
  (->> (all-actions process-definition)
       (reduce (fn [acc action]
                 (let [produced-data (data-produced-by-action process-definition action)]
                   (cond
                     (empty? produced-data) acc
                     (every? true? (map (fn [data] (has-committed-data? case data)) produced-data)) (conj acc action)
                     :default acc)))
               #{})))

(defn next-actions
  "Returns a set of actions that are allowed to perform and are also not yet performed.
  If a role is provided then only actions that are performed by that role are returned"
  ([process-definition case]
   (difference (actions-with-prereqs-present process-definition case)
               (actions-performed process-definition case)))
  ([process-definition case role]
   (intersection (next-actions process-definition case)
                 (actions-performed-by-role process-definition role))))

(defn action-allowed?
  "Returns true if the given action has all data dependencies satisfied, otherwise false."
  {:test (fn []
           (is (false? (action-allowed? (test-process) {} :a)))
           (is (false? (action-allowed? (test-process) {:e "yeah"} :a)))
           (is (true? (action-allowed? (test-process) (-> {}
                                                          (add-data :b {:power-level 9001})
                                                          (add-data :e "yeah")) :a)))
           (is (true? (action-allowed? (test-process) (add-data {} :e "yeah") :d)))
           (is (false? (action-allowed? (test-process) (add-data {} :c "yeah") :d)))
           (is (false? (action-allowed? (test-process) (-> {}
                                                           (add-data :a "lol")
                                                           (add-data :b {:power-level 9001})
                                                           (add-data :c "yeah")) :d))))}
  [process-definition case action]
  (contains? (actions-with-prereqs-present process-definition case) action))

(defn- uncommit-datas-produced-by-action
  "uncommits all data nodes that have a production edge from the specified action node"
  {:test (fn []
           (is (true? (as-> {} case
                            (add-data case :e "ey")
                            (add-data case :h "kolasås")
                            (add-data case :a "sås")
                            (uncommit-datas-produced-by-action (test-process) case :g)
                            (every? false? [(has-committed-data? case :e)
                                            (has-committed-data? case :h)])))))}
  [process-definition case action]
  (loop [loop-case case
         [data & datas] (vec (data-produced-by-action process-definition action))]
    (if data
      (recur (uncommit-data loop-case data)
             datas)
      loop-case)))

(defn invalidate-action
  "Uncommits the data produced by the specified action, and then recursively performs
  the same procedure on all actions that require the data produced by the specified action."
  {:test (fn []
           (as-> {} case
                 (add-data case :c "far out")
                 (add-data case :h "no way")
                 (invalidate-action (test-process) case :b)
                 (do
                   (is (not (has-committed-data? case :c)))
                   (is (has-committed-data? case :h))
                   (is (not (action-allowed? (test-process) case :b)))))
           (as-> {} case
                 (add-data case :c "far out")
                 (add-data case :e "yoloswaggins")
                 (add-data case :a "dope")
                 (add-data case :h "fuego")
                 (invalidate-action (test-process) case :g)
                 (do
                   (is (false? (has-committed-data? case :c)))
                   (is (false? (has-committed-data? case :e)))
                   (is (false? (has-committed-data? case :a)))
                   (is (false? (has-committed-data? case :h)))))
           (as-> {} case
                 (add-data case :c "far out")
                 (add-data case :e "yoloswaggins")
                 (add-data case :a "dope")
                 (add-data case :h "fuego")
                 (invalidate-action (test-process) case :d)
                 (is (not-any? false? [(has-committed-data? case :h)
                                       (has-committed-data? case :e)]))
                 (is (not-any? true? [(has-committed-data? case :c)
                                      (has-committed-data? case :a)]))))}
  [process-definition case action]
  (loop [loop-case case
         [loop-action & loop-actions] [action]
         seen-actions #{}]
    (if loop-action
      (recur (uncommit-datas-produced-by-action process-definition loop-case loop-action)
             (vec (difference (set (concat (actions-that-can-be-performed-after-action process-definition loop-action) loop-actions)) seen-actions))
             (conj seen-actions loop-action))
      loop-case)))

