package com.mansaketh.chitta.intake

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.mansaketh.chitta.core.intake.PingContract

/**
 * The door Ping uses to hand over a new bank SMS while Chitta is closed. It does no parsing and no database work: it checks who is
 * calling and writes the small JSON text Ping sent into files/ping_inbox/, one file per SMS. [IntakeStore] reads that folder the next
 * time Chitta opens, saves what it is sure about and puts the rest in the review list.
 *
 * The caller must be package com.ping.sms AND signed with Ping's pinned certificate (PingContract). The caller package is filled in by
 * the system, so it cannot be faked in the call. A port of the old app's PingInboxProvider.java.
 */
class PingInboxProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        fun fail(why: String) = Bundle().apply { putBoolean("ok", false); putString("error", why) }
        if (method != METHOD_ENQUEUE) return fail("unknown method")
        val caller = callingPackage
        if (caller != PingContract.PACKAGE) return fail("caller not allowed")
        if (arg == null || arg.length > PingContract.MAX_JSON_CHARS || !arg.trim().startsWith("{")) return fail("nothing usable was sent")
        val context = context ?: return fail("no context")
        if (!callerHasPinnedCertificate(context, caller)) return fail("caller not allowed")
        return try {
            IntakeStore.writeToInbox(context, arg)
            Bundle().apply { putBoolean("ok", true) }
        } catch (e: Throwable) {
            fail(e.message ?: "could not save the message")
        }
    }

    // The provider has no tables: everything goes through call().
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        const val METHOD_ENQUEUE = "enqueue"

        /** True when [pkg] is signed only with Ping's pinned certificate. Cannot check = not allowed. */
        @Suppress("DEPRECATION")
        fun callerHasPinnedCertificate(context: Context, pkg: String): Boolean = try {
            val pm = context.packageManager
            val signers = if (Build.VERSION.SDK_INT >= 28) {
                val signing = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                if (signing == null) null else (if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory)
            } else {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
            }
            PingContract.allPinned(signers?.map { it.toByteArray() })
        } catch (e: Throwable) {
            false
        }
    }
}
