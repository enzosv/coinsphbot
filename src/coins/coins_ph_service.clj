(ns coins.coins-ph-service
  (:require [clojure.tools.logging :as log]
            [clj-http.client :as client]))

(defn fetch-new-price
  [ticker]
  (let [new-price-promise (promise)
        url (format "https://quote.coins.ph/v1/markets/%s-PHP" ticker)]
    (log/info "Fetching:" url)
    (client/get
     url
     {:async? true
      :as :json
      :conn-timeout 1000}
     (fn [response]
       (log/info "Fetched:" url)
       (deliver new-price-promise (int (read-string (:ask (:market (:body response)))))))
     (fn [exception]
       (log/error "Fetch fail:" url exception)
       (deliver new-price-promise exception)))
    new-price-promise))