(fn [[{:keys [today calendar tasks events]}]]
  (let [% mod, ÷ quot

        ;; Calendar view settings
        settings (:block/properties calendar)
        show-weekends (get settings :calendar/show-weekends true)
        highlight-overdue (get settings :calendar/highlight-overdue false)

        ;; User preferences
        ;; :journal/page-title-format "yyyy-MM-dd EEEE"
        config (js->clj (call-api "get_current_graph_configs"))
        journal-format (get config "journal/page-title-format")

        ;; Extract a YYYYMMDD number into a [YYYY MM DD] sequence
        ymd->seq (fn [ymd] [(÷ ymd 10000) (÷ (% ymd 10000) 100) (% ymd 100)])

        ;; Calculate the Julian Day Number of a given date
        ;; https://en.wikipedia.org/wiki/Julian_day
        ymd->jdn
        (fn ymd->jdn
          ([ymd] (apply ymd->jdn (ymd->seq ymd)))
          ([y m d]
           (let [a (÷ (- m 14) 12), b (* 1461 (+ y 4800 a))
                 c (* 367 (- m 2 (* 12 a))), e (÷ (+ y 4900 a) 100)]
             (+ (÷ b 4) (÷ c 12) (* -3 (÷ e 4)) d -32075))))

        ;; Calculate the Gregorian date from a given Julian Day Number
        ;; https://en.wikipedia.org/wiki/Julian_day
        jdn->ymd
        (fn [j]
          (let [a (+ j 1401 (÷ (* (÷ (+ (* 4 j) 274277) 146097) 3) 4) -38)
                b (+ (* 4 a) 3), h (+ (* 5 (÷ (% b 1461) 4)) 2)
                d (+ (÷ (% h 153) 5) 1), m (+ (% (+ (÷ h 153) 2) 12) 1)
                y (+ (÷ b 1461) -4716 (÷ (- 12 -2 m) 12))]
            (+ (* 10000 y) (* 100 m) d)))

        ;; Date offset functions
        ymd- (fn [ymd sub] (jdn->ymd (- (ymd->jdn ymd) sub)))
        ymd+ (fn [ymd sub] (jdn->ymd (+ (ymd->jdn ymd) sub)))

        ;; Weekday number with zero denoting Sunday
        jdn->wd (fn [jdn] (% (+ jdn 1) 7))
        ymd->wd (fn [ymd] (jdn->wd (ymd->jdn ymd)))

        ;; Names for weekdays and months
        weekday ["Sunday" "Monday" "Tuesday" "Wednesday"
                 "Thursday" "Friday" "Saturday" "Sunday"]
        month ["December" "January" "February" "March" "April" "May" "June"
               "July" "August" "September" "October" "November" "December"]

        ;; Format dates as strings
        format-date
        (fn [ymd fmt]
          (let [j (ymd->jdn ymd)
                [y m d] (ymd->seq ymd)]
            (clojure.string/replace
             fmt
             (re-pattern "%[YmdA]|yyyy|MM|dd|EEEE")
             (fn [match]
               (case match
                 "%Y" (str y)
                 "yyyy" (str y)
                 "%m" (str (when (< m 10) "0") m)
                 "MM" (str (when (< m 10) "0") m)
                 "%d" (str (when (< d 10) "0") d)
                 "dd" (str (when (< d 10) "0") d)
                 "%A" (weekday (ymd->wd ymd))
                 "EEEE" (weekday (ymd->wd ymd))
                 match)))))

        ;; Offset a date back to the start of the week
        week-start (fn [ymd] (ymd- ymd (ymd->wd ymd)))

        ;; First line of a block's content
        block-text (fn [b] (first (clojure.string/split-lines (:block/content b))))

        ;; Strip a block's content of any formatting characters
        unformat-text
        (fn [text]
          (let [to-strip (str "^(TODO|LATER|DOING|NOW|DONE|CANCELED) "
                              "|](\\[^)]+\\)"          ;; Links ](...)
                              "|[\\[\\]]+"             ;; Any other [ or ]
                              "|\\{\\{[^}]*\\}\\}"     ;; Macros {{...}}
                              "|\\(\\([^)]*\\)\\)"     ;; Block refs ((...))
                              "|\\*\\*|\\~\\~| - ")]   ;; ** or ~~ or " - "
            (clojure.string/replace text (re-pattern to-strip) " ")))
        unformat-block (fn [block] (unformat-text (block-text block)))

        ;; Properties for use in an [:a ...] element linking to a block or page
        page-link
        (fn page-link [page-name]
          {:href (str "#/page/" page-name)
           :on-click
           (fn [e] (if (aget e "shiftKey")
                     (call-api "open_in_right_sidebar"
                               (aget (call-api "get_page" page-name) "uuid"))))})
        block-link
        (fn block-link [uuid]
          {:href (str "#/page/" uuid)
           :on-click
           (fn [e] (if (aget e "shiftKey")
                     (call-api "open_in_right_sidebar" uuid)))})]

    ;; The main view
    [:div.lsm-calendar
     {:class [(when (not show-weekends) "lsm-hide-weekends")]}

     ;; Day of week headings
     (map (fn [wd] [:h2 (weekday wd)]) (range 7))

     ;; Cells for each day
     (let [current (ymd->jdn today)
           start (- current (jdn->wd current) 7)
           visible-days (range start (+ start 28))
           event-pattern (re-pattern "^(\\d\\d:\\d\\d) - (.*)")]
       (map
        (fn [day]
          (let [ymd (jdn->ymd day), past (< ymd today)]
            [:div {:class (if past "lsm-past" (if (> ymd today) "lsm-future" "lsm-today"))}

             ;; Numeric day of month
             [:a.page-ref (page-link (format-date ymd journal-format)) (% ymd 100)]

             ;; Incomplete tasks
             [:ul
              (map
               (fn [e]
                 [:li.lsm-task {:title (:block/content e)
                                :class [(:block/marker e)
                                        (when (:block/scheduled e) "lsm-scheduled")
                                        (when (and past highlight-overdue) "lsm-overdue")]}
                  [:a.block-ref (block-link (:block/uuid e)) (unformat-block e)]])
               (reverse
                (sort-by :block/marker
                         (filter (fn [task] (not (contains? #{"DONE" "CANCELED"} (:block/marker task))))
                                 (get tasks ymd)))))]
             ;; Events
             [:ul
              (map
               (fn [e]
                 [:li.lsm-event {:title (:block/content e)
                                 :class (:block/marker e)}
                  [:a.block-ref (block-link (:block/uuid e))
                   (if-let [match (re-find event-pattern (block-text e))]
                     (let [[_ time content] match] [[:span time] (unformat-text content)])
                     (unformat-block e))]])
               (sort-by :block/content (get events ymd)))]

             ;; Complete/canceled tasks
             [:ul
              (map
               (fn [e]
                 [:li.lsm-task {:title (:block/content e)
                                :class [(:block/marker e)
                                        (when (:block/scheduled e) "lsm-scheduled")]}
                  [:a.block-ref (block-link (:block/uuid e)) (unformat-block e)]])
               (reverse
                (sort-by :block/marker
                         (filter (fn [task] (contains? #{"DONE" "CANCELED"} (:block/marker task)))
                                 (get tasks ymd)))))]]))

        visible-days))]))
