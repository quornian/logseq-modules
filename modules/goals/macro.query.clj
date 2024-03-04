{:title "Goals"
 :query
 [:find (pull ?block [:block/uuid :block/content]) ?goal ?add ?target ?marker
  :keys block goal add target marker
  :in $
  :where
  [?block :block/properties ?props]
  [(get-else $ ?block :block/marker "DONE") ?marker]
  [(get ?props :goal) ?goal]
  [(get ?props :goal-add -1) ?add]
  [(get ?props :goal-target -1) ?target]]
 :result-transform :goals
 :view :goals}
