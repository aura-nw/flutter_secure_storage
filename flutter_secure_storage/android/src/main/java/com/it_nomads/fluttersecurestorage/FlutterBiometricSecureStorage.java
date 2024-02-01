package com.it_nomads.fluttersecurestorage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.it_nomads.fluttersecurestorage.ciphers.StorageCipher;
import com.it_nomads.fluttersecurestorage.ciphers.StorageCipherFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public class FlutterBiometricSecureStorage implements  ISecureStorage{
    static class FlutterBiometricInformationDefault{
        public  static int userAuthenticationTimeout = 1;
        public  static String SHARED_PREFERENCES_NAME = "FlutterSecureStorage";
        public  static String KEY_ALIAS = "Flutter_Biometric_Secure_Storage";
        public  static String ELEMENT_PREFERENCES_KEY_PREFIX = "ELEMENT_PREFERENCES_KEY_PREFIX";
    }

    private final String TAG = "BiometricSecureStorage";
    private final Context applicationContext;
    private final Charset charset;
    protected String ELEMENT_PREFERENCES_KEY_PREFIX = FlutterBiometricInformationDefault.ELEMENT_PREFERENCES_KEY_PREFIX;

    protected String KEY_ALIAS = FlutterBiometricInformationDefault.KEY_ALIAS;
    protected Map<String, Object> options;
    private String SHARED_PREFERENCES_NAME = FlutterBiometricInformationDefault.SHARED_PREFERENCES_NAME;
    private SharedPreferences preferences;

    private int userAuthenticationTimeout = FlutterBiometricInformationDefault.userAuthenticationTimeout;

    private Executor executor;

    private FragmentActivity currentActivity;

    private StorageCipher storageCipher;
    private StorageCipherFactory storageCipherFactory;


    public FlutterBiometricSecureStorage(Context context) {
        applicationContext = context.getApplicationContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            charset = StandardCharsets.UTF_8;
        } else {
            //noinspection CharsetObjectCanBeUsed
            charset = Charset.forName("UTF-8");
        }
    }

    public  boolean containsKey(String key) throws GeneralSecurityException, IOException {
        ensureInitialized();

        return preferences.contains(key);
    }

    public  String read(String key) throws GeneralSecurityException, IOException {
        ensureInitialized();

        return preferences.getString(key, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> readAll() throws GeneralSecurityException, IOException {
        ensureInitialized();
        Map<String, String> raw = (Map<String, String>) preferences.getAll();
        Map<String, String> all = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String keyWithPrefix = entry.getKey();
            if (keyWithPrefix.contains(ELEMENT_PREFERENCES_KEY_PREFIX)) {
                String key = entry.getKey().replaceFirst(ELEMENT_PREFERENCES_KEY_PREFIX + '_', "");
                all.put(key, entry.getValue());
            }
        }
        return all;
    }

    public  void write(String key, String value) throws GeneralSecurityException, IOException {
        ensureInitialized();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    public void delete(String key) throws GeneralSecurityException, IOException {
        ensureInitialized();

        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(key);
        editor.apply();
    }

    public  void deleteAll() throws GeneralSecurityException, IOException {
        ensureInitialized();

        final SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
    }

    @SuppressWarnings({"ConstantConditions"})
    public void ensureInitialized() throws GeneralSecurityException, IOException {
        if (options.containsKey("sharedPreferencesName") && !((String) options.get("sharedPreferencesName")).isEmpty()) {
            SHARED_PREFERENCES_NAME = (String) options.get("sharedPreferencesName");
        }

        if (options.containsKey("preferencesKeyPrefix") && !((String) options.get("preferencesKeyPrefix")).isEmpty()) {
            ELEMENT_PREFERENCES_KEY_PREFIX = (String) options.get("preferencesKeyPrefix");
        }

        try {
            JSONObject jsonObj = new JSONObject((String) Objects.requireNonNull(options.get("userAuthenticationRequired")));

            if (jsonObj.has("userAuthenticationTimeout") && !((String) jsonObj.get("userAuthenticationTimeout")).isEmpty()) {
                userAuthenticationTimeout = Integer.parseInt(jsonObj.get("userAuthenticationTimeout").toString());
            }
        } catch (JSONException e) {
            Log.e(TAG, "Decode json object error", e);
            userAuthenticationTimeout = FlutterBiometricInformationDefault.userAuthenticationTimeout;
        }

        SharedPreferences nonEncryptedPreferences = applicationContext.getSharedPreferences(
                SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );

        if (storageCipher == null) {
            try {
                initStorageCipher(nonEncryptedPreferences);

            } catch (Exception e) {
                Log.e(TAG, "StorageCipher initialization failed", e);
            }
        }


        if (getUseEncryptedSharedPreferences() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            preferences = initializeEncryptedSharedPreferencesManager(applicationContext);
            checkAndMigrateToEncrypted(nonEncryptedPreferences, preferences);
        } else {
            preferences = nonEncryptedPreferences;
        }
    }

    @SuppressWarnings({"ConstantConditions"})
    private boolean getUseEncryptedSharedPreferences() {

        return options.containsKey("encryptedSharedPreferences") && options.get("encryptedSharedPreferences").equals("true") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private SharedPreferences initializeEncryptedSharedPreferencesManager(Context context) throws GeneralSecurityException, IOException {

        KeyGenParameterSpec.Builder paramsSpec = new KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setUserAuthenticationRequired(true)
                .setKeySize(256);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            paramsSpec
                    .setUserAuthenticationParameters(userAuthenticationTimeout, KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL)
                    .setUnlockedDeviceRequired(true);
        } else {
            paramsSpec.setUserAuthenticationValidityDurationSeconds(userAuthenticationTimeout);
        }

        MasterKey key = new MasterKey.Builder(context, KEY_ALIAS)
                .setKeyGenParameterSpec(paramsSpec.build()).build();

        return EncryptedSharedPreferences.create(
                context,
                SHARED_PREFERENCES_NAME,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }


    private void initStorageCipher(SharedPreferences source) throws Exception {
        storageCipherFactory = new StorageCipherFactory(source, options);
        storageCipher = storageCipherFactory.getSavedStorageCipher(applicationContext);
    }


    private String decodeRawValue(String value) throws Exception {
        if (value == null) {
            return null;
        }
        byte[] data = Base64.decode(value, 0);
        byte[] result = storageCipher.decrypt(data);

        return new String(result, charset);
    }

    private void checkAndMigrateToEncrypted(SharedPreferences source, SharedPreferences target) {
        try {
            for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();
                Log.e(TAG, "Data migration = === " + key);
                if (v instanceof String && key.contains(ELEMENT_PREFERENCES_KEY_PREFIX)) {
                    Log.e(TAG, "Data migration");
                    final String decodedValue = decodeRawValue((String) v);
                    target.edit().putString(key, (decodedValue)).apply();
                    source.edit().remove(key).apply();
                }
            }
            final SharedPreferences.Editor sourceEditor = source.edit();
            storageCipherFactory.removeCurrentAlgorithms(sourceEditor);
            sourceEditor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Data migration failed", e);
        }
    }


    public void setCurrentActivity(FragmentActivity activity) {
        this.currentActivity = activity;
    }

    @Override
    public String addPrefixToKey(String key)throws GeneralSecurityException, IOException {
        ensureInitialized();
        return ELEMENT_PREFERENCES_KEY_PREFIX + "_" + key;
    }

    @Override
    public void handleException(Exception e,IExceptionObserver observer) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !(e instanceof  UserNotAuthenticatedException) || (e.getCause() != null && !(e.getCause() instanceof  UserNotAuthenticatedException))) {
                observer.onUserUnAuthorizeOrError(e);
                return;
        }

        BiometricPrompt.AuthenticationCallback callback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                StringWriter stringWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stringWriter));

                observer.onUserUnAuthorizeOrError(e);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                observer.onUserAuthorize();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                StringWriter stringWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stringWriter));
            }
        };

        final Runnable changeView = new Runnable() {
            public void run() {
                executor = ContextCompat.getMainExecutor(applicationContext);
                BiometricPrompt biometricPrompt = new BiometricPrompt(currentActivity,
                        executor, callback);

                try {
                    JSONObject jsonObj = new JSONObject((String) Objects.requireNonNull(options.get("userAuthenticationRequired")));

                    BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                            .setTitle((String) jsonObj.get("bioMetricTitle"))
                            .setSubtitle((String) jsonObj.get("bioMetricSubTitle"))
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                            .build();

                    biometricPrompt.authenticate(promptInfo);
                } catch (JSONException e) {
                    Log.e(TAG, "Decode json object error", e);
                    callback.onAuthenticationFailed();
                }
            }
        };

        currentActivity.runOnUiThread(changeView);
    }

}
