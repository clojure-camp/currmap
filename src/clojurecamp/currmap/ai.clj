(ns clojurecamp.currmap.ai
  (:require
   [clojure.string :as string]
   [clojurecamp.currmap.db :as db]
   [cheshire.core :as json]
   [org.httpkit.client :as http]))

(def oauth-token "REDACTED")

(defn prompt
  [{:keys [url topics]}]
  (str "Scrape the following page: " url "\n"
       "I want to know the title of the page, a brief description, and what topics this page teaches.\n"
       "Return the set of relevant topics, the title and description of the page as JSON, in the following format: "
       "```{\"topics\": [0, 2],
            \"title\": \"Page title\",
            \"description\": \"Brief description of the page\"}```"
       "\n\n"
       "The available topics (and their ids) are as follows:\n"
       (->> topics
            (map (fn [[id label]]
                   (str id ": " label)))
            (string/join "\n"))))

(defn scrape!
  [{:keys [url]}]
  (let [outcomes (->> (db/q
                       '[:find ?topic-name ?outcome-name ?outcome-id
                         :where
                         [?outcome :outcome/id ?outcome-id]
                         [?outcome :outcome/name ?outcome-name]
                         [?outcome :outcome/topic ?topic]
                         [?topic :topic/name ?topic-name]])
                      (sort-by first))
        outcome-index->label (->> outcomes
                                  (map-indexed (fn [index [topic outcome _]]
                                                 [index (str topic " - " outcome)])))
        outcome-index->id (->> outcomes
                               (map-indexed (fn [index [_ _ outcome-id]]
                                              [index outcome-id]))
                               (into {}))]
    (-> @(http/request
          {:method :post
           :url "https://api.openai.com/v1/responses"
           :headers {"Content-Type" "application/json"}
           :body
           (json/generate-string
            {:model "gpt-4.1"
             :tool_choice {:type "web_search_preview"}
             :tools [{:type "web_search_preview"}]
             :input (prompt {:url url
                             :topics outcome-index->label})
             :text {:format {:type "json_schema"
                             :name "page_metadata"
                             :schema {:type "object"
                                      :additionalProperties false
                                      :required ["topics" "title" "description"]
                                      :properties {:topics {:type "array"
                                                            :items {:type "integer"}}
                                                   :title {:type "string"}
                                                   :description {:type "string"}}}}}})
           :oauth-token oauth-token})
        :body
        (json/parse-string keyword)
        :output
        (->> (filterv (fn [x] (= "message" (:type x)))))
        (get-in [0 :content 0 :text])
        (json/parse-string keyword)
        ((fn [{:keys [topics title description]}]
           {:scrape/outcomes (->> topics
                                  (map (fn [x]
                                         {:outcome/id (outcome-index->id x)})))
            :scrape/title title
            :scrape/description description})))))

#_(scrape! {:url "https://caveman.mccue.dev/tutorial/clojure/3_start_an_nrepl_server"})

(defn mock-scrape!
  [{:keys [url]}]
  {:scrape/title (str "Title " url)
   :scrape/description (str "Description " url)
   :scrape/outcomes [#:outcome{:id #uuid "37968997-3651-49c3-8659-0eaa5bb8f138"}
                     #:outcome{:id #uuid "b3fc20b4-d263-46d0-9f6a-fa976e42d7d5"}
                     #:outcome{:id #uuid "11d7a1ef-c5da-40f5-910b-e00a61489f57"}]})

