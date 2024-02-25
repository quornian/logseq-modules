{:title "$1"
 :query
 [:find ?value (count ?block)
  :keys value count
  :where
  [?block :block/properties ?props]
  [(keyword "$2") ?kw]
  [(get ?props ?kw) ?value]]
 :result-transform (fn [value-counts] (sort-by :value value-counts))
 :view :bar-chart}
