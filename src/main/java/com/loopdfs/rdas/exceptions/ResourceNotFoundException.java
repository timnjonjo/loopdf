// exception/ResourceNotFoundException.java
package com.loopdfs.rdas.exceptions;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}