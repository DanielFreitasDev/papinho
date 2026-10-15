package io.github.labdev2025cmd.papinho.identity;

import org.springframework.http.HttpStatus;

import io.github.labdev2025cmd.papinho.shared.error.ApiException;

/** {@code @usuario} fora das regras de formato. */
public class InvalidUsernameException extends ApiException {

    public InvalidUsernameException(String value) {
        super(HttpStatus.BAD_REQUEST, "username-invalid", "Invalid username format: length=" + value.length());
    }
}
