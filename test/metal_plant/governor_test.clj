(ns metal-plant.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [metal-plant.store :as store]
            [metal-plant.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-furnace! st {:furnace-id "furnace-1" :max-safe-temp-c 1200})
    (store/register-batch! st {:batch-id "batch-1" :furnace-id "furnace-1" :spec "alloy-A"})
    st))

(deftest proceeds-on-clean-temp-reading
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :temp-reading :batch-id "batch-1" :temp-c 1000
                   :safety-class :low :effect :propose :confidence 0.9}]
    (is (= :proceed (:decision (governor/assess env proposal))))))

(deftest holds-on-unregistered-batch
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :temp-reading :batch-id "no-such-batch" :temp-c 1000
                   :safety-class :low :effect :propose :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :no-batch (:rule %)) (:violations result)))))

(deftest holds-on-no-actuation-violation
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :temp-reading :batch-id "batch-1" :temp-c 1000
                   :safety-class :low :effect :direct-write :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :no-actuation (:rule %)) (:violations result)))))

(deftest holds-on-overtemp-reading-without-high-safety-class
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :temp-reading :batch-id "batch-1" :temp-c 1300
                   :safety-class :medium :effect :propose :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :overtemp-safety (:rule %)) (:violations result)))))

(deftest human-approval-on-overtemp-reading-with-high-safety-class
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :temp-reading :batch-id "batch-1" :temp-c 1300
                   :safety-class :high :effect :propose :confidence 0.9}]
    (is (= :human-approval (:decision (governor/assess env proposal))))))

(deftest human-approval-on-low-confidence
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :temp-reading :batch-id "batch-1" :temp-c 1000
                   :safety-class :none :effect :propose :confidence 0.2}
        result (governor/assess env proposal)]
    (is (= :human-approval (:decision result)))
    (is (= :low-confidence (:reason result)))))

(deftest store-records-append-only
  (let [st (fresh-store)]
    (store/record-temp-reading! st {:reading-id "r1" :batch-id "batch-1" :temp-c 950})
    (store/record-quality-sample! st {:sample-id "s1" :batch-id "batch-1" :result :pass})
    (is (= 1 (count (store/temp-readings-of st "batch-1"))))
    (is (= 1 (count (store/quality-samples-of st "batch-1"))))
    (is (= 1 (count (store/batches-of st "furnace-1"))))))
