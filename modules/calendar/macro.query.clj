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
         (or-join [?after ?before ?journal-day ?entry]
                  ;; Include all "events": blocks on journals that start "HH:MM - "
                  (and
                   [?journal :block/journal? true]
                   [?journal :block/journal-day ?journal-day]
                   [(> ?journal-day ?after)]
                   [(< ?journal-day ?before)]
                   [?entry :block/parent ?journal]
                   [?entry :block/content ?content]
                   [(re-pattern "^\\d\\d:\\d\\d - ") ?event-pattern]
                   [(re-find ?event-pattern ?content) _])
                  ;; Include all blocks scheduled for the day (tasks or otherwise)
                  (and
                   [?entry :block/scheduled ?journal-day]
                   [(> ?journal-day ?after)]
                   [(< ?journal-day ?before)])
                  ;; Include all tasks created on the day that are not scheduled
                  ;; These are assumed to be same-day tasks
                  (and
                   [?journal :block/journal? true]
                   [?journal :block/journal-day ?journal-day]
                   [(> ?journal-day ?after)]
                   [(< ?journal-day ?before)]
                   [?entry :block/marker _]
                   [?entry :block/page ?journal]
                   (not [?entry :block/scheduled])))]

 :result-transform :calendar
 :view :calendar}
