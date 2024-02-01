package com.it_nomads.fluttersecurestorage;

import java.util.Map;

public interface ISecureStorage {
    boolean containsKey(String key) throws Exception;

    String read(String key) throws Exception;

    Map<String, String> readAll() throws Exception;

    public void write(String key, String value) throws Exception;

    public void delete(String key) throws Exception;

    public void deleteAll() throws Exception;

    public void ensureInitialized() throws Exception;

    public String addPrefixToKey(String key) throws Exception;

    public void handleException(Exception e, IExceptionObserver observer);
}
