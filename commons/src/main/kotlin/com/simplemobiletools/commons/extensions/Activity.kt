package com.simplemobiletools.commons.extensions

import android.app.Activity
import android.app.TimePickerDialog
import android.content.*
import android.content.Intent.EXTRA_STREAM
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.TransactionTooLargeException
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.text.Html
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.auth.AuthPromptCallback
import androidx.biometric.auth.AuthPromptHost
import androidx.biometric.auth.Class2BiometricAuthPrompt
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import com.simplemobiletools.commons.R
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.dialogs.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.*
import com.simplemobiletools.commons.views.MyTextView
import kotlinx.android.synthetic.main.dialog_title.view.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.HashMap

fun AppCompatActivity.updateActionBarTitle(text: String, color: Int = baseConfig.primaryColor) {
    supportActionBar?.title = Html.fromHtml("<font color='${color.getContrastColor().toHex()}'>$text</font>")
}

fun AppCompatActivity.updateActionBarSubtitle(text: String) {
    supportActionBar?.subtitle = Html.fromHtml("<font color='${baseConfig.primaryColor.getContrastColor().toHex()}'>$text</font>")
}

fun Activity.appLaunched(appId: String) {
    baseConfig.internalStoragePath = getInternalStoragePath()
    updateSDCardPath()
    baseConfig.appId = appId
    if (baseConfig.appRunCount == 0) {
        baseConfig.wasOrangeIconChecked = true
        checkAppIconColor()
    } else if (!baseConfig.wasOrangeIconChecked) {
        baseConfig.wasOrangeIconChecked = true
        val primaryColor = resources.getColor(R.color.color_primary)
        if (baseConfig.appIconColor != primaryColor) {
            getAppIconColors().forEachIndexed { index, color ->
                toggleAppIconColor(appId, index, color, false)
            }

            val defaultClassName = "${baseConfig.appId.removeSuffix(".debug")}.activities.SplashActivity"
            packageManager.setComponentEnabledSetting(
                ComponentName(baseConfig.appId, defaultClassName),
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP
            )

            val orangeClassName = "${baseConfig.appId.removeSuffix(".debug")}.activities.SplashActivity.Orange"
            packageManager.setComponentEnabledSetting(
                ComponentName(baseConfig.appId, orangeClassName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            baseConfig.appIconColor = primaryColor
            baseConfig.lastIconColor = primaryColor
        }
    }

    baseConfig.appRunCount++
    if (baseConfig.appRunCount % 30 == 0 && !isAProApp()) {
        showDonateOrUpgradeDialog()
    }

    if (baseConfig.appRunCount % 40 == 0 && !baseConfig.wasAppRated) {
        RateStarsDialog(this)
    }

    if (baseConfig.navigationBarColor == INVALID_NAVIGATION_BAR_COLOR && (window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN == 0)) {
        baseConfig.defaultNavigationBarColor = window.navigationBarColor
        baseConfig.navigationBarColor = window.navigationBarColor
    }
}

fun Activity.showDonateOrUpgradeDialog() {
    if (getCanAppBeUpgraded()) {
        UpgradeToProDialog(this)
    } else if (!isOrWasThankYouInstalled()) {
        DonateDialog(this)
    }
}

fun Activity.isAppInstalledOnSDCard(): Boolean = try {
    val applicationInfo = packageManager.getPackageInfo(packageName, 0).applicationInfo
    (applicationInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE
} catch (e: Exception) {
    false
}

fun BaseSimpleActivity.isShowingSAFDialog(path: String): Boolean {
    return if ((!isRPlus() && isPathOnSD(path) && !isSDCardSetAsDefaultStorage() && (baseConfig.sdTreeUri.isEmpty() || !hasProperStoredTreeUri(false)))) {
        runOnUiThread {
            if (!isDestroyed && !isFinishing) {
                WritePermissionDialog(this, false) {
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        putExtra("android.content.extra.SHOW_ADVANCED", true)
                        try {
                            startActivityForResult(this, OPEN_DOCUMENT_TREE_SD)
                            checkedDocumentPath = path
                            return@apply
                        } catch (e: Exception) {
                            type = "*/*"
                        }

                        try {
                            startActivityForResult(this, OPEN_DOCUMENT_TREE_SD)
                            checkedDocumentPath = path
                        } catch (e: Exception) {
                            toast(R.string.unknown_error_occurred)
                        }
                    }
                }
            }
        }
        true
    } else {
        false
    }
}

fun BaseSimpleActivity.isShowingAndroidSAFDialog(path: String): Boolean {
    return if (isRestrictedSAFOnlyRoot(path) && (getAndroidTreeUri(path).isEmpty() || !hasProperStoredAndroidTreeUri(path))) {
        runOnUiThread {
            if (!isDestroyed && !isFinishing) {
                ConfirmationAdvancedDialog(this, "", R.string.confirm_storage_access_android_text, R.string.ok, R.string.cancel) { success ->
                    if (success) {
                        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            putExtra("android.content.extra.SHOW_ADVANCED", true)
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, createAndroidDataOrObbUri(path))
                            try {
                                startActivityForResult(this, OPEN_DOCUMENT_TREE_FOR_ANDROID_DATA_OR_OBB)
                                checkedDocumentPath = path
                                return@apply
                            } catch (e: Exception) {
                                type = "*/*"
                            }

                            try {
                                startActivityForResult(this, OPEN_DOCUMENT_TREE_FOR_ANDROID_DATA_OR_OBB)
                                checkedDocumentPath = path
                            } catch (e: Exception) {
                                toast(R.string.unknown_error_occurred)
                            }
                        }
                    }
                }
            }
        }
        true
    } else {
        false
    }
}

