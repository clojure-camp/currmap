(ns clojurecamp.currmap.emails
  (:require
    [clojure.string :as string]
    [bloom.omni.auth.token :as token]
    [clojurecamp.currmap.config :as config]))

(defn wrap-login
  [{:keys [url user-id]}]
  (str (config/get :website-base-url)
       url
       "?"
       (token/login-query-string
        user-id
        (config/get :auth-token-secret))))

(defn login-link
  [{:keys [user-id email]}]
  {:to email
   :subject "Clojure Camp CurrMap Login"
   :body [:div
          [:p "Click "
           [:a {:href
                (wrap-login
                  {:user-id user-id
                   :url "/"})}
            "here"] " to log in."]]})

