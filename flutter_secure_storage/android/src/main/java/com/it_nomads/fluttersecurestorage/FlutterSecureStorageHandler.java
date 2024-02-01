package com.it_nomads.fluttersecurestorage;

import android.content.Context;
import android.os.Build;
import android.security.keystore.UserNotAuthenticatedException;

import androidx.fragment.app.FragmentActivity;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public class FlutterSecureStorageHandler {
    private Map<String, Object> options;

    private FlutterSecureStorage nonBioMetricSecureStorage;
    private FlutterBiometricSecureStorage bioMetricSecureStorage;

    FlutterSecureStorageHandler(Context context) {
        //Set default secure storage
        bioMetricSecureStorage = new FlutterBiometricSecureStorage(context);

        nonBioMetricSecureStorage = new FlutterSecureStorage(context);
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
        nonBioMetricSecureStorage.options = options;
        bioMetricSecureStorage.options = options;
    }

    boolean containsKey(String key) throws Exception {
        ISecureStorage secureStorageInstance = ensureInitialized();
        return secureStorageInstance.containsKey(key);
    }

    String read(String key) throws Exception {
        ISecureStorage secureStorageInstance = ensureInitialized();
        return secureStorageInstance.read(key);
    }

    Map<String, String> readAll() throws Exception {
        ISecureStorage secureStorageInstance = ensureInitialized();
        return secureStorageInstance.readAll();
    }

    public void write(String key, String value) throws Exception {
        ISecureStorage secureStorageInstance = ensureInitialized();
        secureStorageInstance.write(key, value);
    }

    public void delete(String key) throws Exception {
        ISecureStorage secureStorageInstance = ensureInitialized();
        secureStorageInstance.delete(key);
    }

    public void deleteAll() throws Exception {
        ISecureStorage secureStorageInstance = ensureInitialized();
        secureStorageInstance.deleteAll();
    }

    public ISecureStorage ensureInitialized() {
        if (options.containsKey("userAuthenticationRequired") && !Objects.equals((String) options.get("userAuthenticationRequired"), "null")) {
            return bioMetricSecureStorage;
        } else {
            return  nonBioMetricSecureStorage;
        }
    }

    public String addPrefixToKey(String key)  throws Exception {
        ISecureStorage secureStorageInstance = ensureInitialized();
        return secureStorageInstance.addPrefixToKey(key);
    }

    public boolean getResetOnError() {
        return options.containsKey("resetOnError") && Objects.equals(options.get("resetOnError"), "true");
    }


    public void startListenActivityChange(FragmentActivity activity) {
        bioMetricSecureStorage.setCurrentActivity(activity);
    }

    public void handleException(IExceptionObserver observer, Exception e) {
        ISecureStorage secureStorageInstance = ensureInitialized();
        secureStorageInstance.handleException(e, observer);
    }

    public void dispose() {
        nonBioMetricSecureStorage = null;
        bioMetricSecureStorage = null;
        options = null;
    }
}
