import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../tuya/controllers/home_hub_controller.dart';
import '../tuya/models/things_device.dart';
import '../tuya/tuya_platform.dart';
import 'auth_page.dart';

class HomeHubPage extends ConsumerStatefulWidget {
  const HomeHubPage({super.key});

  @override
  ConsumerState<HomeHubPage> createState() => _HomeHubPageState();
}

class _HomeHubPageState extends ConsumerState<HomeHubPage>
    with WidgetsBindingObserver {
  bool _refreshOnResume = false;

  static const _bg = Color(0xFF06080D);
  static const _orange = Color(0xFFFF7B39);
  static const _green = Color(0xFF2FD0A6);
  static const _cyan = Color(0xFF29D3C2);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    Future.microtask(() async {
      await ref.read(homeHubControllerProvider.notifier).loadHomes(
            autoPickFirst: true,
          );

      final st = ref.read(homeHubControllerProvider).value;
      if (st == null || st.homes.isEmpty) {
        await ref.read(homeHubControllerProvider.notifier).ensureHomeIfNeeded();
        await ref.read(homeHubControllerProvider.notifier).loadHomes(
              autoPickFirst: true,
            );
      }
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _refreshOnResume) {
      _refreshOnResume = false;
      ref.read(homeHubControllerProvider.notifier).refreshDevices();
    }
  }

  Future<void> _logout() async {
    await TuyaPlatform.logout();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const AuthPage()),
    );
  }

  Future<void> _openAddDevice() async {
    _refreshOnResume = true;
    await ref.read(homeHubControllerProvider.notifier).openBizAddDevice();
  }

  Future<void> _openQrScan() async {
    _refreshOnResume = true;
    await ref.read(homeHubControllerProvider.notifier).openBizQrScan();
  }

  Future<void> _openGatewayPairFlow(ThingDevice gateway) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => _GatewaySubDevicePairingPage(gateway: gateway),
      ),
    );

    if (changed == true) {
      await ref.read(homeHubControllerProvider.notifier).refreshDevices();
    }
  }

  Future<void> _openDevicePanel(ThingDevice device) async {
    try {
      _refreshOnResume = true;
      await TuyaPlatform.openDevicePanel(devId: device.devId);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Could not open ${device.name}: $e'),
        ),
      );
    }
  }

  int _gridCountForWidth(double width) {
    if (width >= 1200) return 4;
    if (width >= 840) return 3;
    return 2;
  }

  double _gridAspectRatioForWidth(double width) {
    if (width < 360) return 0.76;
    if (width < 430) return 0.83;
    if (width < 840) return 0.9;
    return 0.98;
  }

  @override
  Widget build(BuildContext context) {
    final hub = ref.watch(homeHubControllerProvider);
    final screenWidth = MediaQuery.of(context).size.width;
    final gridCount = _gridCountForWidth(screenWidth);
    final gridAspect = _gridAspectRatioForWidth(screenWidth);

    return Scaffold(
      backgroundColor: _bg,
      body: hub.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => _ErrorView(
          message: e.toString(),
          onRetry: () {
            ref.read(homeHubControllerProvider.notifier).loadHomes(
                  autoPickFirst: true,
                );
          },
        ),
        data: (data) {
          final devices = data.devices;
          final gateway = data.selectedGateway;
          final busy = data.isRefreshing;
          final homeTitle = data.selectedHomeName.trim().isEmpty
              ? 'My home'
              : data.selectedHomeName;

          return RefreshIndicator(
            onRefresh: () async {
              await ref.read(homeHubControllerProvider.notifier).loadHomes(
                    autoPickFirst: false,
                  );
            },
            child: CustomScrollView(
              physics: const BouncingScrollPhysics(
                parent: AlwaysScrollableScrollPhysics(),
              ),
              slivers: [
                SliverToBoxAdapter(
                  child: _HeroHeader(
                    title: homeTitle,
                    homes: data.homes,
                    selectedHomeId: data.selectedHomeId,
                    onSelectHome: (home) async {
                      await ref
                          .read(homeHubControllerProvider.notifier)
                          .selectHome(home);
                    },
                    onAdd: _openAddDevice,
                    onLogout: _logout,
                  ),
                ),
                SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(
                      screenWidth < 380 ? 12 : 16,
                      10,
                      screenWidth < 380 ? 12 : 16,
                      0,
                    ),
                    child: _PromoBanner(
                      onTap: gateway == null
                          ? _openAddDevice
                          : () => _openGatewayPairFlow(gateway),
                    ),
                  ),
                ),
                SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(
                      screenWidth < 380 ? 12 : 16,
                      14,
                      screenWidth < 380 ? 12 : 16,
                      0,
                    ),
                    child: _AssistantCard(
                      onTap: gateway == null
                          ? _openAddDevice
                          : () => _openGatewayPairFlow(gateway),
                    ),
                  ),
                ),
                SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(
                      screenWidth < 380 ? 12 : 16,
                      14,
                      screenWidth < 380 ? 12 : 16,
                      0,
                    ),
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        final isTight = constraints.maxWidth < 430;
                        if (isTight) {
                          return const Column(
                            children: [
                              _InfoStatCard(
                                title: 'Home status',
                                value: 'Ready',
                                subtitle:
                                    'Gateway, devices, and pairing tools in one place',
                                icon: Icons.wb_sunny_rounded,
                                accent: _orange,
                              ),
                              SizedBox(height: 12),
                              _InfoStatCard(
                                title: 'Energy',
                                value: 'No data',
                                subtitle:
                                    'You can wire this later to Tuya energy data',
                                icon: Icons.bolt_rounded,
                                accent: _green,
                              ),
                            ],
                          );
                        }

                        return const Row(
                          children: [
                            Expanded(
                              child: _InfoStatCard(
                                title: 'Home status',
                                value: 'Ready',
                                subtitle:
                                    'Gateway, devices, and pairing tools in one place',
                                icon: Icons.wb_sunny_rounded,
                                accent: _orange,
                              ),
                            ),
                            SizedBox(width: 12),
                            Expanded(
                              child: _InfoStatCard(
                                title: 'Energy',
                                value: 'No data',
                                subtitle:
                                    'You can wire this later to Tuya energy data',
                                icon: Icons.bolt_rounded,
                                accent: _green,
                              ),
                            ),
                          ],
                        );
                      },
                    ),
                  ),
                ),
                SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(
                      screenWidth < 380 ? 12 : 16,
                      18,
                      screenWidth < 380 ? 12 : 16,
                      8,
                    ),
                    child: _SectionTitle(
                      title: 'Current gateway',
                      actionLabel: gateway == null ? null : 'Add sub-device',
                      onAction: gateway == null
                          ? null
                          : () => _openGatewayPairFlow(gateway),
                    ),
                  ),
                ),
                SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(
                      screenWidth < 380 ? 12 : 16,
                      0,
                      screenWidth < 380 ? 12 : 16,
                      0,
                    ),
                    child: gateway == null
                        ? _EmptyGatewayCard(
                            onAddGateway: _openAddDevice,
                          )
                        : _CurrentGatewayCard(
                            gateway: gateway,
                            onAddSubDevice: () => _openGatewayPairFlow(gateway),
                            onOpenTuyaUi: _openAddDevice,
                          ),
                  ),
                ),
                SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(
                      screenWidth < 380 ? 12 : 16,
                      20,
                      screenWidth < 380 ? 12 : 16,
                      8,
                    ),
                    child: _SectionTitle(
                      title: 'Devices',
                      actionLabel: 'Refresh',
                      onAction: () {
                        ref.read(homeHubControllerProvider.notifier).refreshDevices();
                      },
                    ),
                  ),
                ),
                if (devices.isEmpty)
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: EdgeInsets.fromLTRB(
                        screenWidth < 380 ? 12 : 16,
                        0,
                        screenWidth < 380 ? 12 : 16,
                        24,
                      ),
                      child: _EmptyDevicesCard(
                        onAdd: _openAddDevice,
                      ),
                    ),
                  )
                else
                  SliverPadding(
                    padding: EdgeInsets.fromLTRB(
                      screenWidth < 380 ? 12 : 16,
                      0,
                      screenWidth < 380 ? 12 : 16,
                      28,
                    ),
                    sliver: SliverGrid(
                      delegate: SliverChildBuilderDelegate(
                        (context, index) {
                          final device = devices[index];
                          final isSelectedGateway =
                              device.isGateway &&
                              data.selectedGatewayId == device.devId;

                          return _SmartDeviceCard(
                            device: device,
                            selectedGateway: isSelectedGateway,
                            homeName: homeTitle,
                            onTap: () {
                              if (device.isGateway) {
                                _openGatewayPairFlow(device);
                                return;
                              }
                              _openDevicePanel(device);
                            },
                          );
                        },
                        childCount: devices.length,
                      ),
                      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: gridCount,
                        mainAxisSpacing: 14,
                        crossAxisSpacing: 14,
                        childAspectRatio: gridAspect,
                      ),
                    ),
                  ),
                SliverToBoxAdapter(
                  child: busy
                      ? const Padding(
                          padding: EdgeInsets.symmetric(vertical: 40),
                          child: Center(
                            child: CircularProgressIndicator(),
                          ),
                        )
                      : const SizedBox.shrink(),
                ),
              ],
            ),
          );
        },
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _openAddDevice,
        backgroundColor: _cyan,
        foregroundColor: Colors.black,
        child: const Icon(Icons.add_rounded),
      ),
    );
  }
}

