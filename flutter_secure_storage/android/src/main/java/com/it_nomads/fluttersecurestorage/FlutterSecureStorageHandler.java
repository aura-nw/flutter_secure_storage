package com.it_nomads.fluttersecurestorage;

import android.content.Context;
import android.os.Build;
import android.security.keystore.UserNotAuthenticatedException;

import androidx.fragment.app.FragmentActivity;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public class FlutterSecureStorageHandler {
    private ISecureStorage secureStorageInstance;
    private Map<String, Object> options;

    private ISecureStorage nonBioMetricSecureStorage;
    private ISecureStorage bioMetricSecureStorage;

    FlutterSecureStorageHandler(Context context) {
        //Set default secure storage
        secureStorageInstance = new FlutterSecureStorage(context);

        bioMetricSecureStorage = new FlutterBiometricSecureStorage(context);

        nonBioMetricSecureStorage = new FlutterSecureStorage(context);
//
//        this.observer = observer;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    boolean containsKey(String key) throws Exception {
        ensureInitialized();
        return secureStorageInstance.containsKey(key);
    }

    String read(String key) throws Exception {
        ensureInitialized();
        return secureStorageInstance.read(key);
    }

    Map<String, String> readAll() throws Exception {
        ensureInitialized();
        return secureStorageInstance.readAll();
    }

    public void write(String key, String value) throws Exception {
        ensureInitialized();
        secureStorageInstance.write(key, value);
    }

    public void delete(String key) throws Exception {
        ensureInitialized();
        secureStorageInstance.delete(key);
    }

    public void deleteAll() throws Exception {
        ensureInitialized();
        secureStorageInstance.deleteAll();
    }

    public void ensureInitialized() throws Exception {
        if (options.containsKey("userAuthenticationRequired") && !Objects.equals((String) options.get("userAuthenticationRequired"), "null")) {
            secureStorageInstance = bioMetricSecureStorage;
        } else {
            secureStorageInstance = nonBioMetricSecureStorage;
        }
    }

    public String addPrefixToKey(String key) {
        return secureStorageInstance.addPrefixToKey(key);
    }

    public boolean getResetOnError() {
        return options.containsKey("resetOnError") && Objects.equals(options.get("resetOnError"), "true");
    }


    public void startListenActivityChange(FragmentActivity activity) {
        if (secureStorageInstance instanceof FlutterBiometricSecureStorage) {
            ((FlutterBiometricSecureStorage) secureStorageInstance).setCurrentActivity(activity);
        }
    }

    public void handleException(IExceptionObserver observer, Exception e) {
        secureStorageInstance.handleException(e,observer);
    }

    public void dispose(){
        secureStorageInstance = null;
        nonBioMetricSecureStorage = null;
        bioMetricSecureStorage = null;
        options = null;
    }
}
