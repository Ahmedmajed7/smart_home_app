class ThingDevice {
  final String devId;
  final String name;
  final String? iconUrl;
  final bool isOnline;
  final bool isGateway;
  final String? parentId;
  final String? productId;
  final String? nodeId;
  final String? category;

  const ThingDevice({
    required this.devId,
    required this.name,
    required this.iconUrl,
    required this.isOnline,
    required this.isGateway,
    required this.parentId,
    required this.productId,
    required this.nodeId,
    required this.category,
  });

  bool get isSubDevice => parentId != null && parentId!.isNotEmpty;

  factory ThingDevice.fromMap(Map<String, dynamic> map) {
    String? clean(dynamic value) {
      final text = value?.toString().trim();
      if (text == null || text.isEmpty || text == 'null') return null;
      return text;
    }

    return ThingDevice(
      devId: clean(map['devId']) ?? '',
      name: clean(map['name']) ?? 'Unnamed Device',
      iconUrl: clean(map['iconUrl']),
      isOnline: map['isOnline'] == true,
      isGateway: map['isGateway'] == true,
      parentId: clean(map['parentId']),
      productId: clean(map['productId']),
      nodeId: clean(map['nodeId']),
      category: clean(map['category']),
    );
  }
}