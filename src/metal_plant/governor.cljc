(ns metal-plant.governor
  "MetalPlantGovernor — the independent safety/traceability layer for the
  ISCO-08 8121 independent metal-processing-plant actor. The Process
  Advisor proposes actions (temp-reading, quality-sample); it has no
  notion of batch provenance or overtemp risk, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD — the
  itonami-actor pattern (independent Governor gates a proposing actor)
  applied to this occupation.

  Charter (mirrors ADR-2607011000 robotics premise + ADR-2607012000
  cloud-itonami-isco): the actor never dispatches a robot action or writes an
  operating record the governor refuses. A temp-reading that exceeds the
  furnace's `max-safe-temp-c` ALWAYS requires human sign-off — it can never
  be auto-approved, only recorded and escalated.

  HARD invariants for :metal-plant/propose:
    1. Batch provenance   — a temp-reading or quality-sample must
       reference a registered batch on a registered furnace.
    2. No-actuation       — the proposal must not directly mutate a
       temp-reading or quality-sample record outside the
       record-temp-reading!/record-quality-sample! path (effect must be
       :propose, never a raw store write).
    3. Overtemp safety    — a temp-reading whose `temp-c` exceeds the
       furnace's `max-safe-temp-c` always requires :high or higher
       safety-class, forcing human sign-off; it is never auto-approved
       regardless of confidence.
  SOFT:
    4. Confidence floor → escalate."
  (:require [metal-plant.store :as store]))

(def confidence-floor 0.6)
(def safety-classes [:none :low :medium :high :safety-critical])

(defn- safety-rank [safety-class]
  (let [idx (.indexOf safety-classes safety-class)]
    (if (neg? idx) 0 idx)))

(defn- overtemp? [max-safe-temp-c proposal]
  (and (= :temp-reading (:kind proposal))
       (number? max-safe-temp-c)
       (number? (:temp-c proposal))
       (> (:temp-c proposal) max-safe-temp-c)))

(defn- hard-violations [{:keys [batch-fn furnace-fn]} proposal]
  (let [{:keys [batch-id safety-class effect]} proposal
        found-batch (batch-fn batch-id)
        found-furnace (when found-batch (furnace-fn (:furnace-id found-batch)))]
    (cond-> []
      (nil? found-batch)
      (conj {:rule :no-batch :detail (str "未登録 batch " batch-id)})

      (not= :propose effect)
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and found-furnace
           (overtemp? (:max-safe-temp-c found-furnace) proposal)
           (< (safety-rank (or safety-class :none)) (safety-rank :high)))
      (conj {:rule :overtemp-safety
             :detail "max-safe-temp-c を超える temp-reading は :high 以上の safety-class が必須"}))))

(defn assess
  "Assess a proposal against `env` (a map with `:batch-fn`/`:furnace-fn`
  lookups, decoupled from any concrete Store so this stays pure). Returns
  `{:decision :proceed|:hold|:human-approval :violations [...] :confidence n}`."
  [env proposal]
  (let [violations (hard-violations env proposal)
        safety-class (or (:safety-class proposal) :none)
        confidence (or (:confidence proposal) 1.0)]
    (cond
      (seq violations)
      {:decision :hold :violations violations :confidence confidence}

      (>= (safety-rank safety-class) (safety-rank :high))
      {:decision :human-approval :violations [] :confidence confidence}

      (< confidence confidence-floor)
      {:decision :human-approval :violations [] :confidence confidence
       :reason :low-confidence}

      :else
      {:decision :proceed :violations [] :confidence confidence})))

(defn env-for-store
  "Build the decoupled env map `assess` needs from a concrete
  `metal-plant.store/Store` implementation."
  [store]
  {:batch-fn #(store/batch store %)
   :furnace-fn #(store/furnace store %)})
