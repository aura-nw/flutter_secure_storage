package com.it_nomads.fluttersecurestorage;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Objects;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class FlutterSecureStoragePlugin implements MethodCallHandler, FlutterPlugin, ActivityAware {

    private static final String TAG = "FlutterSecureStoragePl";
    private MethodChannel channel;
    private FlutterSecureStorage secureStorage;
    private HandlerThread workerThread;
    private Handler workerThreadHandler;

    private FlutterBiometricSecureStorage flutterBiometricSecureStorage;
    private boolean isUseBiometric = false;

    public void initInstance(BinaryMessenger messenger, Context context) {
        try {
            secureStorage = new FlutterSecureStorage(context);
            flutterBiometricSecureStorage = new FlutterBiometricSecureStorage(context);

            workerThread = new HandlerThread("com.it_nomads.fluttersecurestorage.worker");
            workerThread.start();
            workerThreadHandler = new Handler(workerThread.getLooper());

            channel = new MethodChannel(messenger, "plugins.it_nomads.com/flutter_secure_storage");
            channel.setMethodCallHandler(this);
        } catch (Exception e) {
            Log.e(TAG, "Registration failed", e);
        }
    }

    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        initInstance(binding.getBinaryMessenger(), binding.getApplicationContext());
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (channel != null) {
            workerThread.quitSafely();
            workerThread = null;

            channel.setMethodCallHandler(null);
            channel = null;
        }
        secureStorage = null;
        flutterBiometricSecureStorage = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result rawResult) {
        MethodResultWrapper result = new MethodResultWrapper(rawResult);
        // Run all method calls inside the worker thread instead of the platform thread.
        workerThreadHandler.post(new MethodRunner(call, result));
    }

    @SuppressWarnings("unchecked")
    private String getKeyFromCall(MethodCall call) {
        Map<String, Object> arguments = (Map<String, Object>) call.arguments;
        return addPrefixToKey((String) arguments.get("key"));
    }

    @SuppressWarnings("unchecked")
    private String getValueFromCall(MethodCall call) {
        Map<String, Object> arguments = (Map<String, Object>) call.arguments;
        return (String) arguments.get("value");
    }

    private String addPrefixToKey(String key) {
        if (isUseBiometric) {
            return flutterBiometricSecureStorage.ELEMENT_PREFERENCES_KEY_PREFIX + "_" + key;
        }
        return secureStorage.ELEMENT_PREFERENCES_KEY_PREFIX + "_" + key;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        Activity activity = binding.getActivity();
        if (activity instanceof FragmentActivity) {
            flutterBiometricSecureStorage.setCurrentActivity((FragmentActivity) binding.getActivity());
        }

    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        flutterBiometricSecureStorage.setCurrentActivity(null);
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        Activity activity = binding.getActivity();
        if (activity instanceof FragmentActivity) {
            flutterBiometricSecureStorage.setCurrentActivity((FragmentActivity) binding.getActivity());
        }
    }

    @Override
    public void onDetachedFromActivity() {
        flutterBiometricSecureStorage.setCurrentActivity(null);
    }

    /**
     * MethodChannel.Result wrapper that responds on the platform thread.
     */
    static class MethodResultWrapper implements Result {

        private final Result methodResult;
        private final Handler handler = new Handler(Looper.getMainLooper());

        MethodResultWrapper(Result methodResult) {
            this.methodResult = methodResult;
        }

        @Override
        public void success(final Object result) {
            handler.post(() -> methodResult.success(result));
        }

        @Override
        public void error(@NonNull final String errorCode, final String errorMessage, final Object errorDetails) {
            handler.post(() -> methodResult.error(errorCode, errorMessage, errorDetails));
        }

        @Override
        public void notImplemented() {
            handler.post(methodResult::notImplemented);
        }
    }

    /**
     * Wraps the functionality of onMethodCall() in a Runnable for execution in the worker thread.
     */
    class MethodRunner implements Runnable {
        private final MethodCall call;
        private final Result result;

        MethodRunner(MethodCall call, Result result) {
            this.call = call;
            this.result = result;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            try {
                boolean userAuthenticationRequired = false;
                Map<String, Object> options = (Map<String, Object>) ((Map<String, Object>) call.arguments).get("options");
                if (options.containsKey("userAuthenticationRequired") && !Objects.equals((String) options.get("userAuthenticationRequired"), "null")) {
                    userAuthenticationRequired = true;
                }

                if (userAuthenticationRequired) {
                    isUseBiometric = true;
                    biometricSecureStorageRun();
                } else {
                    isUseBiometric = false;
                    secureStorageRun();
                }
            } catch (Exception e) {
                StringWriter stringWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stringWriter));
                result.error("Exception encountered", call.method, stringWriter.toString());
            }
        }


        void secureStorageRun() {
            boolean resetOnError = false;
            try {
                secureStorage.options = (Map<String, Object>) ((Map<String, Object>) call.arguments).get("options");
                resetOnError = secureStorage.getResetOnError();
                switch (call.method) {
                    case "write": {
                        String key = getKeyFromCall(call);
                        String value = getValueFromCall(call);

                        if (value != null) {
                            secureStorage.write(key, value);
                            result.success(null);
                        } else {
                            result.error("null", null, null);
                        }
                        break;
                    }
                    case "read": {
                        String key = getKeyFromCall(call);

                        if (secureStorage.containsKey(key)) {
                            String value = secureStorage.read(key);
                            result.success(value);
                        } else {
                            result.success(null);
                        }
                        break;
                    }
                    case "readAll": {
                        result.success(secureStorage.readAll());
                        break;
                    }
                    case "containsKey": {
                        String key = getKeyFromCall(call);

                        boolean containsKey = secureStorage.containsKey(key);
                        result.success(containsKey);
                        break;
                    }
                    case "delete": {
                        String key = getKeyFromCall(call);

                        secureStorage.delete(key);
                        result.success(null);
                        break;
                    }
                    case "deleteAll": {
                        secureStorage.deleteAll();
                        result.success(null);
                        break;
                    }
                    default:
                        result.notImplemented();
                        break;
                }
            } catch (FileNotFoundException e) {
                Log.i("Creating sharedPrefs", e.getLocalizedMessage());
            } catch (Exception e) {
                if (resetOnError) {
                    try {
                        secureStorage.deleteAll();
                        result.success("Data has been reset");
                    } catch (Exception ex) {
                        handleException(ex);
                    }
                } else {
                    handleException(e);
                }
            }
        }

        void biometricSecureStorageRun() throws Exception {
            try {
                flutterBiometricSecureStorage.options = (Map<String, Object>) ((Map<String, Object>) call.arguments).get("options");

                switch (call.method) {
                    case "write": {
                        String key = getKeyFromCall(call);
                        String value = getValueFromCall(call);

                        if (value != null) {
                            flutterBiometricSecureStorage.write(key, value);
                            result.success(null);
                        } else {
                            result.error("null", null, null);
                        }
                        break;
                    }
                    case "read": {
                        String key = getKeyFromCall(call);

                        if (flutterBiometricSecureStorage.containsKey(key)) {
                            String value = flutterBiometricSecureStorage.read(key);
                            result.success(value);
                        } else {
                            result.success(null);
                        }
                        break;
                    }
                    case "readAll": {
                        Map<String, String> value = flutterBiometricSecureStorage.readAll();

                        result.success(value);
                        break;
                    }
                    case "containsKey": {
                        String key = getKeyFromCall(call);

                        boolean containsKey = flutterBiometricSecureStorage.containsKey(key);
                        result.success(containsKey);
                        break;
                    }
                    case "delete": {
                        String key = getKeyFromCall(call);

                        flutterBiometricSecureStorage.delete(key);
                        result.success(null);
                        break;
                    }
                    case "deleteAll": {
                        flutterBiometricSecureStorage.deleteAll();
                        result.success(null);
                        break;
                    }
                    default:
                        result.notImplemented();
                        break;
                }
            } catch (Exception e) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (e instanceof UserNotAuthenticatedException || e.getCause() instanceof UserNotAuthenticatedException) {
                        handleUserNotAuthenticatedException(e);
                        return;
                    }
                }
                StringWriter stringWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stringWriter));
            }

        }


        private void handleUserNotAuthenticatedException(Exception e) {
            flutterBiometricSecureStorage.requestBiometrics(new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    StringWriter stringWriter = new StringWriter();
                    e.printStackTrace(new PrintWriter(stringWriter));
                    result.error("Exception encountered", call.method, errString.toString());
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    run();
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    StringWriter stringWriter = new StringWriter();
                    e.printStackTrace(new PrintWriter(stringWriter));
                }
            });
        }

        private void handleException(Exception e) {
            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));
            result.error("Exception encountered", call.method, stringWriter.toString());
        }
    }
}
