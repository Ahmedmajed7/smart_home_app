package com.example.alrawi_app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.api.ILogoutCallback
import com.thingclips.smart.android.user.api.IRegisterCallback
import com.thingclips.smart.android.user.bean.User
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.sdk.api.IResultCallback
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy

object TuyaBridge {

    private const val TAG = "TuyaBridge"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activityRef: WeakReference<Activity>? = null
    private var channel: MethodChannel? = null

    @JvmStatic fun bindActivity(activity: Activity) { activityRef = WeakReference(activity) }
    @JvmStatic fun unbindActivity(activity: Activity) { if (activityRef?.get() === activity) activityRef = null }
    private val currentActivity: Activity? get() = activityRef?.get()

    @JvmStatic
    fun setChannel(ch: MethodChannel) {
        channel = ch
    }

    private fun emit(event: String, args: Any? = null) {
        try { channel?.invokeMethod(event, args) } catch (_: Throwable) {}
    }

    @JvmStatic
    fun handle(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {

                "initSdk" -> {
                    Log.d(TAG, "initSdk()")
                    result.success(true)
                }

                "isLoggedIn" -> {
                    val logged = ThingHomeSdk.getUserInstance().isLogin
                    Log.d(TAG, "isLoggedIn() => $logged")
                    result.success(logged)
                }

                // ---------------- AUTH ----------------
                "loginByEmail" -> {
                    val countryCode = call.argument<String>("countryCode") ?: ""
                    val email = call.argument<String>("email") ?: ""
                    val password = call.argument<String>("password") ?: ""

                    Log.d(TAG, "➡️ loginByEmail(country=$countryCode, email=$email)")

                    ThingHomeSdk.getUserInstance().loginWithEmail(
                        countryCode, email, password,
                        object : ILoginCallback {
                            override fun onSuccess(user: User?) {
                                Log.d(TAG, "✅ loginByEmail success user=${user?.uid}")
                                tryCallWrapperOnLogin()
                                mainHandler.post { result.success(true) }
                            }

                            override fun onError(code: String?, error: String?) {
                                Log.e(TAG, "❌ loginByEmail error code=$code msg=$error")
                                mainHandler.post { result.error(code ?: "LOGIN_FAILED", error ?: "login failed", null) }
                            }
                        }
                    )
                }

                "sendEmailCode" -> {
                    val countryCode = call.argument<String>("countryCode") ?: ""
                    val email = call.argument<String>("email") ?: ""
                    val type = call.argument<Int>("type") ?: 1

                    Log.d(TAG, "➡️ sendEmailCode(country=$countryCode, email=$email, type=$type)")

                    ThingHomeSdk.getUserInstance().sendVerifyCodeWithUserName(
                        email, "", countryCode, type,
                        object : IResultCallback {
                            override fun onSuccess() {
                                Log.d(TAG, "✅ sendEmailCode success")
                                mainHandler.post { result.success(true) }
                            }
                            override fun onError(code: String?, error: String?) {
                                Log.e(TAG, "❌ sendEmailCode error code=$code msg=$error")
                                mainHandler.post { result.error(code ?: "SEND_CODE_FAILED", error ?: "send code failed", null) }
                            }
                        }
                    )
                }

                "registerEmail" -> {
                    val countryCode = call.argument<String>("countryCode") ?: ""
                    val email = call.argument<String>("email") ?: ""
                    val password = call.argument<String>("password") ?: ""
                    val code = call.argument<String>("code") ?: ""

                    Log.d(TAG, "➡️ registerEmail(country=$countryCode, email=$email)")

                    ThingHomeSdk.getUserInstance().registerAccountWithEmail(
                        countryCode, email, password, code,
                        object : IRegisterCallback {
                            override fun onSuccess(user: User?) {
                                Log.d(TAG, "✅ registerEmail success user=${user?.uid}")
                                tryCallWrapperOnLogin()
                                mainHandler.post { result.success(true) }
                            }
                            override fun onError(code: String?, error: String?) {
                                Log.e(TAG, "❌ registerEmail error code=$code msg=$error")
                                mainHandler.post { result.error(code ?: "REGISTER_FAILED", error ?: "register failed", null) }
                            }
                        }
                    )
                }

                "logout" -> {
                    Log.d(TAG, "➡️ logout()")
                    ThingHomeSdk.getUserInstance().logout(object : ILogoutCallback {
                        override fun onSuccess() {
                            Log.d(TAG, "✅ logout success")
                            tryCallWrapperOnLogout()
                            mainHandler.post { result.success(true) }
                        }
                        override fun onError(code: String?, error: String?) {
                            Log.e(TAG, "❌ logout error code=$code msg=$error")
                            mainHandler.post { result.error(code ?: "LOGOUT_FAILED", error ?: "logout failed", null) }
                        }
                    })
                }

                // ---------------- HOME ----------------
                "getHomeList" -> {
                    Log.d(TAG, "➡️ getHomeList()")
                    ThingHomeSdk.getHomeManagerInstance().queryHomeList(object : IThingGetHomeListCallback {
                        override fun onSuccess(homeBeans: MutableList<HomeBean>?) {
                            Log.d(TAG, "✅ getHomeList success size=${homeBeans?.size ?: 0}")
                            val list = (homeBeans ?: mutableListOf()).map { hb ->
                                hashMapOf<String, Any?>(
                                    "homeId" to hb.homeId,
                                    "name" to hb.name,
                                    "geoName" to hb.geoName
                                )
                            }
                            mainHandler.post { result.success(list) }
                        }

                        override fun onError(errorCode: String?, error: String?) {
                            Log.e(TAG, "❌ getHomeList error code=$errorCode msg=$error")
                            mainHandler.post { result.error(errorCode ?: "HOME_LIST_FAILED", error ?: "queryHomeList failed", null) }
                        }
                    })
                }

                "ensureHome" -> {
                    val name = call.argument<String>("name") ?: "My Home"
                    val geoName = call.argument<String>("geoName") ?: "Oman"
                    val rooms = call.argument<List<String>>("rooms") ?: listOf("Living Room")

                    Log.d(TAG, "➡️ ensureHome(name=$name geo=$geoName rooms=${rooms.size})")

                    ThingHomeSdk.getHomeManagerInstance().queryHomeList(object : IThingGetHomeListCallback {
                        override fun onSuccess(homeBeans: MutableList<HomeBean>?) {
                            val existing = homeBeans?.firstOrNull()
                            if (existing != null) {
                                mainHandler.post {
                                    result.success(
                                        hashMapOf<String, Any?>(
                                            "homeId" to existing.homeId,
                                            "name" to existing.name,
                                            "geoName" to existing.geoName
                                        )
                                    )
                                }
                                return
                            }

                            ThingHomeSdk.getHomeManagerInstance().createHome(
                                name, 0.0, 0.0, geoName, rooms,
                                object : IThingHomeResultCallback {
                                    override fun onSuccess(bean: HomeBean?) {
                                        if (bean == null) {
                                            mainHandler.post { result.error("CREATE_HOME_FAILED", "HomeBean is null", null) }
                                            return
                                        }
                                        mainHandler.post {
                                            result.success(
                                                hashMapOf<String, Any?>(
                                                    "homeId" to bean.homeId,
                                                    "name" to bean.name,
                                                    "geoName" to bean.geoName
                                                )
                                            )
                                        }
                                    }

                                    override fun onError(errorCode: String?, errorMsg: String?) {
                                        mainHandler.post {
                                            result.error(errorCode ?: "CREATE_HOME_FAILED", errorMsg ?: "createHome failed", null)
                                        }
                                    }
                                }
                            )
                        }

                        override fun onError(errorCode: String?, error: String?) {
                            mainHandler.post { result.error(errorCode ?: "HOME_LIST_FAILED", error ?: "queryHomeList failed", null) }
                        }
                    })
                }

                // ---------------- BIZ CONTEXT ----------------
                "ensureBizContext" -> {
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()
                    if (!ThingHomeSdk.getUserInstance().isLogin) {
                        result.error("NOT_LOGGED_IN", "Login required before ensureBizContext", null)
                        return
                    }
                    ensureBizContext(homeId) { ok, err ->
                        if (ok) mainHandler.post { result.success(true) }
                        else mainHandler.post { result.error("ENSURE_BIZ_CONTEXT_FAILED", err ?: "unknown", null) }
                    }
                }

                // ---------------- BIZ UI ----------------
                "bizOpenAddDevice" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    if (!ThingHomeSdk.getUserInstance().isLogin) {
                        result.error("NOT_LOGGED_IN", "Login required", null)
                        return
                    }

                    ensureBizContext(homeId) { ok, err ->
                        if (!ok) {
                            mainHandler.post { result.error("ENSURE_BIZ_CONTEXT_FAILED", err ?: "unknown", null) }
                            return@ensureBizContext
                        }
                        BizBundleActivatorUi.openAddDevice(
                            activity = activity,
                            homeId = homeId,
                            onOk = { mainHandler.post { result.success(true) } },
                            onErr = { t -> mainHandler.post { result.error("ADD_DEVICE_UI_FAILED", t.message, null) } }
                        )
                    }
                }

                "bizOpenQrScan" -> {
                    val activity = currentActivity ?: run {
                        result.error("NO_ACTIVITY", "No foreground Activity available", null)
                        return
                    }
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()

                    if (!ThingHomeSdk.getUserInstance().isLogin) {
                        result.error("NOT_LOGGED_IN", "Login required", null)
                        return
                    }

                    ensureBizContext(homeId) { ok, err ->
                        if (!ok) {
                            mainHandler.post { result.error("ENSURE_BIZ_CONTEXT_FAILED", err ?: "unknown", null) }
                            return@ensureBizContext
                        }
                        mainHandler.post {
                            try {
                                val scanClazz = Class.forName("com.thingclips.smart.activator.scan.qrcode.ScanManager")
                                val instance = scanClazz.getDeclaredField("INSTANCE").get(null)

                                // Prefer openScan(Context, Bundle)
                                val openWithBundle = scanClazz.methods.firstOrNull { m ->
                                    m.name == "openScan" && m.parameterTypes.size == 2
                                }
                                if (openWithBundle != null) {
                                    val b = Bundle().apply {
                                        // best-effort extras
                                        putLong("homeId", homeId)
                                        putLong("relationId", homeId)
                                    }
                                    openWithBundle.invoke(instance, activity /* Context */, b)
                                    result.success(true)
                                    return@post
                                }

                                // Fallback openScan(Context)
                                val open = scanClazz.methods.firstOrNull { m ->
                                    m.name == "openScan" && m.parameterTypes.size == 1
                                } ?: throw NoSuchMethodException("ScanManager.openScan(Context/*,Bundle*/) not found")

                                open.invoke(instance, activity /* Context */)
                                result.success(true)
                            } catch (t: Throwable) {
                                Log.e(TAG, "bizOpenQrScan failed", t)
                                result.error("QR_SCAN_FAILED", t.message, null)
                            }
                        }
                    }
                }

                else -> result.notImplemented()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Bridge crash prevented", t)
            result.error("NATIVE_BRIDGE_ERROR", t.message, null)
        }
    }

    // ------------------------------------------------------------
    // Biz context helpers
    // ------------------------------------------------------------
    private fun ensureBizContext(homeId: Long, done: (Boolean, String?) -> Unit) {
        Log.d(TAG, "➡️ ensureBizContext(homeId=$homeId)")

        if (homeId <= 0L) {
            done(false, "BAD_HOME_ID")
            return
        }

        // warm home detail (async) then shift family/current home
        warmHomeDetail(homeId) { ok, nameOrErr ->
            if (!ok) {
                done(false, nameOrErr ?: "HOME_DETAIL_FAILED")
                return@warmHomeDetail
            }

            val homeName = (nameOrErr ?: "Home").ifBlank { "Home" }
            Log.d(TAG, "✅ warmHomeDetail OK homeId=$homeId name=$homeName")

            // ✅ This is the key piece for relationId/token issues in BizBundle QR flow (best-effort).
            BizBundleFamilyServiceImpl.setHome(homeId, homeName)

            // ✅ Also push home into activator manager if available (best-effort)
            setActivatorHomeIdBestEffort(homeId)

            // Optional: try to warm token inside activator module (best-effort)
            getActivatorTokenBestEffort(homeId) { tokenLen ->
                Log.d(TAG, "✅ ensureBizContext ready. tokenLen=$tokenLen")
                done(true, null)
            }
        }
    }

    private fun warmHomeDetail(homeId: Long, cb: (Boolean, String?) -> Unit) {
        try {
            // run on main thread to reduce weird SDK thread timing issues
            mainHandler.post {
                ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(object : IThingHomeResultCallback {
                    override fun onSuccess(bean: HomeBean?) {
                        cb(true, bean?.name ?: "")
                    }

                    override fun onError(errorCode: String?, errorMsg: String?) {
                        cb(false, "${errorCode ?: "HOME_DETAIL_FAILED"}: ${errorMsg ?: "getHomeDetail failed"}")
                    }
                })
            }
        } catch (t: Throwable) {
            cb(false, t.message)
        }
    }

    private fun setActivatorHomeIdBestEffort(homeId: Long) {
        // We discovered earlier the real manager in your APK build was:
        // com.thingclips.smart.activator.plug.mesosphere.ThingDeviceActivatorManager
        val mgrCandidates = listOf(
            "com.thingclips.smart.activator.plug.mesosphere.ThingDeviceActivatorManager",
            "com.tuya.smart.activator.plug.mesosphere.ThingDeviceActivatorManager"
        )

        for (cn in mgrCandidates) {
            try {
                val clz = Class.forName(cn)
                val instance = runCatching { clz.getDeclaredField("INSTANCE").get(null) }.getOrNull() ?: continue
                val m = clz.methods.firstOrNull { mm ->
                    mm.name == "setHomeId" && mm.parameterTypes.size == 1 &&
                        (mm.parameterTypes[0] == java.lang.Long.TYPE || mm.parameterTypes[0] == java.lang.Long::class.java)
                } ?: continue

                m.invoke(instance, homeId)
                Log.d(TAG, "✅ ActivatorManager.setHomeId($homeId) via $cn")
                return
            } catch (_: Throwable) {
                // try next
            }
        }

        Log.w(TAG, "⚠️ ActivatorManager.setHomeId not found (not fatal).")
    }

    private fun getActivatorTokenBestEffort(homeId: Long, done: (Int) -> Unit) {
        try {
            // ThingHomeSdk.getActivatorInstance()?.getActivatorToken(long, IThingActivatorGetToken)
            val getActInst = ThingHomeSdk::class.java.methods.firstOrNull { it.name == "getActivatorInstance" && it.parameterTypes.isEmpty() }
            val actInst = getActInst?.invoke(null)
            if (actInst == null) {
                done(0)
                return
            }

            val m = actInst.javaClass.methods.firstOrNull { mm ->
                mm.name == "getActivatorToken" && mm.parameterTypes.size == 2 &&
                    (mm.parameterTypes[0] == java.lang.Long.TYPE || mm.parameterTypes[0] == java.lang.Long::class.java) &&
                    mm.parameterTypes[1].isInterface
            } ?: run {
                done(0)
                return
            }

            val cbInterface = m.parameterTypes[1]
            val proxy = Proxy.newProxyInstance(cbInterface.classLoader, arrayOf(cbInterface)) { _, method, args ->
                // Most builds use onSuccess(String token) / onFailure(String code, String msg)
                if (method.name.equals("onSuccess", ignoreCase = true)) {
                    val token = args?.firstOrNull() as? String
                    Log.d(TAG, "✅ getActivatorToken success len=${token?.length ?: 0}")
                    done(token?.length ?: 0)
                    return@newProxyInstance null
                }
                if (method.name.contains("onFail", ignoreCase = true) || method.name.equals("onError", ignoreCase = true)) {
                    Log.w(TAG, "⚠️ getActivatorToken failed: ${args?.toList()}")
                    done(0)
                    return@newProxyInstance null
                }
                null
            }

            Log.d(TAG, "➡️ Invoking getActivatorToken($homeId, cb) on ${actInst.javaClass.name}")
            m.invoke(actInst, homeId, proxy)
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ getActivatorToken best-effort failed: ${t.message}")
            done(0)
        }
    }

    // ------------------------------------------------------------
    // Wrapper hooks (best effort)
    // ------------------------------------------------------------
    private fun tryCallWrapperOnLogin() {
        val candidates = listOf(
            "com.thingclips.smart.bizbundle.TuyaWrapper",
            "com.thingclips.smart.android.bizbundle.TuyaWrapper",
            "com.tuya.smart.bizbundle.TuyaWrapper",
            "com.tuya.smart.android.bizbundle.TuyaWrapper"
        )
        try {
            val clz = candidates.firstNotNullOfOrNull { cn -> runCatching { Class.forName(cn) }.getOrNull() } ?: return
            val m = clz.methods.firstOrNull { it.name == "onLogin" && it.parameterTypes.isEmpty() } ?: return
            m.invoke(null)
            Log.d(TAG, "✅ TuyaWrapper.onLogin() called")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ TuyaWrapper.onLogin failed: ${t.message}")
        }
    }

    private fun tryCallWrapperOnLogout() {
        val candidates = listOf(
            "com.thingclips.smart.bizbundle.TuyaWrapper",
            "com.thingclips.smart.android.bizbundle.TuyaWrapper",
            "com.tuya.smart.bizbundle.TuyaWrapper",
            "com.tuya.smart.android.bizbundle.TuyaWrapper"
        )
        try {
            val clz = candidates.firstNotNullOfOrNull { cn -> runCatching { Class.forName(cn) }.getOrNull() } ?: return
            val m = clz.methods.firstOrNull { it.name == "onLogout" && it.parameterTypes.size == 1 } ?: return
            val ctx = currentActivity?.applicationContext ?: return
            m.invoke(null, ctx)
            Log.d(TAG, "✅ TuyaWrapper.onLogout(ctx) called")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ TuyaWrapper.onLogout failed: ${t.message}")
        }
    }
}