class _GatewaySubDevicePairingPage extends ConsumerStatefulWidget {
  const _GatewaySubDevicePairingPage({
    required this.gateway,
  });

  final ThingDevice gateway;

  @override
  ConsumerState<_GatewaySubDevicePairingPage> createState() =>
      _GatewaySubDevicePairingPageState();
}

class _GatewaySubDevicePairingPageState
    extends ConsumerState<_GatewaySubDevicePairingPage>
    with SingleTickerProviderStateMixin {
  static const _bg = Color(0xFF000000);
  static const _card = Color(0xFF14171D);
  static const _blue = Color(0xFF3F9BFF);
  static const _muted = Color(0xFFAAB3C2);

  late final AnimationController _controller;

  bool _working = true;
  String _headline = 'Searching for nearby devices';
  String _subtitle =
      'Make sure your sub-device has entered pairing mode and stays close to the gateway.';
  String? _error;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 3),
    )..repeat();

    Future.microtask(_startPairing);
  }

  @override
  void dispose() {
    _controller.dispose();
    ref.read(homeHubControllerProvider.notifier).refreshDevices();
    super.dispose();
  }

  Future<void> _startPairing() async {
    setState(() {
      _working = true;
      _error = null;
      _headline = 'Searching for nearby devices';
      _subtitle =
          'Make sure your sub-device has entered pairing mode and stays close to the gateway.';
    });

    try {
      final paired = await ref
          .read(homeHubControllerProvider.notifier)
          .startGatewaySubDevicePairing(
            gatewayDevId: widget.gateway.devId,
            timeoutSeconds: 120,
          );

      if (!mounted) return;

      setState(() {
        _working = false;
        _headline = 'Sub-device added';
        _subtitle =
            '${paired?.name ?? 'A new device'} was added successfully to ${widget.gateway.name}.';
      });

      await Future.delayed(const Duration(milliseconds: 900));
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _working = false;
        _error = e.toString();
        _headline = 'Could not add the sub-device';
        _subtitle =
            'Check that the gateway is online, then put the Zigbee sub-device back into pairing mode and try again.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.of(context).size.width;
    final radarSize = math.min(width * 0.72, 280.0);

    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        foregroundColor: Colors.white,
        centerTitle: true,
        title: const Text(
          'Add Device',
          style: TextStyle(fontWeight: FontWeight.w800),
        ),
      ),
      body: SafeArea(
        child: Padding(
          padding: EdgeInsets.fromLTRB(width < 380 ? 14 : 18, 8, width < 380 ? 14 : 18, 24),
          child: Column(
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: _blue.withOpacity(0.14),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.radar_rounded, color: _blue),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Text.rich(
                      TextSpan(
                        children: [
                          TextSpan(
                            text: '$_headline. ',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const TextSpan(
                            text: 'Make sure your device has entered ',
                            style: TextStyle(
                              color: Colors.white70,
                              fontSize: 15,
                            ),
                          ),
                          const TextSpan(
                            text: 'pairing mode',
                            style: TextStyle(
                              color: _blue,
                              fontSize: 15,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 18),
              Container(
                width: double.infinity,
                padding:
                    const EdgeInsets.symmetric(horizontal: 18, vertical: 22),
                decoration: BoxDecoration(
                  color: _card,
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: Colors.white.withOpacity(0.06)),
                ),
                child: Text(
                  'Gateway: ${widget.gateway.name}',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              const SizedBox(height: 28),
              Expanded(
                child: Center(
                  child: SizedBox(
                    width: radarSize,
                    height: radarSize,
                    child: AnimatedBuilder(
                      animation: _controller,
                      builder: (_, __) {
                        return CustomPaint(
                          painter: _RadarPainter(progress: _controller.value),
                        );
                      },
                    ),
                  ),
                ),
              ),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: const Color(0xFF0E1015),
                  borderRadius: BorderRadius.circular(22),
                  border: Border.all(color: Colors.white.withOpacity(0.06)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _headline,
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                        fontSize: 17,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _subtitle,
                      style: const TextStyle(
                        color: _muted,
                        height: 1.45,
                      ),
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 12),
                      Text(
                        _error!,
                        style: const TextStyle(
                          color: Colors.redAccent,
                          fontSize: 13,
                        ),
                      ),
                    ],
                    const SizedBox(height: 16),
                    LayoutBuilder(
                      builder: (context, constraints) {
                        final stacked = constraints.maxWidth < 360;
                        if (stacked) {
                          return Column(
                            children: [
                              SizedBox(
                                width: double.infinity,
                                child: FilledButton(
                                  onPressed: _working ? null : _startPairing,
                                  style: FilledButton.styleFrom(
                                    backgroundColor: _blue,
                                    foregroundColor: Colors.white,
                                    padding: const EdgeInsets.symmetric(vertical: 14),
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(16),
                                    ),
                                  ),
                                  child:
                                      Text(_working ? 'Scanning...' : 'Try again'),
                                ),
                              ),
                              const SizedBox(height: 12),
                              SizedBox(
                                width: double.infinity,
                                child: OutlinedButton(
                                  onPressed: () async {
                                    await ref
                                        .read(homeHubControllerProvider.notifier)
                                        .openBizAddDevice();
                                  },
                                  style: OutlinedButton.styleFrom(
                                    foregroundColor: Colors.white,
                                    padding: const EdgeInsets.symmetric(vertical: 14),
                                    side: BorderSide(
                                      color: Colors.white.withOpacity(0.14),
                                    ),
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(16),
                                    ),
                                  ),
                                  child: const Text('Open Tuya UI'),
                                ),
                              ),
                            ],
                          );
                        }

                        return Row(
                          children: [
                            Expanded(
                              child: FilledButton(
                                onPressed: _working ? null : _startPairing,
                                style: FilledButton.styleFrom(
                                  backgroundColor: _blue,
                                  foregroundColor: Colors.white,
                                  padding: const EdgeInsets.symmetric(vertical: 14),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(16),
                                  ),
                                ),
                                child: Text(_working ? 'Scanning...' : 'Try again'),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: OutlinedButton(
                                onPressed: () async {
                                  await ref
                                      .read(homeHubControllerProvider.notifier)
                                      .openBizAddDevice();
                                },
                                style: OutlinedButton.styleFrom(
                                  foregroundColor: Colors.white,
                                  padding: const EdgeInsets.symmetric(vertical: 14),
                                  side: BorderSide(
                                    color: Colors.white.withOpacity(0.14),
                                  ),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(16),
                                  ),
                                ),
                                child: const Text('Open Tuya UI'),
                              ),
                            ),
                          ],
                        );
                      },
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RadarPainter extends CustomPainter {
  _RadarPainter({required this.progress});

  final double progress;

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = size.width / 2;

    final bgPaint = Paint()
      ..color = const Color(0xFF07101A)
      ..style = PaintingStyle.fill;

    final ringPaint = Paint()
      ..color = const Color(0xFF0C2C49)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2;

    canvas.drawCircle(center, radius * 0.82, bgPaint);
    canvas.drawCircle(center, radius * 0.82, ringPaint);
    canvas.drawCircle(center, radius * 0.58, ringPaint);

    final sweepRect = Rect.fromCircle(center: center, radius: radius * 0.82);
    final sweepPaint = Paint()
      ..shader = SweepGradient(
        colors: [
          const Color(0x003F9BFF),
          const Color(0x223F9BFF),
          const Color(0x883F9BFF),
          const Color(0x003F9BFF),
        ],
        stops: const [0.0, 0.45, 0.78, 1.0],
        startAngle: 0,
        endAngle: math.pi * 2,
        transform: GradientRotation(progress * math.pi * 2),
      ).createShader(sweepRect);

    canvas.drawCircle(center, radius * 0.82, sweepPaint);

    final angle = -math.pi / 2 + (progress * math.pi * 2);
    final lineLength = radius * 0.82;

    final dot = Offset(
      center.dx + math.cos(angle) * lineLength,
      center.dy + math.sin(angle) * lineLength,
    );

    final linePaint = Paint()
      ..color = const Color(0xFF3F9BFF)
      ..strokeWidth = 2.2
      ..strokeCap = StrokeCap.round;

    canvas.drawLine(center, dot, linePaint);
    canvas.drawCircle(center, 6, Paint()..color = const Color(0xFF3F9BFF));
    canvas.drawCircle(dot, 2.5, Paint()..color = const Color(0xFF3F9BFF));
  }

  @override
  bool shouldRepaint(covariant _RadarPainter oldDelegate) {
    return oldDelegate.progress != progress;
  }
}

class _HeroHeader extends StatelessWidget {
  const _HeroHeader({
    required this.title,
    required this.homes,
    required this.selectedHomeId,
    required this.onSelectHome,
    required this.onAdd,
    required this.onLogout,
  });

  final String title;
  final List<dynamic> homes;
  final int? selectedHomeId;
  final Future<void> Function(dynamic home) onSelectHome;
  final VoidCallback onAdd;
  final VoidCallback onLogout;

  static const _navy = Color(0xFF1C2D44);
  static const _blue = Color(0xFF4B89CB);

  @override
  Widget build(BuildContext context) {
    final currentHomeName = homes
        .where((h) => h.homeId == selectedHomeId)
        .map((h) => h.name.toString())
        .cast<String?>()
        .firstWhere((e) => e != null, orElse: () => title);

    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [_navy, _blue],
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 18),
        child: Column(
          children: [
            Row(
              children: [
                const Icon(
                  Icons.home_outlined,
                  color: Colors.white,
                  size: 28,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    currentHomeName ?? title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                PopupMenuButton<int>(
                  tooltip: 'Switch home',
                  color: const Color(0xFF1A2230),
                  icon: const Icon(
                    Icons.keyboard_arrow_down_rounded,
                    color: Colors.white,
                  ),
                  onSelected: (id) async {
                    final match = homes.firstWhere((h) => h.homeId == id);
                    await onSelectHome(match);
                  },
                  itemBuilder: (_) => homes
                      .map(
                        (h) => PopupMenuItem<int>(
                          value: h.homeId as int,
                          child: Text(
                            h.name.toString(),
                            style: const TextStyle(color: Colors.white),
                          ),
                        ),
                      )
                      .toList(),
                ),
                IconButton(
                  onPressed: onAdd,
                  icon:
                      const Icon(Icons.add_rounded, color: Colors.white, size: 30),
                ),
                IconButton(
                  onPressed: onLogout,
                  icon: const Icon(Icons.logout_rounded, color: Colors.white),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _PromoBanner extends StatelessWidget {
  const _PromoBanner({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(26),
      onTap: onTap,
      child: Ink(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(26),
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFFE9EEF8), Color(0xFFB7C7E2)],
          ),
        ),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final stacked = constraints.maxWidth < 360;
            if (stacked) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Your smart home is ready to grow',
                    style: TextStyle(
                      color: Color(0xFF132B55),
                      fontWeight: FontWeight.w900,
                      fontSize: 16,
                    ),
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    '• Add gateways and Zigbee devices\n'
                    '• Launch the official Tuya add-device flow\n'
                    '• Keep your current gateway selected for sub-device pairing',
                    style: TextStyle(
                      color: Color(0xFF1A3766),
                      height: 1.4,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 14),
                  const _DarkPillButton(label: 'Check now'),
                  const SizedBox(height: 14),
                  Align(
                    alignment: Alignment.centerRight,
                    child: Container(
                      width: 92,
                      height: 92,
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.72),
                        borderRadius: BorderRadius.circular(22),
                      ),
                      child: const Icon(
                        Icons.devices_other_rounded,
                        size: 44,
                        color: Color(0xFF224A82),
                      ),
                    ),
                  ),
                ],
              );
            }

            return Row(
              children: [
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Your smart home is ready to grow',
                        style: TextStyle(
                          color: Color(0xFF132B55),
                          fontWeight: FontWeight.w900,
                          fontSize: 16,
                        ),
                      ),
                      SizedBox(height: 10),
                      Text(
                        '• Add gateways and Zigbee devices\n'
                        '• Launch the official Tuya add-device flow\n'
                        '• Keep your current gateway selected for sub-device pairing',
                        style: TextStyle(
                          color: Color(0xFF1A3766),
                          height: 1.4,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      SizedBox(height: 14),
                      _DarkPillButton(label: 'Check now'),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                Container(
                  width: 92,
                  height: 92,
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.72),
                    borderRadius: BorderRadius.circular(22),
                  ),
                  child: const Icon(
                    Icons.devices_other_rounded,
                    size: 44,
                    color: Color(0xFF224A82),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _AssistantCard extends StatelessWidget {
  const _AssistantCard({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF14171D),
        borderRadius: BorderRadius.circular(28),
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: const BoxDecoration(
                  gradient: LinearGradient(
                    colors: [Color(0xFF28D2C1), Color(0xFF7A4DFF)],
                  ),
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: InkWell(
                  borderRadius: BorderRadius.circular(26),
                  onTap: onTap,
                  child: Container(
                    height: 58,
                    alignment: Alignment.centerLeft,
                    padding: const EdgeInsets.symmetric(horizontal: 18),
                    decoration: BoxDecoration(
                      color: const Color(0xFF191D23),
                      borderRadius: BorderRadius.circular(26),
                      border: Border.all(color: Colors.white.withOpacity(0.10)),
                    ),
                    child: const Text(
                      'How can I help?',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.06),
              borderRadius: BorderRadius.circular(20),
            ),
            child: const Row(
              children: [
                Icon(Icons.chat_bubble_outline_rounded, color: Colors.white70),
                SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Nice to see you again. Add a gateway, then add Zigbee sub-devices from the current gateway card.',
                    style: TextStyle(
                      color: Colors.white70,
                      fontSize: 14,
                      height: 1.35,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _InfoStatCard extends StatelessWidget {
  const _InfoStatCard({
    required this.title,
    required this.value,
    required this.subtitle,
    required this.icon,
    required this.accent,
  });

  final String title;
  final String value;
  final String subtitle;
  final IconData icon;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 176),
      decoration: BoxDecoration(
        color: const Color(0xFF14171D),
        borderRadius: BorderRadius.circular(24),
      ),
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: accent, size: 34),
          const SizedBox(height: 28),
          Text(
            title,
            style: const TextStyle(
              color: Colors.white70,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            value,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 26,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            style: const TextStyle(
              color: Color(0xFF8C95A3),
              height: 1.35,
            ),
          ),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({
    required this.title,
    this.actionLabel,
    this.onAction,
  });

  final String title;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 20,
              fontWeight: FontWeight.w900,
            ),
          ),
        ),
        if (actionLabel != null && onAction != null)
          TextButton(
            onPressed: onAction,
            child: Text(
              actionLabel!,
              style: const TextStyle(
                color: Color(0xFF4F8FD6),
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
      ],
    );
  }
}

class _CurrentGatewayCard extends StatelessWidget {
  const _CurrentGatewayCard({
    required this.gateway,
    required this.onAddSubDevice,
    required this.onOpenTuyaUi,
  });

  final ThingDevice gateway;
  final VoidCallback onAddSubDevice;
  final VoidCallback onOpenTuyaUi;

  @override
  Widget build(BuildContext context) {
    final statusColor =
        gateway.isOnline ? const Color(0xFF2FD0A6) : Colors.redAccent;

    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF14171D),
        borderRadius: BorderRadius.circular(28),
      ),
      padding: const EdgeInsets.all(18),
      child: Column(
        children: [
          Row(
            children: [
              _DeviceIcon(iconUrl: gateway.iconUrl, isGateway: true),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      gateway.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 22,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Container(
                          width: 8,
                          height: 8,
                          decoration: BoxDecoration(
                            color: statusColor,
                            shape: BoxShape.circle,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          gateway.isOnline ? 'Online gateway' : 'Offline gateway',
                          style: const TextStyle(
                            color: Color(0xFF8C95A3),
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.05),
              borderRadius: BorderRadius.circular(18),
            ),
            child: const Text(
              'Use this gateway as the current target when adding Zigbee sub-devices.',
              style: TextStyle(
                color: Color(0xFFBCC4D1),
                height: 1.4,
              ),
            ),
          ),
          const SizedBox(height: 16),
          LayoutBuilder(
            builder: (context, constraints) {
              final stacked = constraints.maxWidth < 360;
              if (stacked) {
                return Column(
                  children: [
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton(
                        onPressed: onAddSubDevice,
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFF29D3C2),
                          foregroundColor: Colors.black,
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(18),
                          ),
                        ),
                        child: const Text(
                          'Add sub-device',
                          style: TextStyle(fontWeight: FontWeight.w800),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton(
                        onPressed: onOpenTuyaUi,
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          side: BorderSide(color: Colors.white.withOpacity(0.12)),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(18),
                          ),
                        ),
                        child: const Text(
                          'Open Tuya UI',
                          style: TextStyle(fontWeight: FontWeight.w800),
                        ),
                      ),
                    ),
                  ],
                );
              }

              return Row(
                children: [
                  Expanded(
                    child: FilledButton(
                      onPressed: onAddSubDevice,
                      style: FilledButton.styleFrom(
                        backgroundColor: const Color(0xFF29D3C2),
                        foregroundColor: Colors.black,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(18),
                        ),
                      ),
                      child: const Text(
                        'Add sub-device',
                        style: TextStyle(fontWeight: FontWeight.w800),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton(
                      onPressed: onOpenTuyaUi,
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        side: BorderSide(color: Colors.white.withOpacity(0.12)),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(18),
                        ),
                      ),
                      child: const Text(
                        'Open Tuya UI',
                        style: TextStyle(fontWeight: FontWeight.w800),
                      ),
                    ),
                  ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _EmptyGatewayCard extends StatelessWidget {
  const _EmptyGatewayCard({
    required this.onAddGateway,
  });

  final VoidCallback onAddGateway;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF14171D),
        borderRadius: BorderRadius.circular(28),
      ),
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          const Icon(
            Icons.hub_outlined,
            color: Color(0xFF4F8FD6),
            size: 42,
          ),
          const SizedBox(height: 14),
          const Text(
            'No gateway found yet',
            style: TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 10),
          const Text(
            'Add your first gateway from the official Tuya flow, then this card will become your current gateway for Zigbee sub-device pairing.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: Color(0xFF8C95A3),
              height: 1.45,
            ),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: onAddGateway,
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF4F8FD6),
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 14),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
              ),
            ),
            child: const Text(
              'Add gateway / device',
              style: TextStyle(fontWeight: FontWeight.w800),
            ),
          ),
        ],
      ),
    );
  }
}

class _SmartDeviceCard extends StatelessWidget {
  const _SmartDeviceCard({
    required this.device,
    required this.selectedGateway,
    required this.homeName,
    required this.onTap,
  });

  final ThingDevice device;
  final bool selectedGateway;
  final String homeName;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final powerColor =
        device.isOnline ? const Color(0xFF29D3C2) : const Color(0xFF5C6472);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(26),
      child: Ink(
        decoration: BoxDecoration(
          color: const Color(0xFF14171D),
          borderRadius: BorderRadius.circular(26),
          border: selectedGateway
              ? Border.all(color: const Color(0xFF29D3C2), width: 1.4)
              : null,
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Flexible(
                    child: _DeviceIcon(
                      iconUrl: device.iconUrl,
                      isGateway: device.isGateway,
                    ),
                  ),
                  const Spacer(),
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: powerColor,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      device.isGateway
                          ? Icons.router_rounded
                          : Icons.power_settings_new_rounded,
                      color: Colors.black,
                    ),
                  ),
                ],
              ),
              const Spacer(),
              if (selectedGateway)
                Container(
                  margin: const EdgeInsets.only(bottom: 10),
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                    color: const Color(0xFF29D3C2).withOpacity(0.18),
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: const Text(
                    'Current gateway',
                    style: TextStyle(
                      color: Color(0xFF29D3C2),
                      fontSize: 12,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              Text(
                device.name,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w900,
                  height: 1.1,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                device.isGateway
                    ? homeName
                    : '$homeName${device.isOnline ? '' : '  |  Offline'}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Color(0xFF8C95A3),
                  fontSize: 14,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DeviceIcon extends StatelessWidget {
  const _DeviceIcon({
    required this.iconUrl,
    required this.isGateway,
  });

  final String? iconUrl;
  final bool isGateway;

  @override
  Widget build(BuildContext context) {
    final fallbackIcon =
        isGateway ? Icons.router_rounded : Icons.sensors_rounded;

    return Container(
      width: 54,
      height: 54,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
      ),
      clipBehavior: Clip.antiAlias,
      child: iconUrl != null && iconUrl!.trim().isNotEmpty
          ? Image.network(
              iconUrl!,
              fit: BoxFit.cover,
              errorBuilder: (_, __, ___) =>
                  Icon(fallbackIcon, color: Colors.black54),
            )
          : Icon(fallbackIcon, color: Colors.black54),
    );
  }
}

class _EmptyDevicesCard extends StatelessWidget {
  const _EmptyDevicesCard({
    required this.onAdd,
  });

  final VoidCallback onAdd;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF14171D),
        borderRadius: BorderRadius.circular(28),
      ),
      padding: const EdgeInsets.all(22),
      child: Column(
        children: [
          const Icon(
            Icons.devices_other_outlined,
            size: 44,
            color: Color(0xFF4F8FD6),
          ),
          const SizedBox(height: 14),
          const Text(
            'No devices in this home yet',
            style: TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w900,
              fontSize: 18,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Use the add button to open the official Tuya pairing UI, or add Zigbee sub-devices once a gateway is available.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: Color(0xFF8C95A3),
              height: 1.45,
            ),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: onAdd,
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF4F8FD6),
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
              ),
            ),
            child: const Text(
              'Open add-device flow',
              style: TextStyle(fontWeight: FontWeight.w800),
            ),
          ),
        ],
      ),
    );
  }
}

class _DarkPillButton extends StatelessWidget {
  const _DarkPillButton({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 42,
      padding: const EdgeInsets.symmetric(horizontal: 18),
      decoration: BoxDecoration(
        color: Colors.black,
        borderRadius: BorderRadius.circular(999),
      ),
      alignment: Alignment.center,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(width: 8),
          const Icon(Icons.chevron_right_rounded, color: Colors.white),
        ],
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({
    required this.message,
    required this.onRetry,
  });

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline_rounded,
                color: Colors.redAccent, size: 46),
            const SizedBox(height: 14),
            const Text(
              'Something went wrong',
              style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w900,
                fontSize: 20,
              ),
            ),
            const SizedBox(height: 10),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: Color(0xFF8C95A3),
                height: 1.45,
              ),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: onRetry,
              child: const Text('Retry'),
            ),
          ],
        ),
      ),
    );
  }
}