(ns coins.storage-service
  (:require
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]))

(defn save-coins
  [coins file-path]
  (log/info "Saving" coins)
  (spit file-path coins))

(defn load-previous-prices
  [file-path]
  (if (.exists (io/file file-path))
    (read-string (slurp file-path))
    {:btc 0 :eth 0 :bch 0}))