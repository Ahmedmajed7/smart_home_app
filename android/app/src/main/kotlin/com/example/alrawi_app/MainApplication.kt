package com.example.alrawi_app

import android.app.Application
import android.util.Log
import com.facebook.drawee.backends.pipeline.Fresco
import com.thingclips.smart.home.sdk.ThingHomeSdk
import java.lang.reflect.Proxy

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            Fresco.initialize(this)
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ Fresco.init failed (continuing)", t)
        }

        try {
            ThingHomeSdk.init(this)
            ThingHomeSdk.setDebugMode(true)
            Log.d(TAG, "✅ ThingHomeSdk.init OK")
        } catch (t: Throwable) {
            Log.e(TAG, "❌ ThingHomeSdk.init failed", t)
            return
        }

        initBizBundleFramework()
    }

    private fun initBizBundleFramework() {
        val wrapperCandidates = listOf(
            "com.thingclips.smart.bizbundle.TuyaWrapper",
            "com.thingclips.smart.android.bizbundle.TuyaWrapper",
            "com.tuya.smart.bizbundle.TuyaWrapper",
            "com.tuya.smart.android.bizbundle.TuyaWrapper"
        )

        val initializerCandidates = listOf(
            "com.thingclips.smart.bizbundle.initializer.BizBundleInitializer",
            "com.thingclips.smart.android.bizbundle.initializer.BizBundleInitializer",
            "com.tuya.smart.bizbundle.initializer.BizBundleInitializer",
            "com.tuya.smart.android.bizbundle.initializer.BizBundleInitializer"
        )

        var inited = false

        // 1) Prefer TuyaWrapper.init(...) because docs are written around this flow.
        for (className in wrapperCandidates) {
            try {
                val clz = Class.forName(className)

                val init3 = clz.methods.firstOrNull { m ->
                    m.name == "init" &&
                        m.parameterTypes.size == 3 &&
                        Application::class.java.isAssignableFrom(m.parameterTypes[0])
                }

                if (init3 != null) {
                    val routeListenerType = init3.parameterTypes[1]
                    val serviceListenerType = init3.parameterTypes[2]

                    val routeListener = createLoggingProxy(routeListenerType, "RouteEventListener")
                    val serviceListener = createLoggingProxy(serviceListenerType, "ServiceEventListener")

                    init3.invoke(null, this, routeListener, serviceListener)
                    Log.d(TAG, "✅ BizBundle init OK via $className.init(Application, Route, Service)")
                    inited = true
                    break
                }

                val init1 = clz.methods.firstOrNull { m ->
                    m.name == "init" &&
                        m.parameterTypes.size == 1 &&
                        Application::class.java.isAssignableFrom(m.parameterTypes[0])
                }

                if (init1 != null) {
                    init1.invoke(null, this)
                    Log.d(TAG, "✅ BizBundle init OK via $className.init(Application)")
                    inited = true
                    break
                }
            } catch (t: Throwable) {
                Log.w(TAG, "BizBundle wrapper init failed for $className: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        // 2) Fallback to initializer if wrapper class is not exposed in this build.
        if (!inited) {
            for (className in initializerCandidates) {
                try {
                    val clz = Class.forName(className)

                    val init3 = clz.methods.firstOrNull { m ->
                        m.name == "init" &&
                            m.parameterTypes.size == 3 &&
                            Application::class.java.isAssignableFrom(m.parameterTypes[0])
                    }

                    if (init3 != null) {
                        val routeListenerType = init3.parameterTypes[1]
                        val serviceListenerType = init3.parameterTypes[2]

                        val routeListener = createLoggingProxy(routeListenerType, "RouteEventListener")
                        val serviceListener = createLoggingProxy(serviceListenerType, "ServiceEventListener")

                        init3.invoke(null, this, routeListener, serviceListener)
                        Log.d(TAG, "✅ BizBundle init OK via $className.init(Application, Route, Service)")
                        inited = true
                        break
                    }

                    val init1 = clz.methods.firstOrNull { m ->
                        m.name == "init" &&
                            m.parameterTypes.size == 1 &&
                            Application::class.java.isAssignableFrom(m.parameterTypes[0])
                    }

                    if (init1 != null) {
                        init1.invoke(null, this)
                        Log.d(TAG, "✅ BizBundle init OK via $className.init(Application)")
                        inited = true
                        break
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "BizBundle initializer init failed for $className: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        }

        if (!inited) {
            Log.e(TAG, "❌ BizBundle init failed. QR / Add Device UI will not be reliable.")
            return
        }

        // 3) Best-effort TuyaOptimusSdk.init(this) per official framework init sequence.
        initOptimusBestEffort()

        // 4) Register AbsBizBundleFamilyService -> BizBundleFamilyServiceImpl
        registerFamilyServiceBestEffort()

        // 5) Keep current cached values visible in logs.
        BizBundleFamilyServiceImpl.bootstrap()
    }

    private fun initOptimusBestEffort() {
        val candidates = listOf(
            "com.thingclips.smart.optimus.sdk.TuyaOptimusSdk",
            "com.thingclips.smart.android.optimus.sdk.TuyaOptimusSdk",
            "com.tuya.smart.optimus.sdk.TuyaOptimusSdk",
            "com.tuya.smart.android.optimus.sdk.TuyaOptimusSdk"
        )

        for (cn in candidates) {
            try {
                val clz = Class.forName(cn)
                val init = clz.methods.firstOrNull { m ->
                    m.name == "init" &&
                        m.parameterTypes.size == 1 &&
                        Application::class.java.isAssignableFrom(m.parameterTypes[0])
                } ?: continue

                init.invoke(null, this)
                Log.d(TAG, "✅ TuyaOptimusSdk.init OK via $cn")
                return
            } catch (_: Throwable) {
            }
        }

        Log.w(TAG, "⚠️ TuyaOptimusSdk class not found (not fatal for all builds).")
    }

    private fun registerFamilyServiceBestEffort() {
        val wrapperCandidates = listOf(
            "com.thingclips.smart.bizbundle.TuyaWrapper",
            "com.thingclips.smart.android.bizbundle.TuyaWrapper",
            "com.tuya.smart.bizbundle.TuyaWrapper",
            "com.tuya.smart.android.bizbundle.TuyaWrapper",
            "com.thingclips.smart.bizbundle.initializer.BizBundleInitializer",
            "com.thingclips.smart.android.bizbundle.initializer.BizBundleInitializer",
            "com.tuya.smart.bizbundle.initializer.BizBundleInitializer",
            "com.tuya.smart.android.bizbundle.initializer.BizBundleInitializer"
        )

        val familyServiceCandidates = listOf(
            "com.thingclips.smart.commonbiz.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.thingclips.smart.family.bizbundle.api.AbsBizBundleFamilyService",
            "com.thingclips.smart.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.thingclips.smart.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.commonbiz.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.family.bizbundle.api.AbsBizBundleFamilyService",
            "com.tuya.smart.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.family.api.AbsBizBundleFamilyService"
        )

        for (wrapperName in wrapperCandidates) {
            try {
                val wrapperClass = Class.forName(wrapperName)
                val register = wrapperClass.methods.firstOrNull { m ->
                    m.name == "registerService" &&
                        m.parameterTypes.size == 2 &&
                        m.parameterTypes[0] == Class::class.java
                } ?: continue

                for (ifaceName in familyServiceCandidates) {
                    try {
                        val ifaceClass = Class.forName(ifaceName)
                        register.invoke(null, ifaceClass, BizBundleFamilyServiceImpl)
                        Log.d(TAG, "✅ registerService($ifaceName -> BizBundleFamilyServiceImpl) via $wrapperName")
                        return
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
            }
        }

        Log.w(TAG, "⚠️ Could not register AbsBizBundleFamilyService reflectively.")
    }

    private fun createLoggingProxy(iface: Class<*>, label: String): Any {
        return Proxy.newProxyInstance(
            iface.classLoader,
            arrayOf(iface)
        ) { _, method, args ->
            if (method.name.contains("onFail", ignoreCase = true)) {
                Log.e(TAG, "❌ $label callback: method=${method.name} args=${args?.toList()}")
            }
            null
        }
    }

    companion object {
        private const val TAG = "MainApplication"
    }
}