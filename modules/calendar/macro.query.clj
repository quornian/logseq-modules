{:inputs [:current-block :today :-14d :+21d] ;; Note: keep in sync with view

 :query [:find ?today
         (pull ?calendar
               [:block/uuid
                :block/content
                :block/properties])
         (pull ?entry
               [:block/uuid
                :block/content
                :block/scheduled
                :block/marker
                {:block/page [:block/journal-day]}])
         :keys today calendar entry
         :in $ ?calendar ?today ?after ?before
         :where
         [?journal :block/journal? true]
         [?journal :block/journal-day ?journal-day]
         [(> ?journal-day ?after)]   ;; Note: these overshoot depending on the
         [(< ?journal-day ?before)]  ;; day of the week, the view will ignore them

         (or-join [?journal ?journal-day ?entry]
                  ;; Include all "events": blocks on journals that start "HH:MM - "
                  (and
                   [?entry :block/parent ?journal]
                   [?entry :block/content ?content]
                   [(re-pattern "^\\d\\d:\\d\\d - ") ?event-pattern]
                   [(re-find ?event-pattern ?content) _])
                  ;; Include all blocks scheduled for the day (tasks or otherwise)
                  (and
                   [?entry :block/scheduled ?journal-day])
                  ;; Include all tasks created on the day that are not scheduled
                  ;; These are assumed to be same-day tasks
                  (and
                   [?entry :block/marker _]
                   [?entry :block/page ?journal]
                   (not [?entry :block/scheduled])))]

 :result-transform
 (fn [results] (let [{:keys [today calendar]} (first results)
                     {tasks false, events true} (group-by
                                                 (fn [et] (nil? (:block/marker et)))
                                                 (map :entry results))
                     extract-day (fn [e] (or (:block/scheduled e)
                                             (:block/journal-day (:block/page e))))
                     events (group-by extract-day events)]
                 [{:today today
                   :calendar calendar
                   :tasks (group-by extract-day tasks)
                   :events (dissoc events nil)}]))

 :view :calendar}
