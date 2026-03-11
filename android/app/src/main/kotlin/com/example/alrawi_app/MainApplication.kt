package com.example.alrawi_app

import android.app.Application
import android.util.Log
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.soloader.SoLoader
import com.thingclips.smart.home.sdk.ThingHomeSdk
import java.lang.reflect.Proxy

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "🚀 App starting")

        try {
            Fresco.initialize(this)
            Log.d(TAG, "✅ Fresco.init OK")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ Fresco.init failed (continuing)", t)
        }

        try {
            SoLoader.init(this, false)
            Log.d(TAG, "✅ SoLoader.init OK")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ SoLoader.init failed (continuing)", t)
        }

        try {
            ThingHomeSdk.init(this)
            ThingHomeSdk.setDebugMode(true)
            Log.d(TAG, "✅ ThingHomeSdk.init OK")
        } catch (t: Throwable) {
            Log.e(TAG, "❌ ThingHomeSdk.init failed", t)
            return
        }

        initBizBundleReflectively()
        registerBizBundleFamilyServiceReflectively()

        Log.d(TAG, "✅ Application ready")
    }

    private fun initBizBundleReflectively() {
        val candidates = listOf(
            "com.thingclips.smart.bizbundle.initializer.BizBundleInitializer",
            "com.thingclips.smart.android.bizbundle.initializer.BizBundleInitializer",
            "com.tuya.smart.bizbundle.initializer.BizBundleInitializer",
            "com.tuya.smart.android.bizbundle.initializer.BizBundleInitializer"
        )

        for (className in candidates) {
            try {
                val initializerClass = Class.forName(className)

                initializerClass.methods.firstOrNull { m ->
                    m.name == "init" &&
                        m.parameterTypes.size == 1 &&
                        Application::class.java.isAssignableFrom(m.parameterTypes[0])
                }?.let { m ->
                    m.invoke(null, this)
                    Log.d(TAG, "✅ BizBundle init OK via $className.init(Application)")
                    return
                }

                val init3 = initializerClass.methods.firstOrNull { m ->
                    m.name == "init" &&
                        m.parameterTypes.size == 3 &&
                        Application::class.java.isAssignableFrom(m.parameterTypes[0]) &&
                        m.parameterTypes[1].isInterface &&
                        m.parameterTypes[2].isInterface
                }

                if (init3 != null) {
                    val routeListenerType = init3.parameterTypes[1]
                    val serviceListenerType = init3.parameterTypes[2]

                    val routeListener = createLoggingProxy(routeListenerType, "RouteEventListener")
                    val serviceListener = createLoggingProxy(serviceListenerType, "ServiceEventListener")

                    init3.invoke(null, this, routeListener, serviceListener)
                    Log.d(TAG, "✅ BizBundle init OK via $className.init(Application, Route, Service)")
                    return
                }

                Log.w(TAG, "⚠️ Found $className but no supported init(...) signature")
            } catch (t: Throwable) {
                Log.w(TAG, "⚠️ BizBundle init failed for $className: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        Log.e(TAG, "❌ BizBundle initializer not found")
    }

    private fun registerBizBundleFamilyServiceReflectively() {
        val initializerCandidates = listOf(
            "com.thingclips.smart.bizbundle.initializer.BizBundleInitializer",
            "com.thingclips.smart.android.bizbundle.initializer.BizBundleInitializer",
            "com.tuya.smart.bizbundle.initializer.BizBundleInitializer",
            "com.tuya.smart.android.bizbundle.initializer.BizBundleInitializer"
        )

        val familyServiceCandidates = listOf(
            "com.thingclips.smart.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.thingclips.smart.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.family.api.AbsBizBundleFamilyService"
        )

        for (initializerName in initializerCandidates) {
            try {
                val initializerClass = Class.forName(initializerName)

                val registerMethod = initializerClass.methods.firstOrNull { m ->
                    m.name == "registerService" &&
                        m.parameterTypes.size == 2 &&
                        m.parameterTypes[0] == Class::class.java
                } ?: continue

                for (serviceName in familyServiceCandidates) {
                    try {
                        val familyServiceClass = Class.forName(serviceName)
                        registerMethod.invoke(null, familyServiceClass, BizBundleFamilyServiceImpl)
                        Log.d(TAG, "✅ Family service registered via $initializerName / $serviceName")
                        return
                    } catch (inner: Throwable) {
                        Log.w(TAG, "⚠️ Family service register failed for $serviceName: ${inner.message}")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "⚠️ registerService lookup failed for $initializerName: ${t.message}")
            }
        }

        Log.e(TAG, "❌ Could not register BizBundle family service")
    }

    private fun createLoggingProxy(iface: Class<*>, label: String): Any {
        return Proxy.newProxyInstance(
            iface.classLoader,
            arrayOf(iface)
        ) { _, method, args ->
            if (
                method.name.contains("fail", ignoreCase = true) ||
                method.name.contains("error", ignoreCase = true)
            ) {
                Log.e(TAG, "❌ $label callback: method=${method.name} args=${args?.toList()}")
            }
            null
        }
    }

    companion object {
        private const val TAG = "MainApplication"
    }
}