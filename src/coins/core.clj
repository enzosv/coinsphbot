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
  [keyword loaded-previous-prices]
  (let [ticker (clojure.string/upper-case (name keyword))
        new-price (cps/fetch-new-price ticker)
        ;; Lookup price from previous using keyword. Default to 0.
        previous-price (or (keyword loaded-previous-prices) 0)
        ;; Async compute diff. Default to previous-price after 1000ms
        price-difference (delay (- (deref new-price 1000 previous-price) previous-price))]
    {:keyword keyword
     :ticker ticker
     :new-price new-price
     :previous-price previous-price
     :price-difference price-difference
     :should-send? (fn [dif-limit] (> (abs (force price-difference)) dif-limit))
     :message (delay (messenger/construct-message
                      new-price
                      price-difference))}))

(defn -main [& args]
  (let [path-to-previous (:file-path config)
        loaded-previous-prices (storer/load-previous-prices path-to-previous)
        bch (coin :bch loaded-previous-prices)
        btc (coin :btc loaded-previous-prices)
        eth (coin :eth loaded-previous-prices)
        dif-limit (:dif-limit config)]
    (log/info "waiting for new prices to load...")
    (when (or
           ((:should-send? btc) dif-limit)
           ((:should-send? eth) dif-limit)
           ((:should-send? bch) dif-limit))
      (storer/save-coins {(:keyword btc) @(:new-price btc)
                          (:keyword eth) @(:new-price eth)
                          (:keyword bch) @(:new-price bch)}
                         path-to-previous)
      (let [sender (:bot-id config)]
        (messenger/send-message
         (force (:message btc))
         sender
         (:btc-channel-id config))
        (messenger/send-message
         (format "%s:%s\n%s:%s\n%s:%s"
                 (:ticker btc)
                 (force (:message btc))
                 (:ticker bch)
                 (force (:message bch))
                 (:ticker eth)
                 (force (:message eth)))
         sender
         (:all-channel-id config)))))
  nil)