(ns work-queue.core
  (:import [java.util.concurrent Executors ExecutorService])
  (:require [durable-queue :as dq]))

(defn define-work-queue [id directory num-workers & dispatchers]
  "Create a work-queue. Id is the queue id, num-workers will be the number
   of threads dedicated to processing the queue when started, and dispatchers
   is a series of keyword/functions. Each keyword is a identifies a task
   type, and the associated function that will process the task."
  {:id id
   :directory directory
   :num-workers num-workers
   :dispatchers (into {} (map vec (partition 2 dispatchers)))})

(defn set-retries [task-data retries]
  "Set the number of retries in task-data's meta-data, preserve existing metadata"
  (if (or (nil? retries) (= 0 retries))
    (dissoc task-data :retries)
    (assoc task-data :retries retries)))

(defn retries [task-data]
  "Return the number of retries in task-data's meta-data"
  (:retries task-data))

(defn retries-- [task-data]
  "Decrement task-data retry metadata"
  (let [retry-count (retries task-data)
        updated (if retry-count
                  (set-retries task-data (dec retry-count))
                  task-data)]
    updated))

(defn submit! [work-queue task-type task-data]
  "Submit to a work-queue. task-type must match one of the dispatcher types
   in the work queue definition and task-data is the data that will be passed
   to the corresponding function."
  (let [submit-me {:task-type task-type :task-data task-data}]
    (dq/put! (:task-store work-queue) (:id work-queue) submit-me)))

(defn handle-task [work-queue]
  "Wait for a specified time interval for something to go on the queue.
   Process it if it appears based on configuration in define-queue, 
   otherwise resubmit self in lambda (allows graceful shutdown)."
  (let [^ExecutorService exec (:executor work-queue)]
    (try
      (let [timeout-val {:task-type :none}
            task (dq/take! (:task-store work-queue) (:id work-queue) 500 timeout-val)
            submitted (if (= timeout-val task) task @task)
            task-type (:task-type submitted)
            task-data (:task-data submitted)
            handler ((:dispatchers work-queue) task-type)]
        (if (not= :none task-type)
          (try
            (handler task-data)
            (catch Exception x
              (.printStackTrace x)
              (if (retries task-data)
                (submit! work-queue task-type (retries-- task-data))
                (if-let [error-handler (:error-handler submitted)]
                  (error-handler submitted))))
            (finally 
              (dq/complete! task)))))
      (catch Exception x (.printStackTrace x))
      (finally 
        (let [^Runnable again #(handle-task work-queue)]
          (.submit exec again))))))

(defn start-work-queue! [work-queue-definition]
  "Start an executor that listens on the queue based on the 
   template created by define-work-queue"
  (let [^ExecutorService exec (Executors/newFixedThreadPool 
                               (:num-workers work-queue-definition))
        work-queue (assoc work-queue-definition
                     :executor exec
                     :task-store (dq/queues (:directory work-queue-definition) {}))
        ^Runnable handler #(handle-task work-queue)]
    (dotimes [n (:num-workers work-queue)]
      (.submit exec handler))
    work-queue))

(defn stop-work-queue! [work-queue]
  "Shutdown the executor listening on the work queue."
  (let [^ExecutorService executor (:executor work-queue)]
    (.shutdown executor)))


