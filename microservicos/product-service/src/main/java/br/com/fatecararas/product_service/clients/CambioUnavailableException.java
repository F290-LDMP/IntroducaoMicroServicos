package br.com.fatecararas.product_service.clients;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class CambioUnavailableException extends RuntimeException {
    public CambioUnavailableException() {
        super("Serviço de câmbio indisponível ou sem cotação");
    }
}
