;; Usage: {{template-button <template-name>,<keep-on-click>}}
;; where
;;   <template-name> is the name of the template to insert when clicked.
;;   <keep-on-click> is the word "keep" if the button is to be kept on click.
;; Examples:
;;     {{template-button A Template}}
;;     {{template-button My Other Template, keep}}
{:inputs [:current-block]
 :query [:find ?block-uuid
         :in $ ?block
         :where [?block :block/uuid ?block-uuid]]
 :result-transform (fn [[block-uuid]]
                     [block-uuid "$1" (= "$2" "keep")])
 :view :template-button}
