import 'package:alrawi_app/tuya/bizbundle/tuya_bizbundle_controller.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../models/things_device.dart';
import '../repositories/tuya_home_repository.dart';

final homeHubControllerProvider =
    AsyncNotifierProvider<HomeHubController, HomeHubState>(
  HomeHubController.new,
);

class HomeHubController extends AsyncNotifier<HomeHubState> {
  static const _unset = Object();

  final TuyaHomeRepository _homeRepo = TuyaHomeRepository();

  @override
  Future<HomeHubState> build() async => HomeHubState.empty;

  Future<void> ensureHomeIfNeeded() async {
    final ensured = await _homeRepo.ensureHome(
      name: 'My Home',
      geoName: 'Oman',
      rooms: const ['Living Room', 'Bedroom'],
    );

    await ref
        .read(tuyaBizBundleControllerProvider.notifier)
        .ensureBizContext(ensured.homeId);

    final prev = state.value ?? HomeHubState.empty;
    state = AsyncData(
      prev.copyWith(
        selectedHomeId: ensured.homeId,
        selectedHomeName: ensured.name,
      ),
    );

    await refreshDevices();
  }

  Future<void> loadHomes({bool autoPickFirst = true}) async {
    final prev = state.value ?? HomeHubState.empty;
    state = AsyncData(prev.copyWith(isRefreshing: true));

    try {
      final homes = await _homeRepo.getHomes();

      if (homes.isEmpty) {
        state = AsyncData(
          HomeHubState.empty.copyWith(isRefreshing: false),
        );
        return;
      }

      int? selectedHomeId = prev.selectedHomeId;
      String selectedHomeName = prev.selectedHomeName;

      final hasPrevious = selectedHomeId != null &&
          homes.any((h) => h.homeId == selectedHomeId);

      if (!hasPrevious || autoPickFirst) {
        final chosen = hasPrevious
            ? homes.firstWhere((h) => h.homeId == selectedHomeId)
            : homes.first;
        selectedHomeId = chosen.homeId;
        selectedHomeName = chosen.name;
      }

      if (selectedHomeId != null) {
        await ref
            .read(tuyaBizBundleControllerProvider.notifier)
            .ensureBizContext(selectedHomeId);
      }

      var next = prev.copyWith(
        homes: homes,
        selectedHomeId: selectedHomeId,
        selectedHomeName: selectedHomeName,
      );

      if (selectedHomeId != null) {
        final devices = await _homeRepo.getHomeDevices(homeId: selectedHomeId);
        final gatewayId =
            _pickGatewayId(devices, preferred: prev.selectedGatewayId);

        next = next.copyWith(
          devices: devices,
          selectedGatewayId: gatewayId,
        );
      } else {
        next = next.copyWith(
          devices: const [],
          selectedGatewayId: null,
        );
      }

      state = AsyncData(next.copyWith(isRefreshing: false));
    } catch (e, st) {
      state = AsyncError(e, st);
    }
  }

  Future<void> selectHome(TuyaHome home) async {
    final prev = state.value ?? HomeHubState.empty;

    state = AsyncData(
      prev.copyWith(
        selectedHomeId: home.homeId,
        selectedHomeName: home.name,
        devices: const [],
        selectedGatewayId: null,
        isRefreshing: true,
      ),
    );

    try {
      await ref
          .read(tuyaBizBundleControllerProvider.notifier)
          .ensureBizContext(home.homeId);

      final devices = await _homeRepo.getHomeDevices(homeId: home.homeId);
      final gatewayId = _pickGatewayId(devices);

      state = AsyncData(
        prev.copyWith(
          homes: prev.homes,
          selectedHomeId: home.homeId,
          selectedHomeName: home.name,
          devices: devices,
          selectedGatewayId: gatewayId,
          isRefreshing: false,
        ),
      );
    } catch (e, st) {
      state = AsyncError(e, st);
    }
  }

