(ns coins.message-service
  (:require [clojure.tools.logging :as log]
            [clj-http.client :as client]))

(defn construct-message
  [new-price price-difference]
  (let [dif (force price-difference)]
    (cond
      (> dif 0) (format "*₱%,d (+₱%,d)*" @new-price dif)
      (< dif 0) (format "`₱%,d (-₱%,d)`" @new-price (Math/abs dif))
      :else (format "₱%,d" new-price))))

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