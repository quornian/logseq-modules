;; Usage: {{template-button <template-name>,<remove-on-click>}}
;; where
;;   <template-name> is the name of the template to insert when clicked.
;;   <remove-on-click> is the word "remove" if the button is to be removed on click.
;; Examples:
;;     {{template-button A Template}}
;;     {{template-button My Other Template, remove}}
{:inputs [:current-block]
 :query [:find ?block-uuid
         :in $ ?block
         :where [?block :block/uuid ?block-uuid]]
 :result-transform (fn [[block-uuid]]
                     [block-uuid "$1" (= "$2" "remove")])
 :view :template-button}
