package com.example.alrawi_app

import android.util.Log
import com.thingclips.smart.commonbiz.bizbundle.family.api.AbsBizBundleFamilyService

object BizBundleFamilyServiceImpl : AbsBizBundleFamilyService() {

    private const val TAG = "BizFamilyService"

    @Volatile
    private var currentHomeId: Long = 0L

    @Volatile
    private var currentHomeName: String = ""

    fun bootstrap() {
        Log.d(TAG, "➡️ bootstrap(currentHomeId=$currentHomeId, currentHomeName=$currentHomeName)")
    }

    override fun getCurrentHomeId(): Long = currentHomeId

    fun getCurrentFamilyId(): Long = currentHomeId
    fun getCurrentHomeName(): String = currentHomeName
    fun getCurrentFamilyName(): String = currentHomeName

    override fun shiftCurrentFamily(familyId: Long, curName: String?) {
        super.shiftCurrentFamily(familyId, curName)
        currentHomeId = familyId
        currentHomeName = curName ?: ""
        Log.d(TAG, "✅ shiftCurrentFamily(familyId=$familyId, curName=$currentHomeName)")
    }

    fun setCurrentHomeId(homeId: Long) {
        currentHomeId = homeId
        Log.d(TAG, "✅ setCurrentHomeId(homeId=$homeId)")
    }

    fun setHome(homeId: Long, homeName: String) {
        currentHomeId = homeId
        currentHomeName = homeName
        Log.d(TAG, "➡️ setHome(homeId=$homeId, homeName=$homeName)")
    }
}