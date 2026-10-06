package io.github.sfesantos.portcullis.policy.coverage;

public record OrderView(Long id, String status) {

    public String label() {
        return id + " " + status;
    }
}
