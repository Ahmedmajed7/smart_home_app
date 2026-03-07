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
import com.thingclips.smart.home.sdk.builder.ThingGwSubDevActivatorBuilder
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.sdk.api.IResultCallback
import com.thingclips.smart.sdk.api.IThingSmartActivatorListener
import com.thingclips.smart.sdk.bean.DeviceBean
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy

object TuyaBridge {

    private const val TAG = "TuyaBridge"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activityRef: WeakReference<Activity>? = null
    private var channel: MethodChannel? = null

    private var gwSubActivator: Any? = null

    @JvmStatic
    fun bindActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    @JvmStatic
    fun unbindActivity(activity: Activity) {
        if (activityRef?.get() === activity) activityRef = null
    }

    private val currentActivity: Activity?
        get() = activityRef?.get()

    @JvmStatic
    fun setChannel(ch: MethodChannel) {
        channel = ch
    }

    private fun emit(event: String, args: Any? = null) {
        try {
            channel?.invokeMethod(event, args)
        } catch (_: Throwable) {
        }
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
                        countryCode,
                        email,
                        password,
                        object : ILoginCallback {
                            override fun onSuccess(user: User?) {
                                Log.d(TAG, "✅ loginByEmail success user=${user?.uid}")
                                tryCallWrapperOnLogin()
                                mainHandler.post { result.success(true) }
                            }

                            override fun onError(code: String?, error: String?) {
                                Log.e(TAG, "❌ loginByEmail error code=$code msg=$error")
                                mainHandler.post {
                                    result.error(
                                        code ?: "LOGIN_FAILED",
                                        error ?: "login failed",
                                        null
                                    )
                                }
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
                        email,
                        "",
                        countryCode,
                        type,
                        object : IResultCallback {
                            override fun onSuccess() {
                                Log.d(TAG, "✅ sendEmailCode success")
                                mainHandler.post { result.success(true) }
                            }

                            override fun onError(code: String?, error: String?) {
                                Log.e(TAG, "❌ sendEmailCode error code=$code msg=$error")
                                mainHandler.post {
                                    result.error(
                                        code ?: "SEND_CODE_FAILED",
                                        error ?: "send code failed",
                                        null
                                    )
                                }
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
                        countryCode,
                        email,
                        password,
                        code,
                        object : IRegisterCallback {
                            override fun onSuccess(user: User?) {
                                Log.d(TAG, "✅ registerEmail success user=${user?.uid}")
                                tryCallWrapperOnLogin()
                                mainHandler.post { result.success(true) }
                            }

                            override fun onError(code: String?, error: String?) {
                                Log.e(TAG, "❌ registerEmail error code=$code msg=$error")
                                mainHandler.post {
                                    result.error(
                                        code ?: "REGISTER_FAILED",
                                        error ?: "register failed",
                                        null
                                    )
                                }
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
                            mainHandler.post {
                                result.error(
                                    code ?: "LOGOUT_FAILED",
                                    error ?: "logout failed",
                                    null
                                )
                            }
                        }
                    })
                }

                // ---------------- HOME ----------------
                "getHomeList" -> {
                    Log.d(TAG, "➡️ getHomeList()")
                    ThingHomeSdk.getHomeManagerInstance().queryHomeList(object :
                        IThingGetHomeListCallback {
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
                            mainHandler.post {
                                result.error(
                                    errorCode ?: "HOME_LIST_FAILED",
                                    error ?: "queryHomeList failed",
                                    null
                                )
                            }
                        }
                    })
                }

                "getHomeDevices" -> {
                    val homeId = (call.argument<Number>("homeId") ?: 0).toLong()
                    if (homeId <= 0L) {
                        result.error("BAD_HOME_ID", "Invalid homeId", null)
                        return
                    }

                    Log.d(TAG, "➡️ getHomeDevices(homeId=$homeId)")

                    ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(object :
                        IThingHomeResultCallback {
                        override fun onSuccess(bean: HomeBean?) {
                            try {
                                val devices = extractDevicesFromHome(bean)
                                Log.d(TAG, "✅ getHomeDevices success size=${devices.size}")
                                mainHandler.post { result.success(devices) }
                            } catch (t: Throwable) {
                                Log.e(TAG, "❌ getHomeDevices parse failed", t)
                                mainHandler.post {
                                    result.error(
                                        "GET_HOME_DEVICES_FAILED",
                                        t.message ?: "parse failed",
                                        null
                                    )
                                }
                            }
                        }

                        override fun onError(errorCode: String?, errorMsg: String?) {
                            Log.e(TAG, "❌ getHomeDevices error code=$errorCode msg=$errorMsg")
                            mainHandler.post {
                                result.error(
                                    errorCode ?: "GET_HOME_DEVICES_FAILED",
                                    errorMsg ?: "getHomeDetail failed",
                                    null
                                )
                            }
                        }
                    })
                }

                "ensureHome" -> {
                    val name = call.argument<String>("name") ?: "My Home"
                    val geoName = call.argument<String>("geoName") ?: "Oman"
                    val rooms = call.argument<List<String>>("rooms") ?: listOf("Living Room")

                    Log.d(TAG, "➡️ ensureHome(name=$name geo=$geoName rooms=${rooms.size})")

                    ThingHomeSdk.getHomeManagerInstance().queryHomeList(object :
                        IThingGetHomeListCallback {
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
                                name,
                                0.0,
                                0.0,
                                geoName,
                                rooms,
                                object : IThingHomeResultCallback {
                                    override fun onSuccess(bean: HomeBean?) {
                                        if (bean == null) {
                                            mainHandler.post {
                                                result.error(
                                                    "CREATE_HOME_FAILED",
                                                    "HomeBean is null",
                                                    null
                                                )
                                            }
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
                                            result.error(
                                                errorCode ?: "CREATE_HOME_FAILED",
                                                errorMsg ?: "createHome failed",
                                                null
                                            )
                                        }
                                    }
                                }
                            )
                        }

                        override fun onError(errorCode: String?, error: String?) {
                            mainHandler.post {
                                result.error(
                                    errorCode ?: "HOME_LIST_FAILED",
                                    error ?: "queryHomeList failed",
                                    null
                                )
                            }
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
                        else mainHandler.post {
                            result.error(
                                "ENSURE_BIZ_CONTEXT_FAILED",
                                err ?: "unknown",
                                null
                            )
                        }
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
                            mainHandler.post {
                                result.error(
                                    "ENSURE_BIZ_CONTEXT_FAILED",
                                    err ?: "unknown",
                                    null
                                )
                            }
                            return@ensureBizContext
                        }
                        BizBundleActivatorUi.openAddDevice(
                            activity = activity,
                            homeId = homeId,
                            onOk = { mainHandler.post { result.success(true) } },
                            onErr = { t ->
                                mainHandler.post {
                                    result.error(
                                        "ADD_DEVICE_UI_FAILED",
                                        t.message,
                                        null
                                    )
                                }
                            }
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
                            mainHandler.post {
                                result.error(
                                    "ENSURE_BIZ_CONTEXT_FAILED",
                                    err ?: "unknown",
                                    null
                                )
                            }
                            return@ensureBizContext
                        }
                        mainHandler.post {
                            try {
                                val scanClazz =
                                    Class.forName("com.thingclips.smart.activator.scan.qrcode.ScanManager")
                                val instance = scanClazz.getDeclaredField("INSTANCE").get(null)

                                val openWithBundle = scanClazz.methods.firstOrNull { m ->
                                    m.name == "openScan" && m.parameterTypes.size == 2
                                }
                                if (openWithBundle != null) {
                                    val b = Bundle().apply {
                                        putLong("homeId", homeId)
                                        putLong("relationId", homeId)
                                    }
                                    openWithBundle.invoke(instance, activity, b)
                                    result.success(true)
                                    return@post
                                }

                                val open = scanClazz.methods.firstOrNull { m ->
                                    m.name == "openScan" && m.parameterTypes.size == 1
                                } ?: throw NoSuchMethodException(
                                    "ScanManager.openScan(Context/*,Bundle*/) not found"
                                )

                                open.invoke(instance, activity)
                                result.success(true)
                            } catch (t: Throwable) {
                                Log.e(TAG, "bizOpenQrScan failed", t)
                                result.error("QR_SCAN_FAILED", t.message, null)
                            }
                        }
                    }
                }

                // ---------------- GATEWAY SUB-DEVICE PAIRING ----------------
                "startGatewaySubDevicePairing" -> {
                    val gatewayDevId = call.argument<String>("gatewayDevId")?.trim().orEmpty()
                    val timeoutSeconds =
                        (call.argument<Number>("timeoutSeconds") ?: 120).toInt()

                    if (gatewayDevId.isEmpty()) {
                        result.error("BAD_GATEWAY_ID", "gatewayDevId is empty", null)
                        return
                    }

                    if (!ThingHomeSdk.getUserInstance().isLogin) {
                        result.error("NOT_LOGGED_IN", "Login required", null)
                        return
                    }

                    Log.d(
                        TAG,
                        "➡️ startGatewaySubDevicePairing(gatewayDevId=$gatewayDevId timeoutSeconds=$timeoutSeconds)"
                    )

                    mainHandler.post {
                        try {
                            stopGatewaySubDevicePairingBestEffort()

                            val builder = ThingGwSubDevActivatorBuilder()
                                .setDevId(gatewayDevId)
                                .setTimeOut(timeoutSeconds)
                                .setListener(object : IThingSmartActivatorListener {
                                    override fun onError(errorCode: String?, errorMsg: String?) {
                                        Log.e(
                                            TAG,
                                            "❌ Gateway sub-device pair error code=$errorCode msg=$errorMsg"
                                        )
                                        stopGatewaySubDevicePairingBestEffort()
                                        mainHandler.post {
                                            result.error(
                                                errorCode ?: "SUB_DEVICE_PAIR_FAILED",
                                                errorMsg ?: "Sub-device pairing failed",
                                                null
                                            )
                                        }
                                    }

                                    override fun onActiveSuccess(devResp: DeviceBean?) {
                                        Log.d(
                                            TAG,
                                            "✅ Gateway sub-device pair success devId=${devResp?.devId}"
                                        )
                                        stopGatewaySubDevicePairingBestEffort()
                                        val map = if (devResp != null) {
                                            deviceToMap(devResp)
                                        } else {
                                            hashMapOf<String, Any?>(
                                                "devId" to "",
                                                "name" to "Sub-device",
                                                "iconUrl" to null,
                                                "isOnline" to true,
                                                "isGateway" to false,
                                                "parentId" to gatewayDevId
                                            )
                                        }
                                        mainHandler.post { result.success(map) }
                                    }

                                    override fun onStep(step: String?, data: Any?) {
                                        Log.d(
                                            TAG,
                                            "ℹ️ Gateway sub-device pair step=$step data=${data?.javaClass?.name}"
                                        )
                                    }
                                })

                            val activator = ThingHomeSdk.getActivatorInstance()
                                .newGwSubDevActivator(builder)

                            gwSubActivator = activator
                            val startMethod = activator.javaClass.methods.firstOrNull {
                                it.name == "start" && it.parameterTypes.isEmpty()
                            } ?: throw NoSuchMethodException("newGwSubDevActivator(...).start() not found")

                            startMethod.invoke(activator)
                            Log.d(TAG, "✅ Gateway sub-device pairing started")
                        } catch (t: Throwable) {
                            Log.e(TAG, "❌ startGatewaySubDevicePairing failed", t)
                            stopGatewaySubDevicePairingBestEffort()
                            result.error("SUB_DEVICE_PAIR_FAILED", t.message, null)
                        }
                    }
                }

                "stopGatewaySubDevicePairing" -> {
                    stopGatewaySubDevicePairingBestEffort()
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Bridge crash prevented", t)
            result.error("NATIVE_BRIDGE_ERROR", t.message, null)
        }
    }

