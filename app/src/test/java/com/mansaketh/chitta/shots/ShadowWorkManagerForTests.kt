package com.mansaketh.chitta.shots

import android.content.Context
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** The app turns off WorkManager's automatic start-up, so a test starts the test WorkManager the first time anything asks for it. */
@Implements(className = "androidx.work.WorkManager\$Companion")
class ShadowWorkManagerForTests {
    @Implementation
    fun getInstance(context: Context): WorkManager {
        return try { WorkManagerImpl.getInstance(context) } catch (e: IllegalStateException) {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            WorkManagerImpl.getInstance(context)
        }
    }
}
