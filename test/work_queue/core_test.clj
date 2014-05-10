(ns work-queue.core-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [work-queue.core :refer :all]))

(defn rm-rf*[dir]
  (let [dir (io/file dir)]
    (doseq [^java.io.File entry (reverse (file-seq dir))]
      (when (.exists entry)
        (println "Delete" (.getAbsolutePath entry))
        (.delete entry)))))

(rm-rf* ".queue-test")

(deftest define-work-queue-test
  (is (= {:id :something :num-workers 1 :dispatchers {:ident identity}
          :directory ".queue-test"}
         (define-work-queue :something ".queue-test" 1 :ident identity))))

(deftest retries-test
  (is (= {:retries 3} (set-retries {} 3)))
  (is (= {:retries 2 :other "a"} (set-retries {:other "a"} 2)))

  (is (nil? (retries :a)))
  (is (= {} (set-retries {} 0)))
  (is (nil? (retries (set-retries {} 0))))
  (is (= 1 (retries (set-retries {} 1))))
  (is (= 1 (retries (retries-- (set-retries {} 2)))))
  (is (= {} (-> (set-retries {} 2) retries-- retries--)))
  )

(defmacro with-queue [wq & body]
  `(try ~@body
        (finally (stop-work-queue! ~wq))))

(defn illex [msg] 
  (throw (new IllegalArgumentException msg)))

(deftest make-it-go-test
  (let [n 5
        retry-latch (java.util.concurrent.CountDownLatch. n)
        retry-counter (java.util.concurrent.atomic.AtomicInteger. 0)
        normal-latch (java.util.concurrent.CountDownLatch. 1)
        normal-counter (java.util.concurrent.atomic.AtomicInteger. 0)
        wqd (define-work-queue :foo ".queue-test" 2 
              :test-retry (fn [_] 
                            (.countDown retry-latch) 
                            (.incrementAndGet retry-counter)
                            (illex "This is expected, please ignore"))
              :normal (fn [_] 
                        (.countDown normal-latch)
                        (.incrementAndGet normal-counter)))
        wq (start-work-queue! wqd)]
    (with-queue wq
      (is (not (nil? (:task-store wq))))

      (submit! wq :test-retry (set-retries {:some "thing"} (dec n)))
      (submit! wq :normal {:some "other thing"})

      (.await retry-latch 1000 java.util.concurrent.TimeUnit/MILLISECONDS)
      (.await normal-latch 1000 java.util.concurrent.TimeUnit/MILLISECONDS)

      (is (= 0 (.getCount retry-latch)))
      (is (= n (.intValue retry-counter))) 

      (is (= 0 (.getCount normal-latch)))
      (is (= 1 (.intValue normal-counter))) 
      
      )))
