(ns html
  (:require [clojure.string         :refer [trim]]
            [net.cgrand.enlive-html :as css])
  (:refer-clojure :exclude [meta]))


;;; Interface

(defn dom [^java.net.URL html]
  (css/html-resource html))

(defn select-all
  ([selector dom] (select-all selector identity dom))
  ([selector transformer dom] (select-all selector transformer identity dom))
  ([selector transformer filter dom]
    (->> (css/select dom selector) (map transformer) filter)))

(defn select
  ([selector dom] (select selector identity dom))
  ([selector transformer dom]
    (-> (css/select dom selector) first transformer)))

(defn attr [name]
  (fn [node] (get-in node [:attrs name])))

(def attr= css/attr=)

(defn meta
  ([key dom] (meta :name key dom))
  ([key value dom] (meta key value identity dom))
  ([key value transformer dom]
    (let [transformer' #(->> % (attr :content) transformer)]
      (select [:head [:meta (css/attr= key value)]] transformer' dom))))

(defn inner-text
  ([node] (inner-text 0 node))
  ([n node] (some-> (:content node) (nth n) trim)))
