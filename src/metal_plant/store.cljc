(ns metal-plant.store
  "SSoT for the ISCO-08 8121 independent metal-processing-plant
  sole-proprietor actor, behind a `Store` protocol so the backend is a
  swap (MemStore default ‖ a real Datomic/kotoba-server backend, per the
  itonami actor pattern).

  Domain = independent metal processing plant operations:

    furnace          — a processing furnace (furnaceId, maxSafeTempC)
    batch            — a production batch scoped to a furnace (batchId,
                       furnaceId, spec)
    temp-reading     — a temperature reading under a batch (readingId,
                       batchId, tempC)
    quality-sample   — a quality-check sample (sampleId, batchId, result
                       #{:pass :fail})

  The append-only records are the operating ledger: a temp-reading or
  quality-sample must reference a registered batch on a registered
  furnace, and temp-readings/quality-samples are never mutated in place,
  only appended.")

(defprotocol Store
  (furnace [st furnace-id])
  (batch [st batch-id])
  (batches-of [st furnace-id])
  (temp-readings-of [st batch-id])
  (quality-samples-of [st batch-id])
  (register-furnace! [st furnace])
  (register-batch! [st batch])
  (record-temp-reading! [st temp-reading])
  (record-quality-sample! [st quality-sample]))

(defrecord MemStore [state]
  Store
  (furnace [_ furnace-id]
    (get-in @state [:furnaces furnace-id]))
  (batch [_ batch-id]
    (get-in @state [:batches batch-id]))
  (batches-of [_ furnace-id]
    (filter #(= furnace-id (:furnace-id %)) (vals (:batches @state))))
  (temp-readings-of [_ batch-id]
    (filter #(= batch-id (:batch-id %)) (:temp-readings @state)))
  (quality-samples-of [_ batch-id]
    (filter #(= batch-id (:batch-id %)) (:quality-samples @state)))
  (register-furnace! [_ furnace]
    (swap! state assoc-in [:furnaces (:furnace-id furnace)] furnace))
  (register-batch! [_ batch]
    (swap! state assoc-in [:batches (:batch-id batch)] batch))
  (record-temp-reading! [_ temp-reading]
    (swap! state update :temp-readings (fnil conj []) temp-reading))
  (record-quality-sample! [_ quality-sample]
    (swap! state update :quality-samples (fnil conj []) quality-sample)))

(defn mem-store
  ([] (mem-store {}))
  ([seed]
   (->MemStore (atom (merge {:furnaces {} :batches {} :temp-readings [] :quality-samples []} seed)))))
