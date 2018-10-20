(ns coins.message-service
  (:require [clojure.tools.logging :as log]
            [clj-http.client :as client]))

(defn message-for
  [coin is-standalone]
  (let [ticker (:ticker coin)
        new @(:new-price coin)
        dif (force (:price-difference coin))
        prefix (if is-standalone
                 ""
                 (str ticker ": "))]
    (cond
      (> dif 0) (format "*%s₱%,d (+₱%,d)*" prefix new dif)
      (< dif 0) (format "`%s₱%,d (-₱%,d)`" prefix new (Math/abs dif))
      :else (format "%s₱%,d" prefix new))))

(defn send-message
  [message sender recipient]

  (let
   [url (format "https://api.telegram.org/bot%s/sendMessage" sender)]
    (log/info "\n\tSending:" message)
    (client/post
     url
     {:async? true
      :query-params
      {:parse_mode "Markdown"
       :text message
       :chat_id recipient}}
     (fn [response] (log/debug "message sent" response url))
     (fn [exception] (log/error "message failed" exception url)))))