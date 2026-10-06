package io.github.sfesantos.portcullis.idempotency;

public sealed interface Reservation {

    Reservation ACQUIRED = new Acquired();
    Reservation IN_PROGRESS = new InProgress();
    static Reservation completed(Object result) {
        return new Completed(result);
    }
    record Acquired() implements Reservation {
    }
    record InProgress() implements Reservation {
    }
    record Completed(Object result) implements Reservation {
    }
}