    // ------------------------------------------------------------
    // Home device parsing helpers
    // ------------------------------------------------------------
    private fun extractDevicesFromHome(homeBean: HomeBean?): List<HashMap<String, Any?>> {
        if (homeBean == null) return emptyList()

        val rawDevices = mutableListOf<Any>()

        try {
            val getter = homeBean.javaClass.methods.firstOrNull {
                it.name == "getDeviceList" && it.parameterTypes.isEmpty()
            }
            val value = getter?.invoke(homeBean)
            if (value is Collection<*>) {
                rawDevices.addAll(value.filterNotNull())
            }
        } catch (_: Throwable) {
        }

        if (rawDevices.isEmpty()) {
            try {
                val field = homeBean.javaClass.declaredFields.firstOrNull { it.name == "deviceList" }
                field?.isAccessible = true
                val value = field?.get(homeBean)
                if (value is Collection<*>) {
                    rawDevices.addAll(value.filterNotNull())
                }
            } catch (_: Throwable) {
            }
        }

        if (rawDevices.isEmpty()) {
            try {
                val getter = homeBean.javaClass.methods.firstOrNull {
                    it.name == "getDeviceMap" && it.parameterTypes.isEmpty()
                }
                val value = getter?.invoke(homeBean)
                if (value is Map<*, *>) {
                    rawDevices.addAll(value.values.filterNotNull())
                }
            } catch (_: Throwable) {
            }
        }

        return rawDevices.mapNotNull { dev ->
            try {
                deviceToMap(dev)
            } catch (t: Throwable) {
                Log.w(TAG, "⚠️ deviceToMap skipped one item: ${t.message}")
                null
            }
        }
    }

