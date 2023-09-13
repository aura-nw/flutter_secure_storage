package com.it_nomads.fluttersecurestorage;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
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
    private HandlerThread workerThread;
    private Handler workerThreadHandler;

    private FlutterSecureStorageHandler secureStorageHandler;

    public void initInstance(BinaryMessenger messenger, Context context) {
        try {
            secureStorageHandler = new FlutterSecureStorageHandler(context);
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
        secureStorageHandler.dispose();
        secureStorageHandler = null;
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
        return secureStorageHandler.addPrefixToKey(key);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        Activity activity = binding.getActivity();
        if (activity instanceof FragmentActivity) {
            secureStorageHandler.startListenActivityChange((FragmentActivity) binding.getActivity());
        }

    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        secureStorageHandler.startListenActivityChange(null);
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
    class MethodRunner implements Runnable, IExceptionObserver {
        private final MethodCall call;
        private final Result result;

        MethodRunner(MethodCall call, Result result) {
            this.call = call;
            this.result = result;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            boolean resetOnError = false;
            try {
                secureStorageHandler.setOptions((Map<String, Object>) ((Map<String, Object>) call.arguments).get("options"));
                resetOnError = secureStorageHandler.getResetOnError();
                switch (call.method) {
                    case "write": {
                        String key = getKeyFromCall(call);
                        String value = getValueFromCall(call);

                        if (value != null) {
                            secureStorageHandler.write(key, value);
                            result.success(null);
                        } else {
                            result.error("null", null, null);
                        }
                        break;
                    }
                    case "read": {
                        String key = getKeyFromCall(call);

                        if (secureStorageHandler.containsKey(key)) {
                            String value = secureStorageHandler.read(key);
                            result.success(value);
                        } else {
                            result.success(null);
                        }
                        break;
                    }
                    case "readAll": {
                        result.success(secureStorageHandler.readAll());
                        break;
                    }
                    case "containsKey": {
                        String key = getKeyFromCall(call);

                        boolean containsKey = secureStorageHandler.containsKey(key);
                        result.success(containsKey);
                        break;
                    }
                    case "delete": {
                        String key = getKeyFromCall(call);

                        secureStorageHandler.delete(key);
                        result.success(null);
                        break;
                    }
                    case "deleteAll": {
                        secureStorageHandler.deleteAll();
                        result.success(null);
                        break;
                    }
                    default:
                        result.notImplemented();
                        break;
                }
            } catch (FileNotFoundException e) {
                Log.i("Creating sharedPrefs", Objects.requireNonNull(e.getLocalizedMessage()));
            } catch (Exception e) {
                if (resetOnError) {
                    try {
                        secureStorageHandler.deleteAll();
                        result.success("Data has been reset");
                    } catch (Exception ex) {
                        secureStorageHandler.handleException(this, e);
                    }
                } else {
                    secureStorageHandler.handleException(this, e);
                }
            }
        }

        @Override
        public void onUserUnAuthorizeOrError(Exception e) {
            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));
            result.error("Exception encountered", call.method, stringWriter.toString());
        }

        @Override
        public void onUserAuthorize() {
            run();
        }

    }
}
