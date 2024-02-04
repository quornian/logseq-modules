;; Creates a button from a block UUID, template name and remove flag
;; On click, appends the specified template after the given block,
;; optionally removing the original block (if the remove flag is set)
(fn [[block-uuid template-name remove-on-click?]]
  [:button
   {:class "lsw-template-button"
    :on-click
    (fn [_]
      (if (call-api "exist_template" template-name)
        (do (call-api "insert_template" block-uuid template-name)
            (if remove-on-click?
              (call-api "remove_block" block-uuid)))
        (call-api "show_msg"
                  (str "Template not found:\n" template-name) :error)))}
   [:b "Insert:"] " " template-name])