    private fun deviceToMap(device: Any): HashMap<String, Any?> {
        val devId = callString(device, "getDevId", "getDevIdStr")
        val name = callString(device, "getName", "getDevName").ifBlank {
            if (devId.isNotBlank()) devId else "Device"
        }
        val iconUrl = callNullableString(device, "getIconUrl", "getIcon")
        val isOnline = callBoolean(device, "getIsOnline", "isOnline", "getOnline")
        val parentId = callNullableString(device, "getParentId", "getGwId", "getParentDevId")
        val nodeId = callNullableString(device, "getNodeId", "getLocalId")
        val category = callNullableString(device, "getCategory")
        val explicitGateway = callNullableBoolean(device, "getIsGateway", "isGateway", "getIsGw", "isGw")

        val computedGateway = explicitGateway ?: run {
            val cat = category?.lowercase().orEmpty()
            val lowerName = name.lowercase()
            val hasGatewayWord = lowerName.contains("gateway") ||
                lowerName.contains("hub") ||
                lowerName.contains("panel")
            val categoryLooksGateway =
                cat.contains("wg") || cat.contains("gateway") || cat.contains("hub")
            val nodeLooksGateway = parentId.isNullOrBlank() && !nodeId.isNullOrBlank() && hasGatewayWord
            hasGatewayWord || categoryLooksGateway || nodeLooksGateway
        }

        return hashMapOf<String, Any?>(
            "devId" to devId,
            "name" to name,
            "iconUrl" to iconUrl,
            "isOnline" to isOnline,
            "isGateway" to computedGateway,
            "parentId" to parentId
        )
    }

