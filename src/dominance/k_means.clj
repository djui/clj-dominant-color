(ns dominance.k-means)
;; Courtesy http://www.learningclojure.com/2011/01/k-means-algorithm-for-clustering-data.html


;;; Utilities

(def & partial)

(defn && [fn] (partial apply fn))

(defn hash-map' [keys vals]
  (apply hash-map (interleave keys vals)))

(defn- sqr [n]
  #_(Math/pow n 2)
  (* n n))

;; Scalar

(defn- distance [a b]
  (if (< a b) (- b a) (- a b)))

(defn- average [& l]
  (/ (reduce + l) (count l)))

(defn- stddev [& l]
  (let [avg (apply average l)]
    (Math/sqrt (/ (reduce + (map #(sqr (distance avg %)) l)) (count l)))))

;; Vector

(defn- vec-distance [a b]
  (reduce + (map sqr (map - a b))))

(defn- vec-average [& l]
  (map #(/ % (count l)) (apply map + l)))

(defn- vec-stddev [& l]
  (let [avg (apply vec-average l)]
    (Math/sqrt (/ (reduce + (map #(sqr (vec-distance avg %)) l)) (count l)))))


;;; Internals

(defn- closest [p means distance-fn]
  (first (sort-by #(distance-fn % p) means)))

(defn- point-groups [means data distance-fn]
  (group-by #(closest % means distance-fn) data)) ;; FIXME: Slow! (0.5s)

(defn- update-seq [sq f]
  (let [freqs (frequencies sq)]
    (apply concat
           (for [[k v] freqs]
             (if (= v 1) (list (f k))
                 (cons (f k) (repeat (dec v) k)))))))

(defn- new-means [average-fn point-groups old-means]
  (update-seq old-means (fn [o]
                          (if (contains? point-groups o)
                            (apply average-fn (get point-groups o)) o))))

(defn- iterate-means [data distance-fn average-fn]
  (fn [means]
    (new-means average-fn (point-groups means data distance-fn) means)))

(defn- groups [data distance-fn means]
  (vals (point-groups means data distance-fn)))

(defn- take-while-unstable
  ([sq]
     (lazy-seq (cons (first sq)
                     (take-while-unstable (rest sq) (first sq)))))
  ([sq last]
     (lazy-seq (when-not (= (first sq) last)
                 (take-while-unstable sq)))))

(defn- k-groups [data distance-fn average-fn]
  (fn [guesses]
    (take-while-unstable ;; FIXME: Too many iterations! (31 * 0.5s)
     (map #(groups data distance-fn %)
          (iterate (iterate-means data distance-fn average-fn) guesses)))))

(defn- random-guesses [n data]
  (take n (repeatedly #(rand-nth data))))


;;; Interface

(defn clusters
  "Calculate clusters given a dataset and guesses, either as scalars or
  vectors."
  [guesses data & [{:keys [iterations] :or {iterations 100} :as opts}]]
  (let [point (first data)
        guesses' (cond
                  (list?    guesses) guesses
                  (integer? guesses) (random-guesses guesses data))
        k-groups-fn (cond
                     (vector? point) (k-groups data vec-distance vec-average)
                     (number? point) (k-groups data distance average))]
    (last (take iterations (k-groups-fn guesses')))))

(defn centroids
  "Calculate centroids given a dataset and guesses, either as scalars or
  vectors, and return a vector of maps with mean, standard deviation, count, and
  weight."
  [guesses data & [{:keys [weight-fn] :as opts}]]
  (let [d (first data)
        res-fn (cond
                (vector? d) (juxt (&& vec-average) (&& vec-stddev) count)
                (number? d) (juxt (&& average) (&& stddev) count))]
    (->> (clusters guesses data opts)
         (map #(hash-map' [:mean :stddev :count] (res-fn %)))
         (map #(assoc-in % [:weight] (weight-fn %)))
         (sort-by :weight)
         reverse)))
