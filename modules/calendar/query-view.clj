(fn [[{:keys [today calendar days]}]]
  (let [% mod, ÷ quot

        ;; Calendar view settings
        settings (:block/properties calendar)
        show-weekends (get settings :calendar/show-weekends true)
        highlight-overdue (get settings :calendar/highlight-overdue true)

        ;; User preferences
        ;; :journal/page-title-format "yyyy-MM-dd EEEE"
        config (js->clj (call-api "get_current_graph_configs"))
        journal-format (get config "journal/page-title-format" "MMM do, yyyy")

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

        ;; Weekday number with zero denoting Sunday
        jdn->wd (fn [jdn] (% (+ jdn 1) 7))
        ymd->wd (fn [ymd] (jdn->wd (ymd->jdn ymd)))

        ;; Names for weekdays and months
        weekday ["Sunday" "Monday" "Tuesday" "Wednesday"
                 "Thursday" "Friday" "Saturday" "Sunday"]
        month ["December" "January" "February" "March" "April" "May" "June"
               "July" "August" "September" "October" "November" "December"]

        ;; Ordinal suffix for a given day of the month: 1st 2nd...11th...31st
        ordinal-d
        (fn [d] (let [r (% d 10)] (if (or (> r 3) (and (> d 10) (< d 14)))
                                    "th" (nth ["th" "st" "nd" "rd"] r))))

        ;; Format dates as strings
        ;; A subset of https://cljdoc.org/d/com.andrewmcveigh/cljs-time/0.5.2/api/cljs-time.format#formatters
        format-date
        (fn [ymd fmt]
          ;; (log "(format-date " ymd " " fmt ")")
          (let [j (ymd->jdn ymd)
                [y m d] (ymd->seq ymd)]
            (clojure.string/replace
             fmt
             (re-pattern "y+|M+|d+|E+|o")
             (fn [match]
               (case match
                 ("y" "yy" "yyy" "yyyy") (str y)
                 "M" (str m)
                 "MM" (str (when (< m 10) "0") m)
                 "MMM" (subs (month m) 0 3)
                 "MMMM" (month m)
                 "d" (str d)
                 "dd" (str (when (< d 10) "0") d)
                 ("E" "EE" "EEE") (subs (weekday (ymd->wd ymd)) 0 3)
                 "EEEE" (weekday (ymd->wd ymd))
                 "o" (ordinal-d d)
                 match)))))

        ;; Properties for use in an [:a ...] element linking to a block or page
        page-link
        (fn page-link [page-name]
          {:href (str "#/page/" (clojure.string/replace page-name "/" "%2F"))
           :on-click
           (fn [e] (if (aget e "shiftKey")
                     (call-api "open_in_right_sidebar"
                               (aget (call-api "get_page" page-name) "uuid"))))})
        block-link
        (fn block-link [attrs uuid]
          (assoc attrs
                 :href (str "#/page/" uuid)
                 :on-click
                 (fn [e] (if (aget e "shiftKey")
                           (call-api "open_in_right_sidebar" uuid)))))]

    (log "[Calendar Module]"
         "\nCalendar properties:"
         (clj->js {:calendar/highlight-overdue highlight-overdue
                   :calendar/show-weekends show-weekends})
         "\nUser configuration:"
         (clj->js {:journal-format journal-format})
         "\nDebug:"
         (clj->js {:today today :today-formatted (format-date today journal-format)}))

    ;; The main view
    [:div.lsm-calendar
     {:class [(when (not show-weekends) "lsm-hide-weekends")]}

     ;; Day of week headings
     (map (fn [wd] [:h2 (weekday wd)]) (range 7))

     ;; Cells for each day
     (let [current (ymd->jdn today)
           start (- current (jdn->wd current) 7)
           visible-days (range start (+ start 28))]
       (map
        (fn [day]
          (let [ymd (jdn->ymd day), past (< ymd today)]
            [:div.lsm-day {:class (if past "lsm-past" (if (> ymd today) "lsm-future" "lsm-today"))}

             ;; Numeric day of month
             [:a.page-ref (page-link (format-date ymd journal-format)) (% ymd 100)]

             ;; This day's entries
             [:div.lsm-entries
              (map
               (fn [{:keys [day time local uuid marker title]}]
                 (let [complete (contains? #{"DONE" "CANCELED" "CANCELLED"} marker)
                       overdue (and highlight-overdue past marker (not complete))]
                   [:a.block-ref
                    (block-link
                     {:class [(when marker "lsm-task") marker
                              (when overdue "lsm-overdue")]} uuid)
                    ;; Time
                    (when time [:span.lsm-time time])
                    ;; Icon
                    (case marker
                      nil         (when-not time [:i.ti.ti-calendar])
                      "DONE"      [:i.ti.ti-checkbox]
                      ("CANCELED"
                       "CANCELLED") [:i.ti.ti-square-off]
                      (if past [:i.ti.ti-layout-collage]
                          (if local
                            [:i.ti.ti-square]
                            [:i.ti.ti-arrow-autofit-right])))
                    ;; Title
                    [:span title]]))
               (get days ymd))]]))
        visible-days))]))
