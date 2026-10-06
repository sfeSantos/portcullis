package io.github.sfesantos.portcullis;

public class UnauthenticatedException extends PortcullisException {

    public UnauthenticatedException(String message) {
        super(message, true);
    }

    @Override
    public int status() {
        return 401;
    }
}
