package com.bodego.platform.catalog.domain.exceptions;

public class CategoryHasProductsException extends RuntimeException {

    public CategoryHasProductsException(String message) {
        super(message);
    }
}

