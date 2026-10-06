package io.github.sfesantos.portcullis;

public class PolicyDefinitionException extends PortcullisException {

    public PolicyDefinitionException(String message) {
        super(message);
    }

    public PolicyDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int status() {
        return 500;
    }
}