    private fun callString(target: Any, vararg names: String): String {
        return callNullableString(target, *names) ?: ""
    }

    private fun callNullableString(target: Any, vararg names: String): String? {
        for (name in names) {
            try {
                val method = target.javaClass.methods.firstOrNull {
                    it.name == name && it.parameterTypes.isEmpty()
                } ?: continue
                val value = method.invoke(target)?.toString()?.trim()
                if (!value.isNullOrEmpty() && value != "null") return value
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun callBoolean(target: Any, vararg names: String): Boolean {
        return callNullableBoolean(target, *names) ?: false
    }

    private fun callNullableBoolean(target: Any, vararg names: String): Boolean? {
        for (name in names) {
            try {
                val method = target.javaClass.methods.firstOrNull {
                    it.name == name && it.parameterTypes.isEmpty()
                } ?: continue
                val value = method.invoke(target)
                when (value) {
                    is Boolean -> return value
                    is Number -> return value.toInt() != 0
                    is String -> {
                        if (value.equals("true", true)) return true
                        if (value.equals("false", true)) return false
                        value.toIntOrNull()?.let { return it != 0 }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun stopGatewaySubDevicePairingBestEffort() {
        val activator = gwSubActivator ?: return
        try {
            activator.javaClass.methods.firstOrNull {
                it.name == "stop" && it.parameterTypes.isEmpty()
            }?.invoke(activator)
        } catch (_: Throwable) {
        }

        try {
            activator.javaClass.methods.firstOrNull {
                it.name.equals("onDestory", ignoreCase = true) && it.parameterTypes.isEmpty()
            }?.invoke(activator)
        } catch (_: Throwable) {
        }

        gwSubActivator = null
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

        warmHomeDetail(homeId) { ok, nameOrErr ->
            if (!ok) {
                done(false, nameOrErr ?: "HOME_DETAIL_FAILED")
                return@warmHomeDetail
            }

            val homeName = (nameOrErr ?: "Home").ifBlank { "Home" }
            Log.d(TAG, "✅ warmHomeDetail OK homeId=$homeId name=$homeName")

            BizBundleFamilyServiceImpl.setHome(homeId, homeName)
            setActivatorHomeIdBestEffort(homeId)

            getActivatorTokenBestEffort(homeId) { tokenLen ->
                Log.d(TAG, "✅ ensureBizContext ready. tokenLen=$tokenLen")
                done(true, null)
            }
        }
    }

    private fun warmHomeDetail(homeId: Long, cb: (Boolean, String?) -> Unit) {
        try {
            mainHandler.post {
                ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(object : IThingHomeResultCallback {
                    override fun onSuccess(bean: HomeBean?) {
                        cb(true, bean?.name ?: "")
                    }

                    override fun onError(errorCode: String?, errorMsg: String?) {
                        cb(
                            false,
                            "${errorCode ?: "HOME_DETAIL_FAILED"}: ${errorMsg ?: "getHomeDetail failed"}"
                        )
                    }
                })
            }
        } catch (t: Throwable) {
            cb(false, t.message)
        }
    }

    private fun setActivatorHomeIdBestEffort(homeId: Long) {
        val mgrCandidates = listOf(
            "com.thingclips.smart.activator.plug.mesosphere.ThingDeviceActivatorManager",
            "com.tuya.smart.activator.plug.mesosphere.ThingDeviceActivatorManager"
        )

        for (cn in mgrCandidates) {
            try {
                val clz = Class.forName(cn)
                val instance =
                    runCatching { clz.getDeclaredField("INSTANCE").get(null) }.getOrNull()
                        ?: continue
                val m = clz.methods.firstOrNull { mm ->
                    mm.name == "setHomeId" &&
                        mm.parameterTypes.size == 1 &&
                        (mm.parameterTypes[0] == java.lang.Long.TYPE ||
                            mm.parameterTypes[0] == java.lang.Long::class.java)
                } ?: continue

                m.invoke(instance, homeId)
                Log.d(TAG, "✅ ActivatorManager.setHomeId($homeId) via $cn")
                return
            } catch (_: Throwable) {
            }
        }

        Log.w(TAG, "⚠️ ActivatorManager.setHomeId not found (not fatal).")
    }

    private fun getActivatorTokenBestEffort(homeId: Long, done: (Int) -> Unit) {
        try {
            val getActInst = ThingHomeSdk::class.java.methods.firstOrNull {
                it.name == "getActivatorInstance" && it.parameterTypes.isEmpty()
            }
            val actInst = getActInst?.invoke(null)
            if (actInst == null) {
                done(0)
                return
            }

            val m = actInst.javaClass.methods.firstOrNull { mm ->
                mm.name == "getActivatorToken" &&
                    mm.parameterTypes.size == 2 &&
                    (mm.parameterTypes[0] == java.lang.Long.TYPE ||
                        mm.parameterTypes[0] == java.lang.Long::class.java) &&
                    mm.parameterTypes[1].isInterface
            } ?: run {
                done(0)
                return
            }

            val cbInterface = m.parameterTypes[1]
            val proxy =
                Proxy.newProxyInstance(cbInterface.classLoader, arrayOf(cbInterface)) { _, method, args ->
                    if (method.name.equals("onSuccess", ignoreCase = true)) {
                        val token = args?.firstOrNull() as? String
                        Log.d(TAG, "✅ getActivatorToken success len=${token?.length ?: 0}")
                        done(token?.length ?: 0)
                        return@newProxyInstance null
                    }
                    if (
                        method.name.contains("onFail", ignoreCase = true) ||
                        method.name.equals("onError", ignoreCase = true)
                    ) {
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
            val clz =
                candidates.firstNotNullOfOrNull { cn ->
                    runCatching { Class.forName(cn) }.getOrNull()
                } ?: return
            val m = clz.methods.firstOrNull {
                it.name == "onLogin" && it.parameterTypes.isEmpty()
            } ?: return
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
            val clz =
                candidates.firstNotNullOfOrNull { cn ->
                    runCatching { Class.forName(cn) }.getOrNull()
                } ?: return
            val m = clz.methods.firstOrNull {
                it.name == "onLogout" && it.parameterTypes.size == 1
            } ?: return
            val ctx = currentActivity?.applicationContext ?: return
            m.invoke(null, ctx)
            Log.d(TAG, "✅ TuyaWrapper.onLogout(ctx) called")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ TuyaWrapper.onLogout failed: ${t.message}")
        }
    }
}