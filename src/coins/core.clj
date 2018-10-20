(ns coins.core
  (:require [coins.coins-ph-service :as cps]
            [coins.storage-service :as storer]
            [coins.message-service :as messenger]
            [clojure.tools.logging :as log]
            [clojure.java.io :refer [resource]])
  (:gen-class))

;; const to make it work with uberjar
(def ^:const config (read-string
                     (slurp
                      (resource "dev_config.edn"))))
(defn- abs [n] (max n (- n)))

(defn coin
  [keyword ticker loaded-previous-prices]
  (let [new-price (cps/fetch-new-price ticker)
        ;; Lookup price from previous using keyword. Default to 0.
        previous-price (or (keyword loaded-previous-prices) 0)
        ;; Async compute diff. Default to previous-price after 1000ms
        price-difference (delay (- (deref new-price 1000 previous-price) previous-price))]
    {:keyword keyword
     :ticker ticker
     :new-price new-price
     :previous-price previous-price
     :price-difference price-difference}))

(defn -main []
  (let [path-to-previous (:file-path config)
        loaded-previous-prices (storer/load-previous-prices path-to-previous)
        bch (coin :bch "BCH" loaded-previous-prices)
        btc (coin :btc "BTC" loaded-previous-prices)
        eth (coin :eth "ETH" loaded-previous-prices)
        dif-limit (:dif-limit config)]
    (log/info "waiting for new prices to load...")
    (when (or
           (> (abs (force (:price-difference btc))) dif-limit)
           (> (abs (force (:price-difference eth))) dif-limit)
           (> (abs (force (:price-difference bch))) dif-limit))
      (storer/save-coins {(:keyword btc) @(:new-price btc)
                          (:keyword eth) @(:new-price eth)
                          (:keyword bch) @(:new-price bch)}
                         path-to-previous)
      (let [sender (:bot-id config)]
        (messenger/send-message
         (messenger/message-for btc true)
         sender
         (:btc-channel-id config))
        (messenger/send-message
         (format "%s\n%s\n%s"
                 (messenger/message-for
                  btc
                  false)
                 (messenger/message-for
                  bch
                  false)
                 (messenger/message-for
                  eth
                  false))
         sender
         (:all-channel-id config)))))
  nil)