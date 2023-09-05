part of '../flutter_secure_storage.dart';

/// Specific options for iOS platform.
class IOSOptions extends AppleOptions {
  final IOSUserAuthenticationRequired? _iosUserAuthenticationRequired;

  const IOSOptions({
    String? groupId,
    String? accountName = AppleOptions.defaultAccountName,
    KeychainAccessibility accessibility = KeychainAccessibility.unlocked,
    bool synchronizable = false,
    IOSUserAuthenticationRequired? iosUserAuthenticationRequired,
  })  : _iosUserAuthenticationRequired = iosUserAuthenticationRequired,
        super(
          groupId: groupId,
          accountName: accountName,
          accessibility: accessibility,
          synchronizable: synchronizable,
        );

  static const IOSOptions defaultOptions = IOSOptions();

  IOSOptions copyWith({
    String? groupId,
    String? accountName,
    KeychainAccessibility? accessibility,
    bool? synchronizable,
  }) =>
      IOSOptions(
        groupId: groupId ?? _groupId,
        accountName: accountName ?? _accountName,
        accessibility: accessibility ?? _accessibility,
        synchronizable: synchronizable ?? _synchronizable,
      );

  @override
  Map<String, String> toMap() {
    if (_iosUserAuthenticationRequired != null) {
      return super.toMap()
        ..addAll({
          'useBiometric': jsonEncode(
            _iosUserAuthenticationRequired!.toMap(),
          ),
        });
    }
    return super.toMap();
  }
}

/// [IOSUserAuthenticationRequired] is object define parameter when user want to use biometric verify
class IOSUserAuthenticationRequired {
  /// Sets the duration of time (seconds) for which this key is authorized to
  /// be used after the users is successfully authenticated by unlock their device. This has effect if
  /// the key requires user authentication for its use
  final int? _userAuthenticationTimeout;

  /// Sets the reason of LAContext
  /// be used after the user interactive with keychain.
  /// See more information [https://developer.apple.com/documentation/localauthentication/lacontext]
  final String _localizedReason;

  IOSUserAuthenticationRequired({
    int? userAuthenticationTimeout,
    required String localizedReason,
  })  : _userAuthenticationTimeout = userAuthenticationTimeout,
        _localizedReason = localizedReason;

  Map<String, String> toMap() {
    return {
      'localizedReason': _localizedReason,
      'userAuthenticationTimeout': '$_userAuthenticationTimeout',
    };
  }
}
