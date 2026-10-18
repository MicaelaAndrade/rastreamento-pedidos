package dev.micaelaandrade.pedidos.shared;

import dev.micaelaandrade.pedidos.auth.InvalidSessionException;
import dev.micaelaandrade.pedidos.order.OrderChangedException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.exc.MismatchedInputException;
import dev.micaelaandrade.pedidos.auth.EmailAlreadyRegisteredException;
import dev.micaelaandrade.pedidos.order.InvalidStatusTransitionException;
import dev.micaelaandrade.pedidos.order.OrderNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduz exceções em respostas HTTP no formato Problem Details (RFC 9457):
 * {@code {"title": ..., "status": ..., "detail": ...}}, mais a lista {@code errors} nos erros de validação.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleNotFound(OrderNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** 409: o pedido existe, mas o estado atual dele não permite a mudança pedida. */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidStatusTransitionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** 409: a pessoa tentou mudar o pedido com base numa versão que já não é a atual. */
    @ExceptionHandler(OrderChangedException.class)
    ProblemDetail handleOrderChanged(OrderChangedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidSessionException.class)
    ProblemDetail handleInvalidSession(InvalidSessionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail handleEmailTaken(EmailAlreadyRegisteredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "O pedido foi alterado por outra pessoa ao mesmo tempo. Atualize a lista e tente de novo.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Valor inválido para o parâmetro \"%s\".".formatted(e.getName()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Alguns campos estão inválidos. Corrija e tente de novo.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, unreadableMessage(e));
        return ResponseEntity.badRequest().body(problem);
    }

    /** Diz qual campo veio com o tipo errado, quando dá para saber. */
    private static String unreadableMessage(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof MismatchedInputException mismatch) {
            String field = fieldPath(mismatch);
            Class<?> type = mismatch.getTargetType();
            if (!field.isEmpty() && type != null) {
                return "Valor inválido em \"%s\": %s.".formatted(field, expected(type));
            }
        }
        // Número bem escrito, mas grande demais para o campo (ex.: quantidade 2147483648).
        if (cause instanceof InputCoercionException coercion) {
            String field = fieldPath(coercion);
            return field.isEmpty()
                    ? "Número fora do limite permitido."
                    : "Valor inválido em \"%s\": número fora do limite permitido.".formatted(field);
        }
        return "Corpo da requisição inválido. Verifique se o JSON está bem formado.";
    }

    /** Monta "items[0].quantity" a partir do caminho que o Jackson percorreu. */
    private static String fieldPath(JacksonException e) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference ref : e.getPath()) {
            if (ref.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String expected(Class<?> type) {
        if (type.isEnum()) {
            return "use um destes valores: " + Arrays.stream(type.getEnumConstants())
                    .map(Object::toString).collect(Collectors.joining(", "));
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return "informe um número inteiro";
        }
        if (Number.class.isAssignableFrom(type)) {
            return "informe um número, como 25.90";
        }
        if (type == String.class) {
            return "informe um texto";
        }
        return "formato não reconhecido";
    }

    /**
     * Qualquer erro não previsto: responde 500 no mesmo formato dos outros erros, sem expor detalhes
     * internos, e registra no log com a causa completa para investigação.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Erro inesperado ao processar a requisição", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro inesperado no servidor. Tente de novo em instantes.");
    }
}
