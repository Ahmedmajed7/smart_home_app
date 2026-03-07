import 'package:flutter/services.dart';

class TuyaPlatform {
  static const MethodChannel _channel = MethodChannel('tuya_bridge');

  // -------------------------
  // Core
  // -------------------------
  static Future<bool> initSdk() async {
    final ok = await _channel.invokeMethod<bool>('initSdk');
    return ok ?? false;
  }

  static Future<bool> isLoggedIn() async {
    final ok = await _channel.invokeMethod<bool>('isLoggedIn');
    return ok ?? false;
  }

  // -------------------------
  // Auth
  // -------------------------
  static Future<bool> loginByEmail({
    required String countryCode,
    required String email,
    required String password,
  }) async {
    final ok = await _channel.invokeMethod<bool>('loginByEmail', {
      'countryCode': countryCode,
      'email': email,
      'password': password,
    });
    return ok ?? false;
  }

  static Future<bool> sendEmailCode({
    required String countryCode,
    required String email,
    int type = 1,
  }) async {
    final ok = await _channel.invokeMethod<bool>('sendEmailCode', {
      'countryCode': countryCode,
      'email': email,
      'type': type,
    });
    return ok ?? false;
  }

  static Future<bool> registerEmail({
    required String countryCode,
    required String email,
    required String password,
    required String code,
  }) async {
    final ok = await _channel.invokeMethod<bool>('registerEmail', {
      'countryCode': countryCode,
      'email': email,
      'password': password,
      'code': code,
    });
    return ok ?? false;
  }

  static Future<bool> logout() async {
    final ok = await _channel.invokeMethod<bool>('logout');
    return ok ?? false;
  }

  // -------------------------
  // Home
  // -------------------------
  static Future<List<Map<dynamic, dynamic>>> getHomeList() async {
    final res = await _channel.invokeMethod<dynamic>('getHomeList');
    if (res is List) {
      return res.map((e) => Map<dynamic, dynamic>.from(e as Map)).toList();
    }
    return <Map<dynamic, dynamic>>[];
  }

  static Future<Map<dynamic, dynamic>> ensureHome({
    required String name,
    required String geoName,
    required List<String> rooms,
  }) async {
    final res = await _channel.invokeMethod<dynamic>('ensureHome', {
      'name': name,
      'geoName': geoName,
      'rooms': rooms,
    });

    if (res is Map) return Map<dynamic, dynamic>.from(res);

    throw  PlatformException(
      code: 'ENSURE_HOME_FAILED',
      message: 'ensureHome returned invalid result',
    );
  }

  static Future<List<Map<dynamic, dynamic>>> getHomeDevices({
    required int homeId,
  }) async {
    final res = await _channel.invokeMethod<dynamic>('getHomeDevices', {
      'homeId': homeId,
    });

    if (res is List) {
      return res.map((e) => Map<dynamic, dynamic>.from(e as Map)).toList();
    }
    return <Map<dynamic, dynamic>>[];
  }

  // -------------------------
  // BizBundle Context + UI
  // -------------------------
  static Future<void> ensureBizContext({required int homeId}) async {
    await _channel.invokeMethod('ensureBizContext', {
      'homeId': homeId,
    });
  }

  static Future<void> bizOpenAddDevice({required int homeId}) async {
    await _channel.invokeMethod('bizOpenAddDevice', {
      'homeId': homeId,
    });
  }

  static Future<void> bizOpenQrScan({required int homeId}) async {
    await _channel.invokeMethod('bizOpenQrScan', {
      'homeId': homeId,
    });
  }

  // -------------------------
  // Gateway sub-device pairing
  // -------------------------
  static Future<Map<dynamic, dynamic>> startGatewaySubDevicePairing({
    required String gatewayDevId,
    int timeoutSeconds = 120,
  }) async {
    final res = await _channel.invokeMethod<dynamic>(
      'startGatewaySubDevicePairing',
      {
        'gatewayDevId': gatewayDevId,
        'timeoutSeconds': timeoutSeconds,
      },
    );

    if (res is Map) return Map<dynamic, dynamic>.from(res);

    throw  PlatformException(
      code: 'SUB_DEVICE_PAIR_FAILED',
      message: 'Native pairing returned invalid result',
    );
  }

  static Future<void> stopGatewaySubDevicePairing() async {
    await _channel.invokeMethod('stopGatewaySubDevicePairing');
  }
}