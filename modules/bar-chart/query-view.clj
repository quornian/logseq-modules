(fn [value-counts]
  (let [biggest (reduce max (map :count value-counts))]
    [:div.lsm-bar-chart
     (map
       (fn [{value :value, number :count}]
         (let [percent (* (/ number biggest) 100)
               label (if (set? value) (clojure.string/join ", " value) value)]
           [[:div.lsm-bar-chart-label label]
            [:div.lsm-bar-chart-bar {:style {:width (str percent "%")}}
             [:span number]]]))
       value-counts)]))
