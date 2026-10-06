package io.github.sfesantos.portcullis.policy.coverage;

// The aspect matches annotations on the executing method, so the one on the interface does not apply here.
public class DocumentService implements Documents {

    @Override
    public void read(Long id) {}
}
