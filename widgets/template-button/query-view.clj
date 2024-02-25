;; Creates a button from a block UUID, template name and keep flag
;; On click, appends the specified template after the given block,
;; removing the original block or keeling it if the "keep" flag is set
(fn [[block-uuid template-name keep-on-click?]]
  [:button
   {:class "lsw-template-button"
    :on-click
    (fn [_]
      (if (call-api "exist_template" template-name)
        (do (call-api "insert_template" block-uuid template-name)
            (if (not keep-on-click?)
              (call-api "remove_block" block-uuid)))
        (call-api "show_msg"
                  (str "Template not found:\n" template-name) :error)))}
   [:b "Insert:"] " " template-name])
