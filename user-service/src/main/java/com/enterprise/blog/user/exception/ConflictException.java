package com.enterprise.blog.user.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