fun BaseSimpleActivity.isShowingOTGDialog(path: String): Boolean {
    return if (!isRPlus() && isPathOnOTG(path) && (baseConfig.OTGTreeUri.isEmpty() || !hasProperStoredTreeUri(true))) {
        showOTGPermissionDialog(path)
        true
    } else {
        false
    }
}

fun BaseSimpleActivity.showOTGPermissionDialog(path: String) {
    runOnUiThread {
        if (!isDestroyed && !isFinishing) {
            WritePermissionDialog(this, true) {
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    try {
                        startActivityForResult(this, OPEN_DOCUMENT_TREE_OTG)
                        checkedDocumentPath = path
                        return@apply
                    } catch (e: Exception) {
                        type = "*/*"
                    }

                    try {
                        startActivityForResult(this, OPEN_DOCUMENT_TREE_OTG)
                        checkedDocumentPath = path
                    } catch (e: Exception) {
                        toast(R.string.unknown_error_occurred)
                    }
                }
            }
        }
    }
}

fun Activity.launchPurchaseThankYouIntent() {
    try {
        launchViewIntent("market://details?id=com.simplemobiletools.thankyou")
    } catch (ignored: Exception) {
        launchViewIntent(getString(R.string.thank_you_url))
    }
}

fun Activity.launchUpgradeToProIntent() {
    try {
        launchViewIntent("market://details?id=${baseConfig.appId.removeSuffix(".debug")}.pro")
    } catch (ignored: Exception) {
        launchViewIntent(getStoreUrl())
    }
}

fun Activity.launchViewIntent(id: Int) = launchViewIntent(getString(id))

fun Activity.launchViewIntent(url: String) {
    ensureBackgroundThread {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            launchActivityIntent(this)
        }
    }
}

fun Activity.redirectToRateUs() {
    try {
        launchViewIntent("market://details?id=${packageName.removeSuffix(".debug")}")
    } catch (ignored: ActivityNotFoundException) {
        launchViewIntent(getStoreUrl())
    }
}

