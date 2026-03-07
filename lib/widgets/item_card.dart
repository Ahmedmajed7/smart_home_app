import 'package:flutter/material.dart';

class ItemCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final String? imageUrl;
  final bool isOnline;
  final bool isGateway;
  final int? childCount;
  final VoidCallback? onTap;
  final Widget? trailing;

  const ItemCard({
    super.key,
    required this.title,
    required this.subtitle,
    required this.imageUrl,
    required this.isOnline,
    required this.isGateway,
    this.childCount,
    this.onTap,
    this.trailing,
  });

  static const Color _bgTop = Color(0xFF0E1B2A);
  static const Color _bgBottom = Color(0xFF13263A);
  static const Color _accent = Color(0xFF4DA3FF);
  static const Color _soft = Color(0xFFEDF4FF);
  static const Color _success = Color(0xFF24C38A);
  static const Color _offline = Color(0xFF93A4B7);

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(24);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: radius,
        onTap: onTap,
        child: Ink(
          decoration: BoxDecoration(
            borderRadius: radius,
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [_bgTop, _bgBottom],
            ),
            border: Border.all(
              color: Colors.white.withOpacity(0.08),
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.12),
                blurRadius: 20,
                offset: const Offset(0, 10),
              ),
            ],
          ),
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Row(
              children: [
                _DeviceAvatar(
                  imageUrl: imageUrl,
                  isGateway: isGateway,
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          _StatusDot(
                            online: isOnline,
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        subtitle,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: _soft.withOpacity(0.78),
                          fontSize: 13,
                          height: 1.35,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          _Pill(
                            icon: isGateway ? Icons.hub_rounded : Icons.devices_other_rounded,
                            label: isGateway ? 'Gateway' : 'Sub-device',
                          ),
                          _Pill(
                            icon: Icons.wifi_tethering_rounded,
                            label: isOnline ? 'Online' : 'Offline',
                            tint: isOnline ? _success : _offline,
                          ),
                          if (isGateway && childCount != null)
                            _Pill(
                              icon: Icons.device_hub_rounded,
                              label: '$childCount linked',
                              tint: _accent,
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
                if (trailing != null) ...[
                  const SizedBox(width: 12),
                  trailing!,
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _DeviceAvatar extends StatelessWidget {
  final String? imageUrl;
  final bool isGateway;

  const _DeviceAvatar({
    required this.imageUrl,
    required this.isGateway,
  });

  @override
  Widget build(BuildContext context) {
    const bg = Color(0x1AFFFFFF);
    const iconColor = Color(0xFFB8D7FF);

    return Container(
      width: 62,
      height: 62,
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withOpacity(0.08)),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(18),
        child: imageUrl != null && imageUrl!.isNotEmpty
            ? Image.network(
                imageUrl!,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => Icon(
                  isGateway ? Icons.router_rounded : Icons.devices_rounded,
                  color: iconColor,
                  size: 30,
                ),
              )
            : Icon(
                isGateway ? Icons.router_rounded : Icons.devices_rounded,
                color: iconColor,
                size: 30,
              ),
      ),
    );
  }
}

class _StatusDot extends StatelessWidget {
  final bool online;

  const _StatusDot({
    required this.online,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 10,
      height: 10,
      decoration: BoxDecoration(
        color: online ? const Color(0xFF24C38A) : const Color(0xFF93A4B7),
        shape: BoxShape.circle,
        boxShadow: [
          BoxShadow(
            color: (online ? const Color(0xFF24C38A) : const Color(0xFF93A4B7))
                .withOpacity(0.45),
            blurRadius: 10,
          ),
        ],
      ),
    );
  }
}

class _Pill extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color? tint;

  const _Pill({
    required this.icon,
    required this.label,
    this.tint,
  });

  @override
  Widget build(BuildContext context) {
    final color = tint ?? const Color(0xFFB8D7FF);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.06),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: Colors.white.withOpacity(0.06)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 15, color: color),
          const SizedBox(width: 6),
          Text(
            label,
            style: TextStyle(
              color: color,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}