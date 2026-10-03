package com.calle.sdk.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.calle.sdk.models.sanitizeE164Phone

/**
 * Represents a contact retrieved from the user's mobile device contact store.
 */
data class DeviceContactInfo(
    val name: String,
    val phone: String
)

/**
 * Resolves contact names from the Android device's Contact Store if permission is granted.
 */
class DeviceContactResolver {

    /**
     * Checks if the app has READ_CONTACTS permission.
     */
    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Searches device contacts for a matching name extracted from prompt or query string.
     */
    fun searchContact(context: Context, nameQuery: String): DeviceContactInfo? {
        if (!hasPermission(context) || nameQuery.isBlank()) return null

        val cleanQuery = nameQuery.trim()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$cleanQuery%")

        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    if (nameIdx != -1 && numIdx != -1) {
                        val name = it.getString(nameIdx) ?: ""
                        val rawNumber = it.getString(numIdx) ?: ""
                        val phone = sanitizeE164Phone(rawNumber)
                        if (phone.isNotBlank()) {
                            return DeviceContactInfo(name = name, phone = phone)
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts potential contact target name from user instruction prompt.
     * E.g. "Call Mom and ask..." -> "Mom"
     * "Call John Smith to ask about meeting" -> "John Smith"
     */
    fun extractNameFromPrompt(prompt: String): String {
        var clean = prompt.trim()
        if (clean.startsWith("Call ", ignoreCase = true)) {
            clean = clean.substring(5).trim()
        }
        val delimiters = listOf(" and ", " to ", " for ", " at ", " in ", " regarding ", " about ")
        for (delimiter in delimiters) {
            val idx = clean.indexOf(delimiter, ignoreCase = true)
            if (idx > 0) {
                return clean.substring(0, idx).trim()
            }
        }
        return clean.take(30)
    }
}