fun Activity.sharePathIntent(path: String, applicationId: String) {
    ensureBackgroundThread {
        val newUri = getFinalUriFromPath(path, applicationId) ?: return@ensureBackgroundThread
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(EXTRA_STREAM, newUri)
            type = getUriMimeType(path, newUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            try {
                startActivity(Intent.createChooser(this, getString(R.string.share_via)))
            } catch (e: ActivityNotFoundException) {
                toast(R.string.no_app_found)
            } catch (e: RuntimeException) {
                if (e.cause is TransactionTooLargeException) {
                    toast(R.string.maximum_share_reached)
                } else {
                    showErrorToast(e)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}

fun Activity.sharePathsIntent(paths: List<String>, applicationId: String) {
    ensureBackgroundThread {
        if (paths.size == 1) {
            sharePathIntent(paths.first(), applicationId)
        } else {
            val uriPaths = ArrayList<String>()
            val newUris = paths.map {
                val uri = getFinalUriFromPath(it, applicationId) ?: return@ensureBackgroundThread
                uriPaths.add(uri.path!!)
                uri
            } as ArrayList<Uri>

            var mimeType = uriPaths.getMimeType()
            if (mimeType.isEmpty() || mimeType == "*/*") {
                mimeType = paths.getMimeType()
            }

            Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putParcelableArrayListExtra(EXTRA_STREAM, newUris)

                try {
                    startActivity(Intent.createChooser(this, getString(R.string.share_via)))
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.no_app_found)
                } catch (e: RuntimeException) {
                    if (e.cause is TransactionTooLargeException) {
                        toast(R.string.maximum_share_reached)
                    } else {
                        showErrorToast(e)
                    }
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }
}

fun Activity.shareTextIntent(text: String) {
    ensureBackgroundThread {
        Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)

            try {
                startActivity(Intent.createChooser(this, getString(R.string.share_via)))
            } catch (e: ActivityNotFoundException) {
                toast(R.string.no_app_found)
            } catch (e: RuntimeException) {
                if (e.cause is TransactionTooLargeException) {
                    toast(R.string.maximum_share_reached)
                } else {
                    showErrorToast(e)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}

fun Activity.setAsIntent(path: String, applicationId: String) {
    ensureBackgroundThread {
        val newUri = getFinalUriFromPath(path, applicationId) ?: return@ensureBackgroundThread
        Intent().apply {
            action = Intent.ACTION_ATTACH_DATA
            setDataAndType(newUri, getUriMimeType(path, newUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(this, getString(R.string.set_as))

            try {
                startActivityForResult(chooser, REQUEST_SET_AS)
            } catch (e: ActivityNotFoundException) {
                toast(R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}

fun Activity.openEditorIntent(path: String, forceChooser: Boolean, applicationId: String) {
    ensureBackgroundThread {
        val newUri = getFinalUriFromPath(path, applicationId) ?: return@ensureBackgroundThread
        Intent().apply {
            action = Intent.ACTION_EDIT
            setDataAndType(newUri, getUriMimeType(path, newUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            val parent = path.getParentPath()
            val newFilename = "${path.getFilenameFromPath().substringBeforeLast('.')}_1"
            val extension = path.getFilenameExtension()
            val newFilePath = File(parent, "$newFilename.$extension")

            val outputUri = if (isPathOnOTG(path)) newUri else getFinalUriFromPath("$newFilePath", applicationId)
            val resInfoList = packageManager.queryIntentActivities(this, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                grantUriPermission(packageName, outputUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            putExtra(REAL_FILE_PATH, path)

            try {
                val chooser = Intent.createChooser(this, getString(R.string.edit_with))
                startActivityForResult(if (forceChooser) chooser else this, REQUEST_EDIT_IMAGE)
            } catch (e: ActivityNotFoundException) {
                toast(R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}

fun Activity.openPathIntent(
    path: String,
    forceChooser: Boolean,
    applicationId: String,
    forceMimeType: String = "",
    extras: HashMap<String, Boolean> = HashMap()
) {
    ensureBackgroundThread {
        val newUri = getFinalUriFromPath(path, applicationId) ?: return@ensureBackgroundThread
        val mimeType = if (forceMimeType.isNotEmpty()) forceMimeType else getUriMimeType(path, newUri)
        Intent().apply {
            action = Intent.ACTION_VIEW
            setDataAndType(newUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (applicationId == "com.simplemobiletools.gallery.pro" || applicationId == "com.simplemobiletools.gallery.pro.debug") {
                putExtra(IS_FROM_GALLERY, true)
            }

            for ((key, value) in extras) {
                putExtra(key, value)
            }

            putExtra(REAL_FILE_PATH, path)

            try {
                val chooser = Intent.createChooser(this, getString(R.string.open_with))
                startActivity(if (forceChooser) chooser else this)
            } catch (e: ActivityNotFoundException) {
                if (!tryGenericMimeType(this, mimeType, newUri)) {
                    toast(R.string.no_app_found)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}

fun Activity.launchViewContactIntent(uri: Uri) {
    Intent().apply {
        action = ContactsContract.QuickContact.ACTION_QUICK_CONTACT
        data = uri
        launchActivityIntent(this)
    }
}

fun BaseSimpleActivity.launchCallIntent(recipient: String, handle: PhoneAccountHandle? = null) {
    handlePermission(PERMISSION_CALL_PHONE) {
        val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", recipient, null)

            if (isMarshmallowPlus() && handle != null) {
                putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }

            launchActivityIntent(this)
        }
    }
}

fun Activity.launchSendSMSIntent(recipient: String) {
    Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.fromParts("smsto", recipient, null)
        launchActivityIntent(this)
    }
}

fun Activity.showLocationOnMap(coordinates: String) {
    val uriBegin = "geo:${coordinates.replace(" ", "")}"
    val encodedQuery = Uri.encode(coordinates)
    val uriString = "$uriBegin?q=$encodedQuery&z=16"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
    launchActivityIntent(intent)
}

fun Activity.getFinalUriFromPath(path: String, applicationId: String): Uri? {
    val uri = try {
        ensurePublicUri(path, applicationId)
    } catch (e: Exception) {
        showErrorToast(e)
        return null
    }

    if (uri == null) {
        toast(R.string.unknown_error_occurred)
        return null
    }

    return uri
}

fun Activity.tryGenericMimeType(intent: Intent, mimeType: String, uri: Uri): Boolean {
    var genericMimeType = mimeType.getGenericMimeType()
    if (genericMimeType.isEmpty()) {
        genericMimeType = "*/*"
    }

    intent.setDataAndType(uri, genericMimeType)

    return try {
        startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

fun BaseSimpleActivity.checkWhatsNew(releases: List<Release>, currVersion: Int) {
    if (baseConfig.lastVersion == 0) {
        baseConfig.lastVersion = currVersion
        return
    }

    val newReleases = arrayListOf<Release>()
    releases.filterTo(newReleases) { it.id > baseConfig.lastVersion }

    if (newReleases.isNotEmpty()) {
        WhatsNewDialog(this, newReleases)
    }

    baseConfig.lastVersion = currVersion
}

fun BaseSimpleActivity.deleteFolders(folders: List<FileDirItem>, deleteMediaOnly: Boolean = true, callback: ((wasSuccess: Boolean) -> Unit)? = null) {
    ensureBackgroundThread {
        deleteFoldersBg(folders, deleteMediaOnly, callback)
    }
}

fun BaseSimpleActivity.deleteFoldersBg(folders: List<FileDirItem>, deleteMediaOnly: Boolean = true, callback: ((wasSuccess: Boolean) -> Unit)? = null) {
    var wasSuccess = false
    var needPermissionForPath = ""
    for (folder in folders) {
        if (needsStupidWritePermissions(folder.path) && baseConfig.sdTreeUri.isEmpty()) {
            needPermissionForPath = folder.path
            break
        }
    }

    handleSAFDialog(needPermissionForPath) {
        if (!it) {
            return@handleSAFDialog
        }

        folders.forEachIndexed { index, folder ->
            deleteFolderBg(folder, deleteMediaOnly) {
                if (it)
                    wasSuccess = true

                if (index == folders.size - 1) {
                    runOnUiThread {
                        callback?.invoke(wasSuccess)
                    }
                }
            }
        }
    }
}

fun BaseSimpleActivity.deleteFolder(folder: FileDirItem, deleteMediaOnly: Boolean = true, callback: ((wasSuccess: Boolean) -> Unit)? = null) {
    ensureBackgroundThread {
        deleteFolderBg(folder, deleteMediaOnly, callback)
    }
}

fun BaseSimpleActivity.deleteFolderBg(fileDirItem: FileDirItem, deleteMediaOnly: Boolean = true, callback: ((wasSuccess: Boolean) -> Unit)? = null) {
    val folder = File(fileDirItem.path)
    if (folder.exists()) {
        val filesArr = folder.listFiles()
        if (filesArr == null) {
            runOnUiThread {
                callback?.invoke(true)
            }
            return
        }

        val files = filesArr.toMutableList().filter { !deleteMediaOnly || it.isMediaFile() }
        for (file in files) {
            deleteFileBg(file.toFileDirItem(applicationContext), false) { }
        }

        if (folder.listFiles()?.isEmpty() == true) {
            deleteFileBg(fileDirItem, true) { }
        }
    }
    runOnUiThread {
        callback?.invoke(true)
    }
}

fun BaseSimpleActivity.deleteFiles(files: List<FileDirItem>, allowDeleteFolder: Boolean = false, callback: ((wasSuccess: Boolean) -> Unit)? = null) {
    ensureBackgroundThread {
        deleteFilesBg(files, allowDeleteFolder, callback)
    }
}

fun BaseSimpleActivity.deleteFilesBg(files: List<FileDirItem>, allowDeleteFolder: Boolean = false, callback: ((wasSuccess: Boolean) -> Unit)? = null) {
    if (files.isEmpty()) {
        runOnUiThread {
            callback?.invoke(true)
        }
        return
    }

    var wasSuccess = false
    handleSAFDialog(files[0].path) {
        if (!it) {
            return@handleSAFDialog
        }

        val failedFileDirItems = ArrayList<FileDirItem>()
        files.forEachIndexed { index, file ->
            deleteFileBg(file, allowDeleteFolder) {
                if (it) {
                    wasSuccess = true
                } else {
                    failedFileDirItems.add(file)
                }

                if (index == files.size - 1) {
                    if (isRPlus() && failedFileDirItems.isNotEmpty()) {
                        val fileUris = getFileUrisFromFileDirItems(failedFileDirItems).second
                        deleteSDK30Uris(fileUris) { success ->
                            runOnUiThread {
                                callback?.invoke(success)
                            }
                        }
                    } else {
                        runOnUiThread {
                            callback?.invoke(wasSuccess)
                        }
                    }
                }
            }
        }
    }
}

fun BaseSimpleActivity.deleteFile(fileDirItem: FileDirItem, allowDeleteFolder: Boolean = false, callback: ((wasSuccess: Boolean) -> Unit)? = null) {
    ensureBackgroundThread {
        deleteFileBg(fileDirItem, allowDeleteFolder, callback)
    }
}

fun BaseSimpleActivity.deleteFileBg(fileDirItem: FileDirItem, allowDeleteFolder: Boolean = false, callback: ((wasSuccess: Boolean) -> Unit)? = null) {
    val path = fileDirItem.path
    if (isRestrictedSAFOnlyRoot(path)) {
        deleteAndroidSAFDirectory(path, allowDeleteFolder, callback)
    } else {
        val file = File(path)
        if (!isRPlus() && file.absolutePath.startsWith(internalStoragePath) && !file.canWrite()) {
            callback?.invoke(false)
            return
        }

        var fileDeleted = !isPathOnOTG(path) && ((!file.exists() && file.length() == 0L) || file.delete())
        if (fileDeleted) {
            deleteFromMediaStore(path) { needsRescan ->
                if (needsRescan) {
                    rescanAndDeletePath(path) {
                        runOnUiThread {
                            callback?.invoke(true)
                        }
                    }
                } else {
                    runOnUiThread {
                        callback?.invoke(true)
                    }
                }
            }
        } else {
            if (getIsPathDirectory(file.absolutePath) && allowDeleteFolder) {
                fileDeleted = deleteRecursively(file)
            }

            if (!fileDeleted) {
                if (needsStupidWritePermissions(path)) {
                    handleSAFDialog(path) {
                        if (it) {
                            trySAFFileDelete(fileDirItem, allowDeleteFolder, callback)
                        }
                    }
                } else if (isRPlus()) {
                    val fileUris = getFileUrisFromFileDirItems(arrayListOf(fileDirItem)).second
                    deleteSDK30Uris(fileUris) { success ->
                        runOnUiThread {
                            callback?.invoke(success)
                        }
                    }
                }
            }
        }
    }
}

private fun deleteRecursively(file: File): Boolean {
    if (file.isDirectory) {
        val files = file.listFiles() ?: return file.delete()
        for (child in files) {
            deleteRecursively(child)
        }
    }

    return file.delete()
}

fun Activity.scanFileRecursively(file: File, callback: (() -> Unit)? = null) {
    applicationContext.scanFileRecursively(file, callback)
}

fun Activity.scanPathRecursively(path: String, callback: (() -> Unit)? = null) {
    applicationContext.scanPathRecursively(path, callback)
}

fun Activity.scanFilesRecursively(files: List<File>, callback: (() -> Unit)? = null) {
    applicationContext.scanFilesRecursively(files, callback)
}

fun Activity.scanPathsRecursively(paths: List<String>, callback: (() -> Unit)? = null) {
    applicationContext.scanPathsRecursively(paths, callback)
}

fun Activity.rescanPath(path: String, callback: (() -> Unit)? = null) {
    applicationContext.rescanPath(path, callback)
}

fun Activity.rescanPaths(paths: List<String>, callback: (() -> Unit)? = null) {
    applicationContext.rescanPaths(paths, callback)
}

fun BaseSimpleActivity.renameFile(
    oldPath: String,
    newPath: String,
    isRenamingMultipleFiles: Boolean,
    callback: ((success: Boolean, useAndroid30Way: Boolean) -> Unit)? = null
) {
    if (isRestrictedSAFOnlyRoot(oldPath)) {
        handleAndroidSAFDialog(oldPath) {
            if (!it) {
                runOnUiThread {
                    callback?.invoke(false, false)
                }
                return@handleAndroidSAFDialog
            }

            try {
                val success = renameAndroidSAFDocument(oldPath, newPath)
                runOnUiThread {
                    callback?.invoke(success, false)
                }
            } catch (e: Exception) {
                showErrorToast(e)
                runOnUiThread {
                    callback?.invoke(false, false)
                }
            }
        }
    } else if (needsStupidWritePermissions(newPath)) {
        handleSAFDialog(newPath) {
            if (!it) {
                return@handleSAFDialog
            }

            val document = getSomeDocumentFile(oldPath)
            if (document == null || (File(oldPath).isDirectory != document.isDirectory)) {
                runOnUiThread {
                    callback?.invoke(false, false)
                }
                return@handleSAFDialog
            }

            try {
                ensureBackgroundThread {
                    try {
                        DocumentsContract.renameDocument(applicationContext.contentResolver, document.uri, newPath.getFilenameFromPath())
                    } catch (ignored: FileNotFoundException) {
                        // FileNotFoundException is thrown in some weird cases, but renaming works just fine
                    } catch (e: Exception) {
                        showErrorToast(e)
                        callback?.invoke(false, false)
                        return@ensureBackgroundThread
                    }

                    updateInMediaStore(oldPath, newPath)
                    rescanPaths(arrayListOf(oldPath, newPath)) {
                        if (!baseConfig.keepLastModified) {
                            updateLastModified(newPath, System.currentTimeMillis())
                        }
                        deleteFromMediaStore(oldPath)
                        runOnUiThread {
                            callback?.invoke(true, false)
                        }
                    }
                }
            } catch (e: Exception) {
                showErrorToast(e)
                runOnUiThread {
                    callback?.invoke(false, false)
                }
            }
        }
    } else {
        val oldFile = File(oldPath)
        val newFile = File(newPath)
        val tempFile = try {
            createTempFile(oldFile) ?: return
        } catch (exception: Exception) {
            if (isRPlus() && exception is java.nio.file.FileSystemException) {
                // if we are renaming multiple files at once, we should give the Android 30+ permission dialog all uris together, not one by one
                if (isRenamingMultipleFiles) {
                    callback?.invoke(false, true)
                } else {
                    val fileUris = getFileUrisFromFileDirItems(arrayListOf(File(oldPath).toFileDirItem(this))).second
                    updateSDK30Uris(fileUris) { success ->
                        if (success) {
                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, newPath.getFilenameFromPath())
                            }

                            try {
                                contentResolver.update(fileUris.first(), values, null, null)
                                callback?.invoke(true, false)
                            } catch (e: Exception) {
                                showErrorToast(e)
                                callback?.invoke(false, false)
                            }
                        } else {
                            callback?.invoke(false, false)
                        }
                    }
                }
            } else {
                showErrorToast(exception)
                callback?.invoke(false, false)
            }
            return
        }

        val oldToTempSucceeds = oldFile.renameTo(tempFile)
        val tempToNewSucceeds = tempFile.renameTo(newFile)
        if (oldToTempSucceeds && tempToNewSucceeds) {
            if (newFile.isDirectory) {
                updateInMediaStore(oldPath, newPath)
                rescanPath(newPath) {
                    runOnUiThread {
                        callback?.invoke(true, false)
                    }
                    deleteFromMediaStore(oldPath)
                    scanPathRecursively(newPath)
                }
            } else {
                if (!baseConfig.keepLastModified) {
                    newFile.setLastModified(System.currentTimeMillis())
                }
                updateInMediaStore(oldPath, newPath)
                scanPathsRecursively(arrayListOf(newPath)) {
                    deleteFromMediaStore(oldPath)
                    runOnUiThread {
                        callback?.invoke(true, false)
                    }
                }
            }
        } else {
            tempFile.delete()
            runOnUiThread {
                callback?.invoke(false, false)
            }
        }
    }
}

fun Activity.createTempFile(file: File): File? {
    return if (file.isDirectory) {
        createTempDir("temp", "${System.currentTimeMillis()}", file.parentFile)
    } else {
        if (isRPlus()) {
            // this can throw FileSystemException, lets catch and handle it at the place calling this function
            kotlin.io.path.createTempFile(file.parentFile.toPath(), "temp", "${System.currentTimeMillis()}").toFile()
        } else {
            createTempFile("temp", "${System.currentTimeMillis()}", file.parentFile)
        }
    }
}

fun Activity.hideKeyboard() {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow((currentFocus ?: View(this)).windowToken, 0)
    window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    currentFocus?.clearFocus()
}

fun Activity.showKeyboard(et: EditText) {
    et.requestFocus()
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
}

fun Activity.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

fun BaseSimpleActivity.getFileOutputStream(fileDirItem: FileDirItem, allowCreatingNewFile: Boolean = false, callback: (outputStream: OutputStream?) -> Unit) {
    if (needsStupidWritePermissions(fileDirItem.path)) {
        handleSAFDialog(fileDirItem.path) {
            if (!it) {
                return@handleSAFDialog
            }

            var document = getDocumentFile(fileDirItem.path)
            if (document == null && allowCreatingNewFile) {
                document = getDocumentFile(fileDirItem.getParentPath())
            }

            if (document == null) {
                showFileCreateError(fileDirItem.path)
                callback(null)
                return@handleSAFDialog
            }

            if (!getDoesFilePathExist(fileDirItem.path)) {
                document = document.createFile("", fileDirItem.name) ?: getDocumentFile(fileDirItem.path)
            }

            if (document?.exists() == true) {
                try {
                    callback(applicationContext.contentResolver.openOutputStream(document.uri))
                } catch (e: FileNotFoundException) {
                    showErrorToast(e)
                    callback(null)
                }
            } else {
                showFileCreateError(fileDirItem.path)
                callback(null)
            }
        }
    } else {
        val file = File(fileDirItem.path)
        if (file.parentFile?.exists() == false) {
            file.parentFile.mkdirs()
        }

        try {
            callback(FileOutputStream(file))
        } catch (e: Exception) {
            callback(null)
        }
    }
}

fun BaseSimpleActivity.showFileCreateError(path: String) {
    val error = String.format(getString(R.string.could_not_create_file), path)
    baseConfig.sdTreeUri = ""
    showErrorToast(error)
}

fun BaseSimpleActivity.getFileOutputStreamSync(path: String, mimeType: String, parentDocumentFile: DocumentFile? = null): OutputStream? {
    val targetFile = File(path)

    return when {
        isRestrictedSAFOnlyRoot(path) -> {
            val uri = getAndroidSAFUri(path)
            if (!getDoesFilePathExist(path)) {
                createAndroidSAFFile(path)
            }
            applicationContext.contentResolver.openOutputStream(uri)
        }
        needsStupidWritePermissions(path) -> {
            var documentFile = parentDocumentFile
            if (documentFile == null) {
                if (getDoesFilePathExist(targetFile.parentFile.absolutePath)) {
                    documentFile = getDocumentFile(targetFile.parent)
                } else {
                    documentFile = getDocumentFile(targetFile.parentFile.parent)
                    documentFile = documentFile!!.createDirectory(targetFile.parentFile.name) ?: getDocumentFile(targetFile.parentFile.absolutePath)
                }
            }

            if (documentFile == null) {
                val casualOutputStream = createCasualFileOutputStream(this, targetFile)
                return if (casualOutputStream == null) {
                    showFileCreateError(targetFile.parent)
                    null
                } else {
                    casualOutputStream
                }
            }

            try {
                val newDocument = documentFile.createFile(mimeType, path.getFilenameFromPath()) ?: getDocumentFile(path)
                applicationContext.contentResolver.openOutputStream(newDocument!!.uri)
            } catch (e: Exception) {
                showErrorToast(e)
                null
            }
        }
        else -> return createCasualFileOutputStream(this, targetFile)
    }
}

private fun createCasualFileOutputStream(activity: BaseSimpleActivity, targetFile: File): OutputStream? {
    if (targetFile.parentFile?.exists() == false) {
        targetFile.parentFile?.mkdirs()
    }

    return try {
        FileOutputStream(targetFile)
    } catch (e: Exception) {
        activity.showErrorToast(e)
        null
    }
}

fun FragmentActivity.performSecurityCheck(
    protectionType: Int,
    requiredHash: String,
    successCallback: ((String, Int) -> Unit)? = null,
    failureCallback: (() -> Unit)? = null
) {
    if (protectionType == PROTECTION_FINGERPRINT && isTargetSdkVersion30Plus()) {
        showBiometricPrompt(successCallback, failureCallback)
    } else {
        SecurityDialog(
            activity = this,
            requiredHash = requiredHash,
            showTabIndex = protectionType,
            callback = { hash, type, success ->
                if (success) {
                    successCallback?.invoke(hash, type)
                } else {
                    failureCallback?.invoke()
                }
            }
        )
    }
}

fun FragmentActivity.showBiometricPrompt(
    successCallback: ((String, Int) -> Unit)? = null,
    failureCallback: (() -> Unit)? = null
) {
    Class2BiometricAuthPrompt.Builder(getText(R.string.authenticate), getText(R.string.cancel))
        .build()
        .startAuthentication(
            AuthPromptHost(this),
            object : AuthPromptCallback() {
                override fun onAuthenticationSucceeded(activity: FragmentActivity?, result: BiometricPrompt.AuthenticationResult) {
                    successCallback?.invoke("", PROTECTION_FINGERPRINT)
                }

                override fun onAuthenticationError(activity: FragmentActivity?, errorCode: Int, errString: CharSequence) {
                    val isCanceledByUser = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED
                    if (!isCanceledByUser) {
                        toast(errString.toString())
                    }
                    failureCallback?.invoke()
                }

                override fun onAuthenticationFailed(activity: FragmentActivity?) {
                    toast(R.string.authentication_failed)
                    failureCallback?.invoke()
                }
            }
        )
}

fun FragmentActivity.handleHiddenFolderPasswordProtection(callback: () -> Unit) {
    if (baseConfig.isHiddenPasswordProtectionOn) {
        SecurityDialog(this, baseConfig.hiddenPasswordHash, baseConfig.hiddenProtectionType) { _, _, success ->
            if (success) {
                callback()
            }
        }
    } else {
        callback()
    }
}

fun FragmentActivity.handleAppPasswordProtection(callback: (success: Boolean) -> Unit) {
    if (baseConfig.isAppPasswordProtectionOn) {
        SecurityDialog(this, baseConfig.appPasswordHash, baseConfig.appProtectionType) { _, _, success ->
            callback(success)
        }
    } else {
        callback(true)
    }
}

fun FragmentActivity.handleDeletePasswordProtection(callback: () -> Unit) {
    if (baseConfig.isDeletePasswordProtectionOn) {
        SecurityDialog(this, baseConfig.deletePasswordHash, baseConfig.deleteProtectionType) { _, _, success ->
            if (success) {
                callback()
            }
        }
    } else {
        callback()
    }
}

fun FragmentActivity.handleLockedFolderOpening(path: String, callback: (success: Boolean) -> Unit) {
    if (baseConfig.isFolderProtected(path)) {
        SecurityDialog(this, baseConfig.getFolderProtectionHash(path), baseConfig.getFolderProtectionType(path)) { _, _, success ->
            callback(success)
        }
    } else {
        callback(true)
    }
}

fun BaseSimpleActivity.createDirectorySync(directory: String): Boolean {
    if (getDoesFilePathExist(directory)) {
        return true
    }

    if (needsStupidWritePermissions(directory)) {
        val documentFile = getDocumentFile(directory.getParentPath()) ?: return false
        val newDir = documentFile.createDirectory(directory.getFilenameFromPath()) ?: getDocumentFile(directory)
        return newDir != null
    }

    if (isRestrictedSAFOnlyRoot(directory)) {
        return createAndroidSAFDirectory(directory)
    }

    return File(directory).mkdirs()
}

fun Activity.updateSharedTheme(sharedTheme: SharedTheme) {
    try {
        val contentValues = MyContentProvider.fillThemeContentValues(sharedTheme)
        applicationContext.contentResolver.update(MyContentProvider.MY_CONTENT_URI, contentValues, null, null)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Activity.setupDialogStuff(
    view: View,
    dialog: AlertDialog,
    titleId: Int = 0,
    titleText: String = "",
    cancelOnTouchOutside: Boolean = true,
    callback: (() -> Unit)? = null
) {
    if (isDestroyed || isFinishing) {
        return
    }

    val adjustedPrimaryColor = getAdjustedPrimaryColor()
    if (view is ViewGroup)
        updateTextColors(view)
    else if (view is MyTextView) {
        view.setColors(baseConfig.textColor, adjustedPrimaryColor, baseConfig.backgroundColor)
    }

    var title: TextView? = null
    if (titleId != 0 || titleText.isNotEmpty()) {
        title = layoutInflater.inflate(R.layout.dialog_title, null) as TextView
        title.dialog_title_textview.apply {
            if (titleText.isNotEmpty()) {
                text = titleText
            } else {
                setText(titleId)
            }
            setTextColor(baseConfig.textColor)
        }
    }

    // if we use the same primary and background color, use the text color for dialog confirmation buttons
    val dialogButtonColor = if (adjustedPrimaryColor == baseConfig.backgroundColor) {
        baseConfig.textColor
    } else {
        adjustedPrimaryColor
    }

    dialog.apply {
        setView(view)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCustomTitle(title)
        setCanceledOnTouchOutside(cancelOnTouchOutside)
        show()
        getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(dialogButtonColor)
        getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(dialogButtonColor)
        getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(dialogButtonColor)

        val bgDrawable = resources.getColoredDrawableWithColor(R.drawable.dialog_bg, baseConfig.backgroundColor)
        window?.setBackgroundDrawable(bgDrawable)
    }
    callback?.invoke()
}

fun Activity.showPickSecondsDialogHelper(
    curMinutes: Int, isSnoozePicker: Boolean = false, showSecondsAtCustomDialog: Boolean = false, showDuringDayOption: Boolean = false,
    cancelCallback: (() -> Unit)? = null, callback: (seconds: Int) -> Unit
) {
    val seconds = if (curMinutes == -1) curMinutes else curMinutes * 60
    showPickSecondsDialog(seconds, isSnoozePicker, showSecondsAtCustomDialog, showDuringDayOption, cancelCallback, callback)
}

fun Activity.showPickSecondsDialog(
    curSeconds: Int, isSnoozePicker: Boolean = false, showSecondsAtCustomDialog: Boolean = false, showDuringDayOption: Boolean = false,
    cancelCallback: (() -> Unit)? = null, callback: (seconds: Int) -> Unit
) {
    hideKeyboard()
    val seconds = TreeSet<Int>()
    seconds.apply {
        if (!isSnoozePicker) {
            add(-1)
            add(0)
        }
        add(1 * MINUTE_SECONDS)
        add(5 * MINUTE_SECONDS)
        add(10 * MINUTE_SECONDS)
        add(30 * MINUTE_SECONDS)
        add(60 * MINUTE_SECONDS)
        add(curSeconds)
    }

    val items = ArrayList<RadioItem>(seconds.size + 1)
    seconds.mapIndexedTo(items) { index, value ->
        RadioItem(index, getFormattedSeconds(value, !isSnoozePicker), value)
    }

    var selectedIndex = 0
    seconds.forEachIndexed { index, value ->
        if (value == curSeconds) {
            selectedIndex = index
        }
    }

    items.add(RadioItem(-2, getString(R.string.custom)))

    if (showDuringDayOption) {
        items.add(RadioItem(-3, getString(R.string.during_day_at_hh_mm)))
    }

    RadioGroupDialog(this, items, selectedIndex, showOKButton = isSnoozePicker, cancelCallback = cancelCallback) {
        when (it) {
            -2 -> {
                CustomIntervalPickerDialog(this, showSeconds = showSecondsAtCustomDialog) {
                    callback(it)
                }
            }
            -3 -> {
                TimePickerDialog(
                    this, getDialogTheme(),
                    { view, hourOfDay, minute -> callback(hourOfDay * -3600 + minute * -60) },
                    curSeconds / 3600, curSeconds % 3600, baseConfig.use24HourFormat
                ).show()
            }
            else -> {
                callback(it as Int)
            }
        }
    }
}

fun BaseSimpleActivity.getAlarmSounds(type: Int, callback: (ArrayList<AlarmSound>) -> Unit) {
    val alarms = ArrayList<AlarmSound>()
    val manager = RingtoneManager(this)
    manager.setType(type)

    try {
        val cursor = manager.cursor
        var curId = 1
        val silentAlarm = AlarmSound(curId++, getString(R.string.no_sound), SILENT)
        alarms.add(silentAlarm)

        while (cursor.moveToNext()) {
            val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            var uri = cursor.getString(RingtoneManager.URI_COLUMN_INDEX)
            val id = cursor.getString(RingtoneManager.ID_COLUMN_INDEX)
            if (!uri.endsWith(id)) {
                uri += "/$id"
            }

            val alarmSound = AlarmSound(curId++, title, uri)
            alarms.add(alarmSound)
        }
        callback(alarms)
    } catch (e: Exception) {
        if (e is SecurityException) {
            handlePermission(PERMISSION_READ_STORAGE) {
                if (it) {
                    getAlarmSounds(type, callback)
                } else {
                    showErrorToast(e)
                    callback(ArrayList())
                }
            }
        } else {
            showErrorToast(e)
            callback(ArrayList())
        }
    }
}

fun AppCompatActivity.checkAppSideloading(): Boolean {
    val isSideloaded = when (baseConfig.appSideloadingStatus) {
        SIDELOADING_TRUE -> true
        SIDELOADING_FALSE -> false
        else -> isAppSideloaded()
    }

    baseConfig.appSideloadingStatus = if (isSideloaded) SIDELOADING_TRUE else SIDELOADING_FALSE
    if (isSideloaded) {
        showSideloadingDialog()
    }

    return isSideloaded
}

fun AppCompatActivity.isAppSideloaded(): Boolean {
    return try {
        getDrawable(R.drawable.ic_camera_vector)
        false
    } catch (e: Exception) {
        true
    }
}

fun AppCompatActivity.showSideloadingDialog() {
    AppSideloadedDialog(this) {
        finish()
    }
}

fun BaseSimpleActivity.getTempFile(folderName: String, fileName: String): File? {
    val folder = File(cacheDir, folderName)
    if (!folder.exists()) {
        if (!folder.mkdir()) {
            toast(R.string.unknown_error_occurred)
            return null
        }
    }

    return File(folder, fileName)
}
