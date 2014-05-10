# work-queue

Layer an executor over Factual's durable-queue.

## Usage

Add the following dependency to your leiningen project.

    [work-queue "0.0.1"]

Create a queue definition with define-work-queue and start it
with start-work-queue!.

    (def num-workers 2)
    (defn handler1 [task-data] ...)
    (defn handler2 [task-data] ...)

    (def qdef (define-work-queue :durable-queue-channel
                                 "durable-queue-directory"
                                 num-workers
                                 :task-type1 handler1
                                 :task-type2 handler2))

    (def q (start-work-queue! qdef))

Call submit! to submit a specific type of task to the queue.

    ;; submitted data will be processed by handler1 when pulled of the queue
    (submit! q :task-type1 {:data "..."})

    ;; submitted data will be processed by handler2 when pulled of the queue
    (submit! q :task-type2 {:other-data "..."})

If you wish for a task to be retried, up to a fixed number of
times if it errors out (throws an exception), set retries the number
of retries in the task data. 

    ;; task will be retried up to two times if it errors out
    (submit q :task-type1 (set-retries {:data "..."} 2))

Note, this does not use the durable queue retry mechanism, so the retries
will not show up in the durable queue stats.

To stop the executor that is waiting on the queue, call stop-work-queue!

    (stop-work-queue! q)

For more information on durable
## License

Copyright © 2014 Bill Robertson

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

