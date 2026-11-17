package com.nijatva.photo.editor.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nijatva.photo.editor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One image row from MediaStore. */
data class DevicePhoto(val uri: Uri, val name: String)

fun mediaPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasMediaPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, mediaPermission()) == PackageManager.PERMISSION_GRANTED

/** Most recent images on the device, newest first. */
fun queryDevicePhotos(context: Context, limit: Int = 400): List<DevicePhoto> {
    val out = ArrayList<DevicePhoto>()
    val collection = if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME
    )
    try {
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            MediaStore.Images.Media.DATE_ADDED + " DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext() && out.size < limit) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "image"
                out.add(DevicePhoto(ContentUris.withAppendedId(collection, id), name))
            }
        }
    } catch (t: Throwable) {
        // no access yet, or a broken provider — the caller falls back to the system picker
    }
    return out
}

fun loadThumb(context: Context, uri: Uri, size: Int = 240): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 29) {
        context.contentResolver.loadThumbnail(uri, android.util.Size(size, size), null)
    } else {
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        }
    }
} catch (t: Throwable) {
    null
}

/** Whether a sibling Nijatva app is installed, so we can hand off to it. */
fun isPackageInstalled(context: Context, pkg: String): Boolean = try {
    context.packageManager.getLaunchIntentForPackage(pkg) != null
} catch (t: Throwable) {
    false
}

fun launchPackage(context: Context, pkg: String): Boolean = try {
    val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
} catch (t: Throwable) {
    false
}

/**
 * In-app photo browser: reads the device gallery itself instead of pushing the
 * user into the system picker. Falls back to the system picker when the
 * permission is refused.
 */
@Composable
fun PhotoBrowserScreen(
    onPick: (Uri) -> Unit,
    onBack: () -> Unit,
    onSystemPicker: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    var loading by remember { mutableStateOf(true) }
    var photos by remember { mutableStateOf<List<DevicePhoto>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(mediaPermission())
    }

    LaunchedEffect(granted) {
        if (granted) {
            loading = true
            photos = withContext(Dispatchers.IO) { queryDevicePhotos(context) }
            loading = false
        } else {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back), color = NijatvaTeal, fontSize = 13.sp)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.browse_title),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSystemPicker) {
                Text(stringResource(R.string.system_picker), color = TextSecondary, fontSize = 12.sp)
            }
        }

        when {
            !granted -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.browse_permission),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(NijatvaTeal, RoundedCornerShape(14.dp))
                            .clickable { permissionLauncher.launch(mediaPermission()) }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            stringResource(R.string.allow_access),
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onSystemPicker) {
                        Text(stringResource(R.string.system_picker), color = NijatvaTeal, fontSize = 13.sp)
                    }
                }
            }

            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NijatvaTeal, strokeWidth = 2.dp)
                }
            }

            photos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.browse_empty), color = TextSecondary, fontSize = 13.sp)
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(photos, key = { it.uri.toString() }) { photo ->
                        PhotoCell(photo) { onPick(photo.uri) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(photo: DevicePhoto, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(initialValue = null, photo.uri) {
        value = withContext(Dispatchers.IO) { loadThumb(context, photo.uri) }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Surface2, RoundedCornerShape(6.dp))
            .clickable { onClick() }
    ) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = photo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