  Future<void> refreshDevices() async {
    final current = state.value ?? HomeHubState.empty;
    final homeId = current.selectedHomeId;
    if (homeId == null) return;

    state = AsyncData(current.copyWith(isRefreshing: true));

    try {
      await ref
          .read(tuyaBizBundleControllerProvider.notifier)
          .ensureBizContext(homeId);

      final devices = await _homeRepo.getHomeDevices(homeId: homeId);
      final gatewayId =
          _pickGatewayId(devices, preferred: current.selectedGatewayId);

      state = AsyncData(
        current.copyWith(
          devices: devices,
          selectedGatewayId: gatewayId,
          isRefreshing: false,
        ),
      );
    } catch (e, st) {
      state = AsyncError(e, st);
    }
  }

  void setSelectedGateway(String devId) {
    final current = state.value ?? HomeHubState.empty;
    state = AsyncData(
      current.copyWith(selectedGatewayId: devId),
    );
  }

  Future<void> openBizAddDevice() async {
    final homeId = state.value?.selectedHomeId;
    if (homeId == null) return;

    await ref
        .read(tuyaBizBundleControllerProvider.notifier)
        .openAddDevice(homeId);
  }

  Future<void> openBizQrScan() async {
    final homeId = state.value?.selectedHomeId;
    if (homeId == null) return;

    await ref
        .read(tuyaBizBundleControllerProvider.notifier)
        .openQrScan(homeId);
  }

  Future<ThingDevice?> startGatewaySubDevicePairing({
    required String gatewayDevId,
    int timeoutSeconds = 120,
  }) async {
    final res = await _homeRepo.startGatewaySubDevicePairing(
      gatewayDevId: gatewayDevId,
      timeoutSeconds: timeoutSeconds,
    );

    final map = Map<String, dynamic>.from(res);
    final paired = ThingDevice.fromMap(map);

    await refreshDevices();
    return paired;
  }

  String? _pickGatewayId(
    List<ThingDevice> devices, {
    String? preferred,
  }) {
    if (devices.isEmpty) return null;

    if (preferred != null &&
        devices.any((d) => d.devId == preferred && d.isGateway)) {
      return preferred;
    }

    final explicitGateway = devices.where((d) => d.isGateway).toList();
    if (explicitGateway.isNotEmpty) {
      final onlineGateway = explicitGateway.where((d) => d.isOnline).toList();
      return (onlineGateway.isNotEmpty ? onlineGateway.first : explicitGateway.first)
          .devId;
    }

    return devices.first.devId;
  }
}

class HomeHubState {
  final List<TuyaHome> homes;
  final int? selectedHomeId;
  final String selectedHomeName;
  final List<ThingDevice> devices;
  final String? selectedGatewayId;
  final bool isRefreshing;

  const HomeHubState({
    required this.homes,
    required this.selectedHomeId,
    required this.selectedHomeName,
    required this.devices,
    required this.selectedGatewayId,
    required this.isRefreshing,
  });

  static const empty = HomeHubState(
    homes: [],
    selectedHomeId: null,
    selectedHomeName: '',
    devices: [],
    selectedGatewayId: null,
    isRefreshing: false,
  );

  ThingDevice? get selectedGateway {
    if (selectedGatewayId == null) return null;
    for (final d in devices) {
      if (d.devId == selectedGatewayId) return d;
    }
    return null;
  }

  HomeHubState copyWith({
    List<TuyaHome>? homes,
    Object? selectedHomeId = HomeHubController._unset,
    String? selectedHomeName,
    List<ThingDevice>? devices,
    Object? selectedGatewayId = HomeHubController._unset,
    bool? isRefreshing,
  }) {
    return HomeHubState(
      homes: homes ?? this.homes,
      selectedHomeId: selectedHomeId == HomeHubController._unset
          ? this.selectedHomeId
          : selectedHomeId as int?,
      selectedHomeName: selectedHomeName ?? this.selectedHomeName,
      devices: devices ?? this.devices,
      selectedGatewayId: selectedGatewayId == HomeHubController._unset
          ? this.selectedGatewayId
          : selectedGatewayId as String?,
      isRefreshing: isRefreshing ?? this.isRefreshing,
    );
  }
}