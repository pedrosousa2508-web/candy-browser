package dev.sk2andy.materialbrowser.data.passwords

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Never let a picker result turn an export into a plaintext write to an app-private file URI. */
internal object PasswordCsvDocumentRules {
    fun isSafDocument(context: Context, uri: Uri): Boolean =
        uri.scheme == "content" && runCatching { DocumentsContract.isDocumentUri(context, uri) }.getOrDefault(false)
}
