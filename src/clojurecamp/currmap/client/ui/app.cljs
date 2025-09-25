(ns clojurecamp.currmap.client.ui.app
  (:require
   [bloom.commons.pages :as pages]
   [clojurecamp.currmap.client.state :as state]
   [clojurecamp.currmap.client.ui.resources :as ui.resources]
   [clojurecamp.currmap.client.ui.common :as ui]
   [clojurecamp.currmap.client.ui.spreadsheet :as ui.spreadsheet]))

(defn auth-view
  []
  (if @state/user
    [ui/text-button
     {:label "Log Out"
      :on-click (fn []
                  (state/log-out!))}]
    [ui/text-button
     {:label "Log In"
      :on-click (fn []
                  (when-let [email (js/prompt "Please enter your email:")]
                    (state/authenticate! email)))}]))

(defn app-view []
  (when @state/ready?
    [:<>
     [:div.nav {:tw "flex space-between gap-2 relative w-full z-100"}
      (doall ;; doall needed b/c pages/active? derefs an atom
       (for [[path label] [[[:spreadsheet] "Spreadsheet"]
                           [[:resource-editor] "Resource Editor"]]]
         ^{:key path}
         [:a {:href (pages/path-for path)
              :tw ["px-2 p-1 text-white bg-blue-500"
                   (when (pages/active? path)
                     "font-bold")]} label]))
      [:div {:tw "grow-1"}]
      [auth-view]]

     [pages/current-page-view]]))

(defonce _
  (pages/initialize!
   [ui.spreadsheet/page
    ui.resources/page
    ui.resources/page-resource]))
