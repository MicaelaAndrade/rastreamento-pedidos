package dev.micaelaandrade.pedidos.auth;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("Já existe uma conta com este e-mail.");
    }
}
