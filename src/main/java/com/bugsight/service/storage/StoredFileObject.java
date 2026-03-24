package com.bugsight.service.storage;

public record StoredFileObject(String contentType, byte[] bytes) {
}
