package com.ridenow.driver.ui.components

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.theme.RideTheme

enum class PaperKind { Certificate, Revenue, Insurance }
enum class VehiclePhotoKind { Front, Rear, Left, Right, Interior, Extra }

/** One vehicle document: thumbnail, title, an Upload button and an expiry date button. */
@Composable
fun DocumentCard(
    title: String, kind: PaperKind, photo: Bitmap?, expiry: String,
    onUpload: () -> Unit, onExpiry: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val done = photo != null
    Row(
        modifier.fillMaxWidth().clip(shape).background(c.field).border(1.dp, c.border, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 64.dp, height = 80.dp).clip(RoundedCornerShape(12.dp))
                .background(if (done) c.accent.copy(alpha = 0.10f) else c.bg)
                .border(1.dp, if (done) c.accent else c.border, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onUpload).semantics { contentDescription = title },
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) Image(photo.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else PaperIllustration(kind, Modifier.fillMaxSize().padding(2.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(
                    if (done) RideIcons.Check else RideIcons.Camera, stringResource(if (done) R.string.doc_added_short else R.string.doc_upload),
                    done, onUpload, Modifier.weight(1f),
                )
                SmallButton(RideIcons.Calendar, expiry.ifEmpty { stringResource(R.string.doc_expiry) }, expiry.isNotEmpty(), onExpiry, Modifier.weight(1.2f))
            }
        }
    }
}

