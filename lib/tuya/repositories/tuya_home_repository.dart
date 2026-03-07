import '../models/things_device.dart';
import '../tuya_platform.dart';

class TuyaHome {
  final int homeId;
  final String name;
  final String geoName;

  const TuyaHome({
    required this.homeId,
    required this.name,
    required this.geoName,
  });

  factory TuyaHome.fromMap(Map<dynamic, dynamic> map) {
    return TuyaHome(
      homeId: (map['homeId'] as num).toInt(),
      name: (map['name'] ?? '').toString(),
      geoName: (map['geoName'] ?? '').toString(),
    );
  }
}

class TuyaHomeRepository {
  Future<List<TuyaHome>> getHomes() async {
    final list = await TuyaPlatform.getHomeList();
    return list.map((e) => TuyaHome.fromMap(e)).toList();
  }

  Future<TuyaHome> ensureHome({
    required String name,
    required String geoName,
    required List<String> rooms,
  }) async {
    final map = await TuyaPlatform.ensureHome(
      name: name,
      geoName: geoName,
      rooms: rooms,
    );
    return TuyaHome.fromMap(map);
  }

  Future<List<ThingDevice>> getHomeDevices({
    required int homeId,
  }) async {
    final list = await TuyaPlatform.getHomeDevices(homeId: homeId);

    final devices = list
        .map((e) => ThingDevice.fromMap(Map<String, dynamic>.from(e)))
        .where((d) => d.devId.trim().isNotEmpty)
        .toList();

    devices.sort((a, b) {
      if (a.isGateway != b.isGateway) {
        return a.isGateway ? -1 : 1;
      }
      if (a.isOnline != b.isOnline) {
        return a.isOnline ? -1 : 1;
      }
      return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    });

    return devices;
  }

  Future<Map<dynamic, dynamic>> startGatewaySubDevicePairing({
    required String gatewayDevId,
    int timeoutSeconds = 120,
  }) {
    return TuyaPlatform.startGatewaySubDevicePairing(
      gatewayDevId: gatewayDevId,
      timeoutSeconds: timeoutSeconds,
    );
  }

  Future<void> stopGatewaySubDevicePairing() {
    return TuyaPlatform.stopGatewaySubDevicePairing();
  }
}