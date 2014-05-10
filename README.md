# work-queue

Layer an executor over Factual's durable-queue.

## Usage

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

    (start-work-queue! qdef)

## License

Copyright © 2014 Bill Robertson

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
