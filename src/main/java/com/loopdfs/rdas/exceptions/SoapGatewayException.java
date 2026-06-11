// exception/SoapGatewayException.java
package com.loopdfs.rdas.exceptions;

public class SoapGatewayException extends RuntimeException {
    public SoapGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}