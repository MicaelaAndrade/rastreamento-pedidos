package dev.micaelaandrade.pedidos.auth;

/** Token autêntico, mas que não corresponde a um usuário existente. Vira 401. */
public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("Sua sessão não é mais válida. Faça login novamente.");
    }
}
