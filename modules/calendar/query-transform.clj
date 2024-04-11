(fn [results]
  (let [{:keys [today calendar]} (first results)
        ;; Extract the day for the given calendar entry
        ->day
        (fn [e] (or (:block/scheduled e)
                    (:block/journal-day (:block/page e))))

        ;; Extract/separate time and non-time content from an entry
        ;; Supports:
        ;;    10:30 - Content
        ;;    Content\nSCHEDULED: <2024-04-11 Thu 12:30>
        time-pattern
        (re-pattern
         (str "^(\\d\\d:\\d\\d) - (.*)"             ;; "10:30 - Event..."
              "|^(.*)\\n"                           ;; [OR] "Event\n"
              "(?:.*\\n)*"                          ;; "...\n" (0 or more)
              "SCHEDULED: <[^>]+ (\\d\\d:\\d\\d)>"  ;; "SCHEDULED <... 10:30>"
              "|^(.*)"))                            ;; "Anything else"
        ->time-and-title
        (fn [e] (when-let [[_ t0 c0 c1 t1 c2] (re-find time-pattern (:block/content e))]
                  [(or t0 t1) (or c0 c1 c2)]))

        ;; Strip markers and formatting characters to form text title
        unformat-text
        (fn [text]
          (when text
            (let [to-strip (str "^(TODO|LATER|DOING|NOW|DONE|CANCELL?ED) "
                                "|](\\[^)]+\\)"          ;; Links ](...)
                                "|[\\[\\]]+"             ;; Any other [ or ]
                                "|\\{\\{[^}]*\\}\\}"     ;; Macros {{...}}
                                "|\\(\\([^)]*\\)\\)"     ;; Block refs ((...))
                                "|\\*\\*|\\~\\~| - ")]   ;; ** or ~~ or " - "
              (clojure.string/replace text (re-pattern to-strip) " "))))

        ;; Create a compact entry description for the view
        make-entry
        (fn [{block :entry}]
          (let [[time title] (->time-and-title block)]
            {:day (->day block)
             :time time
             :local (nil? (:block/scheduled block))
             :uuid (:block/uuid block)
             :marker (:block/marker block)
             :title (unformat-text title)}))

        ;; Sort events in the following order:
        ;; 1. Untimed events
        ;; 2. Untimed, incomplete tasks
        ;; 3. Timed tasks and events, by time
        ;; 4. Untimed, complete (or canceled) tasks
        entry-sort
        (fn [{:keys [time marker title]}]
          [(and (nil? time) (contains? #{"DONE" "CANCELED" "CANCELLED"} marker))
           time
           marker
           title])

        demo (get (:block/properties calendar) :calendar/demo nil)
        demo-filter (fn [result] (clojure.string/includes? (:block/content (:entry result)) demo))]

    [{:today today
      :calendar calendar
      :days (group-by :day
                      (sort-by entry-sort
                               (map make-entry (if demo
                                                 (filter demo-filter results)
                                                 results))))}]))
