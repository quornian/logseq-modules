(fn [results]
  (map
   (fn [[goal items]]
     (let [goal-def (last (sort-by :target items))]
       {:name goal
        :definition (:block goal-def)
        ;; Add this goal's blocks' :goal-add, defaulting to 1 for most blocks and 0 for target definitions
        :value (sum (map (fn [{add :add target :target}]
                           (if (not= add -1) add (if (not= target -1) 0 1)))
                         (filter (fn [item] (= (:marker item) "DONE")) items)))
        ;; Take the maximum of any target definitions
        :target (:target goal-def)}))
   (group-by :goal results)))