@Composable
private fun SmallButton(icon: List<String>, text: String, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(44.dp).clip(shape).background(if (active) c.accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (active) c.accent else c.border, shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        RideIcon(icon, if (active) c.accent else c.muted, size = 18.dp)
        Text(text, color = if (active) c.accent else c.fg, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Sheet-of-paper drawing for each document type, in outline style. */
@Composable
private fun PaperIllustration(kind: PaperKind, modifier: Modifier) {
    val c = RideTheme.colors
    Canvas(modifier) {
        val s = minOf(size.width / 64f, size.height / 76f)
        val ox = (size.width - 64f * s) / 2f
        fun x(v: Float) = ox + (v - 28f) * s
        fun y(v: Float) = (size.height - 76f * s) / 2f + v * s
        val soft = c.muted.copy(alpha = 0.35f)
        val solid = Stroke(1.3f * s)
        fun bar(l: Float, t: Float, w: Float) = drawRoundRect(soft, Offset(x(l), y(t)), Size(w * s, 4f * s), CornerRadius(2f * s))
        fun box(l: Float, t: Float, w: Float, h: Float, r: Float, color: Color) = drawRoundRect(color, Offset(x(l), y(t)), Size(w * s, h * s), CornerRadius(r * s), style = solid)
        drawRoundRect(c.muted, Offset(x(32f), y(4f)), Size(56f * s, 68f * s), CornerRadius(6f * s), style = Stroke(1.3f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * s, 3f * s))))
        when (kind) {
            PaperKind.Certificate -> {
                box(40f, 12f, 40f, 7f, 2f, c.accent); bar(40f, 25f, 40f); bar(40f, 33f, 32f); bar(40f, 41f, 38f)
                drawCircle(c.accent, 8f * s, Offset(x(70f), y(58f)), style = solid)
                drawPath(Path().apply { moveTo(x(66.5f), y(58f)); lineTo(x(69f), y(60.5f)); lineTo(x(73.5f), y(55f)) }, c.accent, style = solid)
            }
            PaperKind.Revenue -> {
                box(40f, 12f, 18f, 18f, 3f, c.accent); bar(62f, 14f, 18f); bar(62f, 22f, 14f); bar(40f, 38f, 40f); bar(40f, 46f, 32f); box(40f, 54f, 14f, 12f, 2f, c.accent)
            }
            PaperKind.Insurance -> {
                val shield = Path().apply {
                    moveTo(x(60f), y(11f)); lineTo(x(71f), y(15f)); lineTo(x(71f), y(23f))
                    cubicTo(x(71f), y(30f), x(66f), y(34f), x(60f), y(37f)); cubicTo(x(54f), y(34f), x(49f), y(30f), x(49f), y(23f))
                    lineTo(x(49f), y(15f)); close()
                }
                drawPath(shield, c.accent, style = solid)
                drawPath(Path().apply { moveTo(x(55f), y(23f)); lineTo(x(59f), y(27f)); lineTo(x(66f), y(19f)) }, c.accent, style = solid)
                bar(40f, 44f, 40f); bar(40f, 52f, 32f); bar(40f, 60f, 36f)
            }
        }
    }
}

/** Slot for one vehicle photo. Empty: a dashed outline of the view to frame; filled: the photo. */
@Composable
fun VehiclePhotoTile(label: String, kind: VehiclePhotoKind, photo: Bitmap?, optional: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val done = photo != null
    val border = c.border
    Column(
        modifier.height(136.dp).clip(shape).background(if (done) c.accent.copy(alpha = 0.10f) else c.field)
            .then(
                if (done) Modifier.border(1.dp, c.accent, shape)
                else Modifier.drawBehind {
                    drawRoundRect(border, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                },
            )
            .clickable(role = Role.Button, onClick = onClick).padding(8.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(66.dp), contentAlignment = Alignment.Center) {
            if (photo != null) Image(photo.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            else VehicleIllustration(kind, Modifier.fillMaxSize())
        }
        Text(label, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text(
            stringResource(if (done) R.string.doc_added else if (optional) R.string.doc_optional else R.string.doc_tap),
            color = if (done) c.accent else c.muted, fontSize = 12.sp,
        )
    }
}

/** Outline drawings of a car from the front, rear, sides and inside, on a 120 x 76 grid. */
@Composable
private fun VehicleIllustration(kind: VehiclePhotoKind, modifier: Modifier) {
    val c = RideTheme.colors
    Canvas(modifier) {
        val s = minOf(size.width / 120f, size.height / 76f)
        val ox = (size.width - 120f * s) / 2f
        val oy = (size.height - 76f * s) / 2f
        fun x(v: Float) = ox + v * s
        fun y(v: Float) = oy + v * s
        val edge = Stroke(1.3f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * s, 3f * s)))
        val solid = Stroke(1.3f * s)
        val soft = c.muted.copy(alpha = 0.35f)
        fun box(l: Float, t: Float, w: Float, h: Float, r: Float, color: Color, style: Stroke) =
            drawRoundRect(color, Offset(x(l), y(t)), Size(w * s, h * s), CornerRadius(r * s), style = style)

        fun DrawScope.side() {
            val body = Path().apply {
                moveTo(x(12f), y(52f)); lineTo(x(12f), y(44f)); quadraticBezierTo(x(12f), y(38f), x(20f), y(37f)); lineTo(x(34f), y(35f)); lineTo(x(44f), y(20f))
                quadraticBezierTo(x(46f), y(17f), x(50f), y(17f)); lineTo(x(78f), y(17f)); quadraticBezierTo(x(82f), y(17f), x(85f), y(21f)); lineTo(x(94f), y(35f))
                lineTo(x(102f), y(38f)); quadraticBezierTo(x(108f), y(40f), x(108f), y(46f)); lineTo(x(108f), y(52f)); close()
            }
            drawPath(body, c.muted, style = edge)
            drawPath(Path().apply { moveTo(x(47f), y(34f)); lineTo(x(53f), y(22f)); lineTo(x(65f), y(22f)); lineTo(x(65f), y(34f)); close() }, c.muted, style = solid)
            drawPath(Path().apply { moveTo(x(69f), y(22f)); lineTo(x(78f), y(22f)); lineTo(x(86f), y(34f)); lineTo(x(69f), y(34f)); close() }, c.muted, style = solid)
            for (cx in listOf(32f, 88f)) { drawCircle(c.muted, 9f * s, Offset(x(cx), y(53f)), style = solid); drawCircle(c.muted, 3.5f * s, Offset(x(cx), y(53f)), style = solid) }
            drawRoundRect(c.accent, Offset(x(100f), y(40f)), Size(7f * s, 5f * s), CornerRadius(2f * s), style = solid)
            drawLine(soft, Offset(x(6f), y(64f)), Offset(x(114f), y(64f)), 1.3f * s)
        }

        when (kind) {
            VehiclePhotoKind.Front, VehiclePhotoKind.Rear -> {
                val front = kind == VehiclePhotoKind.Front
                drawPath(Path().apply { moveTo(x(34f), y(28f)); lineTo(x(42f), y(10f)); lineTo(x(78f), y(10f)); lineTo(x(86f), y(28f)); close() }, c.muted, style = solid)
                box(24f, 26f, 72f, 30f, 9f, c.muted, edge)
                box(28f, 34f, 14f, 7f, 3f, if (front) c.accent else c.danger, solid); box(78f, 34f, 14f, 7f, 3f, if (front) c.accent else c.danger, solid)
                if (front) drawRoundRect(soft, Offset(x(48f), y(40f)), Size(24f * s, 9f * s), CornerRadius(2f * s)) else box(50f, 42f, 20f, 8f, 2f, c.muted, solid)
                box(22f, 54f, 14f, 12f, 3f, c.muted, solid); box(84f, 54f, 14f, 12f, 3f, c.muted, solid)
                drawLine(soft, Offset(x(8f), y(68f)), Offset(x(112f), y(68f)), 1.3f * s)
            }
            VehiclePhotoKind.Left -> side()
            VehiclePhotoKind.Right -> scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) { side() }
            VehiclePhotoKind.Interior -> {
                drawPath(Path().apply { moveTo(x(8f), y(32f)); quadraticBezierTo(x(60f), y(14f), x(112f), y(32f)); lineTo(x(112f), y(62f)); lineTo(x(8f), y(62f)); close() }, c.muted, style = edge)
                box(72f, 32f, 30f, 16f, 3f, c.muted, solid)
                drawCircle(c.accent, 17f * s, Offset(x(44f), y(46f)), style = Stroke(3f * s)); drawCircle(c.accent, 5f * s, Offset(x(44f), y(46f)), style = solid)
                drawLine(c.accent, Offset(x(27f), y(46f)), Offset(x(39f), y(46f)), 1.3f * s); drawLine(c.accent, Offset(x(49f), y(46f)), Offset(x(61f), y(46f)), 1.3f * s)
                drawLine(c.accent, Offset(x(44f), y(51f)), Offset(x(44f), y(63f)), 1.3f * s)
            }
            VehiclePhotoKind.Extra -> {
                box(20f, 8f, 80f, 60f, 8f, c.muted, edge)
                drawCircle(c.accent, 6f * s, Offset(x(80f), y(26f)), style = solid)
                drawPath(Path().apply { moveTo(x(20f), y(56f)); lineTo(x(42f), y(34f)); lineTo(x(58f), y(50f)); lineTo(x(70f), y(40f)); lineTo(x(100f), y(64f)) }, c.accent, style = solid)
                drawCircle(c.accent, 10f * s, Offset(x(92f), y(60f)), style = solid)
                drawLine(c.accent, Offset(x(92f), y(55f)), Offset(x(92f), y(65f)), 1.3f * s); drawLine(c.accent, Offset(x(87f), y(60f)), Offset(x(97f), y(60f)), 1.3f * s)
            }
        }
    }
}
