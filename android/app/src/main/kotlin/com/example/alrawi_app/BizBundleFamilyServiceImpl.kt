package com.example.alrawi_app

import android.util.Log

/**
 * Reflection-only helper to push "current home / family" into BizBundle context.
 * This avoids importing AbsBizBundleFamilyService (which may not be on compile classpath).
 */
object BizBundleFamilyServiceImpl {

    private const val TAG = "BizFamilyService"

    @Volatile private var lastHomeId: Long = 0L
    @Volatile private var lastHomeName: String = ""

    fun setHome(homeId: Long, name: String) {
        lastHomeId = homeId
        lastHomeName = name
        Log.d(TAG, "➡️ setHome(homeId=$homeId, name=$name)")

        // Try: MicroServiceManager(Impl).findServiceByInterface(AbsBizBundleFamilyService).shiftCurrentFamily(...)
        tryShiftCurrentFamilyViaMicroService(homeId, name)
    }

    private fun tryShiftCurrentFamilyViaMicroService(homeId: Long, name: String) {
        val mgr = findMicroServiceManagerInstance() ?: run {
            Log.w(TAG, "⚠️ MicroServiceManager not found in app classpath.")
            return
        }

        val familyService = findAbsBizBundleFamilyService(mgr)
        if (familyService == null) {
            Log.w(TAG, "⚠️ AbsBizBundleFamilyService not found/created (service is null).")
            return
        }

        // shiftCurrentFamily(long, String)
        try {
            val m = familyService.javaClass.methods.firstOrNull { mm ->
                mm.name == "shiftCurrentFamily" &&
                    mm.parameterTypes.size == 2 &&
                    (mm.parameterTypes[0] == java.lang.Long.TYPE || mm.parameterTypes[0] == java.lang.Long::class.java) &&
                    mm.parameterTypes[1] == String::class.java
            }

            if (m != null) {
                m.invoke(familyService, homeId, name)
                Log.d(TAG, "✅ shiftCurrentFamily(homeId=$homeId) invoked on ${familyService.javaClass.name}")
                return
            }

            Log.w(TAG, "⚠️ shiftCurrentFamily(long,String) not found on ${familyService.javaClass.name}")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ shiftCurrentFamily invoke failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun findMicroServiceManagerInstance(): Any? {
        val candidates = listOf(
            "com.thingclips.smart.framework.service.MicroServiceManager",
            "com.thingclips.smart.framework.service.MicroServiceManagerImpl",
            "com.tuya.smart.framework.service.MicroServiceManager",
            "com.tuya.smart.framework.service.MicroServiceManagerImpl"
        )

        for (cn in candidates) {
            try {
                val clz = Class.forName(cn)

                // static getInstance()
                clz.methods.firstOrNull { it.name == "getInstance" && it.parameterTypes.isEmpty() }?.let { m ->
                    val inst = m.invoke(null)
                    if (inst != null) return inst
                }

                // Kotlin object INSTANCE
                runCatching {
                    val f = clz.getDeclaredField("INSTANCE")
                    f.isAccessible = true
                    val inst = f.get(null)
                    if (inst != null) return inst
                }

                // static instance()
                clz.methods.firstOrNull { it.name == "instance" && it.parameterTypes.isEmpty() }?.let { m ->
                    val inst = m.invoke(null)
                    if (inst != null) return inst
                }
            } catch (_: Throwable) {
                // ignore and try next
            }
        }
        return null
    }

    private fun findAbsBizBundleFamilyService(mgr: Any): Any? {
        // Your logs + docs typically refer to AbsBizBundleFamilyService
        val familyCandidates = listOf(
            "com.thingclips.smart.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.thingclips.smart.home.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.thingclips.smart.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.home.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.family.api.AbsBizBundleFamilyService"
        )

        // MicroServiceManagerImpl has findServiceByInterface(Class)
        val findMethod = mgr.javaClass.methods.firstOrNull { m ->
            m.name == "findServiceByInterface" &&
                m.parameterTypes.size == 1 &&
                m.parameterTypes[0] == Class::class.java
        } ?: return null

        for (cn in familyCandidates) {
            try {
                val iface = Class.forName(cn)
                val service = findMethod.invoke(mgr, iface)
                if (service != null) {
                    Log.d(TAG, "✅ findServiceByInterface($cn) => ${service.javaClass.name}")
                    return service
                }
            } catch (_: Throwable) {
                // ignore
            }
        }
        return null
    }
}