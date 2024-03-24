{:inputs [:today :-13d :+27d]
 :query
 [:find ?today (pull ?task-or-event
                     [:block/uuid
                      :block/content
                      :block/scheduled
                      :block/marker
                      {:block/page [:block/journal-day]}])
  :in $ ?today ?first-date ?last-date
  :where
  [?journal :block/journal? true]
  [?journal :block/journal-day ?journal-day]
  [(>= ?journal-day ?first-date)]
  [(<= ?journal-day ?last-date)]

  (or-join [?journal ?journal-day ?task-or-event]
   (and
    [?task-or-event :block/parent ?journal]
    [?task-or-event :block/content ?content]
    [(re-pattern "^\\d\\d:\\d\\d - ") ?event-pattern]
    [(re-find ?event-pattern ?content) _])
   (and
    [?task-or-event :block/scheduled ?journal-day])
   (and
    [?task-or-event :block/marker _]
    [?task-or-event :block/page ?journal]
    (not [?task-or-event :block/scheduled]))
  )
    ]
 :result-transform
 (fn [results]
   (let [today (first results)
         events-and-tasks (take-nth 2 (drop 1 results))
         {tasks false, events true} (group-by (fn [et] (nil? (:block/marker et)))
                                              events-and-tasks)
         extract-day (fn [e] (or (:block/scheduled e) (:block/journal-day (:block/page e))))]
     [{:today today
       :tasks (group-by extract-day tasks)
       :events (group-by extract-day events)
      }]))
 :view
 (fn [[{:keys [today tasks events]}]]
   (let [;; Some operators for convenience
         % mod
         ÷ quot
         ;;
         ;; Extract a YYYYMMDD number into a [YYYY MM DD] sequence
         ;;
         ymd->seq
         (fn ymd->seq [ymd]
           [(÷ ymd 10000)
            (÷ (% ymd 10000) 100)
            (% ymd 100)])
         ;;
         ;; Calculate the Julian Day Number of a given date
         ;; https://en.wikipedia.org/wiki/Julian_day
         ;;
         ymd->jdn
         (fn ymd->jdn
           ([ymd] (apply ymd->jdn (ymd->seq ymd)))
           ([y m d]
            (let [a (÷ (- m 14) 12), b (* 1461 (+ y 4800 a))
                  c (* 367 (- m 2 (* 12 a))), e (÷ (+ y 4900 a) 100)]
              (+ (÷ b 4) (÷ c 12) (* -3 (÷ e 4)) d -32075))))
         ;;
         ;; Calculate the Gregorian date from a given Julian Day Number
         ;; https://en.wikipedia.org/wiki/Julian_day
         ;;
         jdn->ymd
         (fn jdn->ymd [j]
           (let [a (+ j 1401 (÷ (* (÷ (+ (* 4 j) 274277) 146097) 3) 4) -38)
                 b (+ (* 4 a) 3), h (+ (* 5 (÷ (% b 1461) 4)) 2)
                 d (+ (÷ (% h 153) 5) 1), m (+ (% (+ (÷ h 153) 2) 12) 1)
                 y (+ (÷ b 1461) -4716 (÷ (- 12 -2 m) 12))]
             (+ (* 10000 y) (* 100 m) d)))
         ;;
         ;; Mathematics functions
         ;;
         ymd-> (fn [f] (fn [ymd & args] (jdn->ymd (apply f (cons (ymd->jdn ymd) args)))))
         ymd- (ymd-> -)
         ymd+ (ymd-> +)
         
         ;;
         ;; Weekday number with zero denoting Sunday
         ;;
         jdn->wd (fn jdn->wd [jdn] (% (+ jdn 1) 7))
         ymd->wd (fn ymd->wd [ymd] (-> ymd ymd->jdn jdn->wd))
         ;;
         ;; Names for weekdays and months
         ;;
         weekday ["Sunday" "Monday" "Tuesday" "Wednesday"
                  "Thursday" "Friday" "Saturday" "Sunday"]
         month ["December" "January" "February" "March" "April" "May" "June"
                "July" "August" "September" "October" "November" "December"]
         ;;
         ;;
         ;;
         format-date
         (fn format-date [ymd fmt]
           (let [j (ymd->jdn ymd)
                 [y m d] (ymd->seq ymd)]
             (clojure.string/replace
              fmt
              (re-pattern "%.")
              (fn [match]
                (case match
                      "%Y" (str y)
                      "%m" (str m)
                      "%d" (str d)
                      match)))))
         ;;
         ;;
         ;;
         week-start
         (fn week-start [ymd] (ymd- ymd (ymd->wd ymd)))
         ;;
         ;;
         ;;
         block-text (fn [b] (first (clojure.string/split-lines (:block/content b))))
         ;;
         ;; Render a block's content
         ;;
         render-block
         (fn render-block [block]
           (let [pattern (re-pattern "\\(\\([^)]*\\)\\)|\\{\\{[^}]*\\}\\}|[\\[\\]]+|\\*+|\\~+| - ")]
             (clojure.string/replace (block-text block) pattern " ")))
         ;;
         ;;
         ;;
         ref-link
         (fn ref-link [ref]
           {:href (str "#/page/" (:block/uuid ref))
            :on-click
            (fn [e] (if (aget e "shiftKey")
                      (call-api "open_in_right_sidebar" (:block/uuid ref))
                      (call-api "push_state" :page {:name (:block/uuid ref)})))})]
     [:div.lsm-calendar
      ;;
      ;; Day of week headings
      ;;
      (map (fn [wd] [:strong (weekday wd)]) (range 7))
      ;;
      ;; Cells
      ;; 
      (let [current (ymd->jdn today)
            start (- current (jdn->wd current) 7)
            visible-days (range start (+ start 35))]
        (map
         (fn [day]
           (let [ymd (jdn->ymd day)]
             [:div {:class (if (< ymd today) "past" (if (> ymd today) "future" "today"))}
              [:u (last (ymd->seq (jdn->ymd day)))]
              ;;
              ;; Tasks
              [:ul
               (map
                (fn [e]
                  [:li {:title (:block/content e)
                        :class [(:block/marker e)
                                (when (:block/scheduled e) "scheduled")]}
                   [:a.block-ref (ref-link e) (render-block e)]])
                (reverse (sort-by :block/marker (get tasks ymd))))
              ]
              ;;
              ;; Events
              [:ul
               (map
                (fn [e]
                  [:li {:title (:block/content e)
                        :class (:block/marker e)}
                   [:a.block-ref (ref-link e) (render-block e)]])
                (sort-by :block/content (get events ymd)))
              ]
             ]))
         visible-days))]))}
