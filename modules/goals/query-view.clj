(fn [results]
  [:table
   [:tr [:th "Goal"] [:th "Progress"]]
   (map
    (fn [{dfn :definition label :label value :value target :target}]
      (let [percent (str (quot (* 100 value) target) "%")
            label (first (clojure.string/split-lines (:block/content dfn)))
            goto-def (fn [_] (call-api "push_state" :page {:name (:block/uuid dfn)}))]
        [:tr
         [:td [:a {:on-click goto-def} label]]
         [:td 
          [:div {:class "lsm-progress-bar"}
           [:div {:class "lsm-bg"}
            [:div {:class "lsm-fg"
                   :style {:width percent}}]]
           [:div {:class "lsm-text"} (str percent " (" value " of " target ")")]]]]))
    (sort-by
     (fn [{dfn :definition}]
       (:block/content dfn))
     results))])
