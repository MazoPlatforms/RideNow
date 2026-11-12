package com.ridenow.driver.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.theme.RideTheme

enum class DocKind { IdFront, IdBack, LicenseFront, LicenseBack }

/**
 * Upload slot for one side of a document. Empty: a dashed outline of the card as a framing guide.
 * Filled: the photo the driver took.
 */
@Composable
fun DocUploadTile(label: String, kind: DocKind, photo: Bitmap?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val done = photo != null
    val border = c.border
    Column(
        modifier.height(140.dp).clip(shape).background(if (done) c.accent.copy(alpha = 0.10f) else c.field)
            .then(
                if (done) Modifier.border(1.dp, c.accent, shape)
                else Modifier.drawBehind {
                    drawRoundRect(border, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                },
            )
            .clickable(role = Role.Button, onClick = onClick).padding(8.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.Center) {
            if (photo != null) Image(photo.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            else DocIllustration(kind, Modifier.fillMaxSize())
        }
        Text(label, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text(stringResource(if (done) R.string.doc_added else R.string.doc_tap), color = if (done) c.accent else c.muted, fontSize = 12.sp)
    }
}

/** Outline drawing of a card front or back on a 120 x 76 grid. */
@Composable
private fun DocIllustration(kind: DocKind, modifier: Modifier) {
    val c = RideTheme.colors
    Canvas(modifier) {
        val s = minOf(size.width / 120f, size.height / 76f)
        val ox = (size.width - 120f * s) / 2f
        val oy = (size.height - 76f * s) / 2f
        fun x(v: Float) = ox + v * s
        fun y(v: Float) = oy + v * s
        val dash = PathEffect.dashPathEffect(floatArrayOf(3f * s, 3f * s))
        val edge = Stroke(1.3f * s, pathEffect = dash)
        val solid = Stroke(1.3f * s)
        val soft = c.muted.copy(alpha = 0.35f)
        fun box(l: Float, t: Float, w: Float, h: Float, r: Float, color: androidx.compose.ui.graphics.Color, style: Stroke) =
            drawRoundRect(color, Offset(x(l), y(t)), Size(w * s, h * s), CornerRadius(r * s), style = style)
        fun bar(l: Float, t: Float, w: Float) = drawRoundRect(soft, Offset(x(l), y(t)), Size(w * s, 4f * s), CornerRadius(2f * s))
        fun portrait(l: Float, t: Float, w: Float, h: Float) {
            box(l, t, w, h, 5f, c.accent, solid)
            drawCircle(c.accent, 6f * s, Offset(x(l + w / 2), y(t + 13f)), style = solid)
            val p = Path().apply { moveTo(x(l + 5f), y(t + h - 2f)); quadraticBezierTo(x(l + w / 2), y(t + 19f), x(l + w - 5f), y(t + h - 2f)); close() }
            drawPath(p, c.accent, style = solid)
        }
        box(6f, 6f, 108f, 64f, 9f, c.muted, edge)
        when (kind) {
            DocKind.IdFront -> {
                portrait(14f, 24f, 30f, 36f); bar(14f, 12f, 40f); bar(52f, 26f, 50f); bar(52f, 36f, 36f); bar(52f, 46f, 44f)
                drawCircle(c.accent, 5f * s, Offset(x(101f), y(15f)), style = solid)
            }
            DocKind.IdBack -> {
                box(6f, 16f, 108f, 12f, 0f, c.muted, solid); bar(14f, 36f, 92f); bar(14f, 45f, 70f)
                for (i in 0 until 24) drawRect(soft, Offset(x(14f + i * 3.6f), y(56f)), Size((1f + (i * 7 % 3)) * s, 9f * s))
            }
            DocKind.LicenseFront -> {
                box(6f, 6f, 108f, 16f, 9f, c.accent, solid); portrait(14f, 30f, 26f, 32f)
                bar(48f, 32f, 56f); bar(48f, 42f, 40f); bar(48f, 52f, 26f); box(84f, 50f, 22f, 12f, 3f, c.accent, solid)
            }
            DocKind.LicenseBack -> {
                box(6f, 14f, 108f, 10f, 0f, c.muted, solid); bar(14f, 30f, 60f)
                for (j in 0 until 2) for (i in 0 until 5) box(14f + i * 20f, 40f + j * 11f, 16f, 8f, 2f, c.accent, solid)
            }
        }
    }
}

/**
 * Front and back upload tiles with a shared camera/gallery sheet. The side being edited is kept in
 * [side] so it survives the sheet closing before the camera or gallery returns a result.
 */
@Composable
fun DocPhotoPair(
    frontLabel: String, backLabel: String, frontKind: DocKind, backKind: DocKind,
    front: Bitmap?, back: Bitmap?, onFront: (Bitmap?) -> Unit, onBack: (Bitmap?) -> Unit, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var side by remember { mutableStateOf(0) }
    var sheet by remember { mutableStateOf(false) }
    fun set(b: Bitmap?) { if (side == 0) onFront(b) else onBack(b) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { if (it != null) set(it) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) decodeSampled(context, uri)?.let { set(it) } }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DocUploadTile(frontLabel, frontKind, front, { side = 0; sheet = true }, Modifier.weight(1f))
        DocUploadTile(backLabel, backKind, back, { side = 1; sheet = true }, Modifier.weight(1f))
    }
    if (sheet) {
        PhotoSheet(
            title = stringResource(R.string.doc_photo_title),
            hasPhoto = (if (side == 0) front else back) != null,
            onCamera = { camera.launch(null) },
            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemove = { set(null) },
            onDismiss = { sheet = false },
        )
    }
}
