package com.example.fangkemeng.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Text

/**
 * 应用级视觉令牌：联盟海报语言。
 * 米白纸面 + 纯黑信息条 + 品牌红 + 金，硬边与硬偏移投影。
 */
object PokeColors {
    val Ink = Color(0xFF14161C) // design-style: fixed-figma-color ink
    val Paper = Color(0xFFFBF7EE) // design-style: fixed-figma-color paper
    val PaperDim = Color(0xFFEFE7D6) // design-style: fixed-figma-color paper-dim
    val PaperDeep = Color(0xFFE4D8C0) // design-style: fixed-figma-color paper-deep

    val BrandRed = Color(0xFFE3350D) // design-style: fixed-figma-color brand-red
    val BrandRedHi = Color(0xFFFF5A36) // design-style: fixed-figma-color brand-red-hi
    val Gold = Color(0xFFF0B429) // design-style: fixed-figma-color fangkemeng-gold
    val GoldSoft = Color(0xFFFFE08A) // design-style: fixed-figma-color gold-soft
    val Blue = Color(0xFF1F4FD8) // design-style: fixed-figma-color move-blue
    val Mint = Color(0xFF1E9E6A) // design-style: fixed-figma-color mint

    val TextHi = Ink
    val TextMid = Color(0xFF4A4E57) // design-style: fixed-figma-color text-mid
    val TextLo = Color(0xFF7C8088) // design-style: fixed-figma-color text-lo
    val PaperText = Color(0xFFFBF7EE) // design-style: fixed-figma-color paper-text
    val PaperTextDim = Color(0xC7FBF7EE) // design-style: fixed-figma-color paper-text-dim

    val HpGreen = Mint
    val HpAmber = Color(0xFFD99A00) // design-style: fixed-figma-color hp-amber
    val HpRed = BrandRed
    val Poison = Color(0xFF7B3FA0) // design-style: fixed-figma-color existing-poison-status

    fun hpColor(ratio: Float): Color = when {
        ratio > 0.5f -> HpGreen
        ratio > 0.2f -> HpAmber
        else -> HpRed
    }
}

/** 新增操作沿用票券的硬边和纸色/墨色，不改变系统主题角色。 */
@Composable
fun PokeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(3.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PokeColors.Ink else PokeColors.PaperDeep,
            contentColor = if (selected) PokeColors.Gold else PokeColors.TextHi,
        ),
    ) { content() }
}

/** 精灵球标记（纯代码绘制，无位图资源）。 */
@Composable
fun PokeballMark(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        drawCircle(Color.White, radius = r, center = c)
        drawArc(
            color = PokeColors.BrandRed,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
        )
        drawLine(
            color = PokeColors.Ink,
            start = Offset(c.x - r, c.y),
            end = Offset(c.x + r, c.y),
            strokeWidth = r * 0.22f,
        )
        drawCircle(
            color = PokeColors.Ink,
            radius = r * 0.34f,
            center = c,
            style = Stroke(width = r * 0.22f),
        )
        drawCircle(color = Color.White, radius = r * 0.19f, center = c)
        drawCircle(
            color = PokeColors.Ink,
            radius = r * 0.94f,
            center = c,
            style = Stroke(width = r * 0.12f),
        )
    }
}

/** 首页主视觉：金色方块兽 + 红色硬投影，无辉光。 */
@Composable
fun CubeCreatureArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val scale = minOf(w, h) / 2f

        // 红色硬投影块。
        drawRect(
            color = PokeColors.BrandRed.copy(alpha = 0.16f),
            topLeft = Offset(cx - scale * 0.62f, cy - scale * 0.10f),
            size = Size(scale * 1.24f, scale * 0.98f),
        )

        val halfW = scale * 0.44f
        val halfH = halfW * 0.58f
        val bodyCy = cy - scale * 0.04f
        val top = Offset(cx, bodyCy - halfH * 2f)
        val rightTop = Offset(cx + halfW, bodyCy - halfH)
        val leftTop = Offset(cx - halfW, bodyCy - halfH)
        val frontTop = Offset(cx, bodyCy)
        val rightBottom = Offset(cx + halfW, bodyCy + halfH)
        val leftBottom = Offset(cx - halfW, bodyCy + halfH)
        val bottom = Offset(cx, bodyCy + halfH * 2f)

        drawPath(quad(top, rightTop, frontTop, leftTop), PokeColors.GoldSoft)
        drawPath(quad(leftTop, frontTop, bottom, leftBottom), PokeColors.Gold)
        drawPath(quad(rightTop, frontTop, bottom, rightBottom), PokeColors.HpAmber)
        drawPath(quad(top, rightTop, frontTop, leftTop), PokeColors.Ink, style = Stroke(width = 3f))
        drawPath(quad(leftTop, frontTop, bottom, leftBottom), PokeColors.Ink, style = Stroke(width = 3f))
        drawPath(quad(rightTop, frontTop, bottom, rightBottom), PokeColors.Ink, style = Stroke(width = 3f))

        val eyeR = halfW * 0.12f
        drawCircle(PokeColors.Ink, radius = eyeR, center = Offset(cx - halfW * 0.34f, bodyCy - halfH * 0.30f))
        drawCircle(PokeColors.Ink, radius = eyeR, center = Offset(cx + halfW * 0.34f, bodyCy - halfH * 0.30f))
        drawArc(
            color = PokeColors.Ink,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - halfW * 0.22f, bodyCy + halfH * 0.02f),
            size = Size(halfW * 0.44f, halfH * 0.34f),
            style = Stroke(width = 2.6f),
        )

        // 海报装饰点。
        drawCircle(PokeColors.BrandRed, radius = 4f, center = Offset(cx - scale * 0.86f, cy + scale * 0.22f))
        drawCircle(PokeColors.Blue, radius = 3f, center = Offset(cx + scale * 0.92f, cy - scale * 0.18f))
        drawCircle(PokeColors.Ink.copy(alpha = 0.3f), radius = 2f, center = Offset(cx + scale * 0.64f, cy + scale * 0.58f))
    }
}

/** 小节标签：红色短线 + 红色全大写英文。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 26.dp, height = 3.dp).background(PokeColors.BrandRed))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = PokeColors.BrandRed,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.2.sp,
        )
    }
}

/**
 * 票根式可点表面：纸面/黑底 + 墨线 + 硬偏移投影。
 * 所有卡片、按钮共用同一套硬边规则。
 */
@Composable
fun TicketSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shadow: Color = PokeColors.Ink.copy(alpha = 0.24f),
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(shadow),
        )
        Button(
            modifier = Modifier.fillMaxSize().padding(end = 4.dp, bottom = 4.dp),
            shape = RoundedCornerShape(3.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected) PokeColors.Ink else PokeColors.Paper,
                contentColor = if (selected) PokeColors.PaperText else PokeColors.TextHi,
            ),
            onClick = onClick,
            enabled = enabled,
        ) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}

/** 战场票根：编号 + 虚线撕口 + 名称/副标题/标签，选中为黑底红投影。 */
@Composable
fun StageTicket(
    name: String,
    subtitle: String,
    index: Int,
    selected: Boolean,
    tags: List<String>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TicketSurface(
        modifier = modifier.fillMaxWidth().height(92.dp),
        selected = selected,
        shadow = if (selected) PokeColors.BrandRed else PokeColors.Ink.copy(alpha = 0.18f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "%02d".format(index + 1),
                modifier = Modifier.width(44.dp),
                color = if (selected) PokeColors.Gold else PokeColors.TextMid,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Canvas(Modifier.width(1.dp).height(64.dp)) {
                drawLine(
                    color = if (selected) PokeColors.PaperTextDim else PokeColors.Ink.copy(alpha = 0.4f),
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = name,
                    color = if (selected) PokeColors.PaperText else PokeColors.TextHi,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = if (selected) PokeColors.PaperTextDim else PokeColors.TextMid,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        Box(
                            Modifier
                                .background(
                                    if (selected) PokeColors.PaperText.copy(alpha = 0.10f)
                                    else PokeColors.Ink.copy(alpha = 0.06f),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = tag,
                                color = if (selected) PokeColors.PaperTextDim else PokeColors.TextLo,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .padding(end = 10.dp)
                        .background(PokeColors.BrandRed)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(text = "已选中", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "→",
                    modifier = Modifier.padding(end = 12.dp),
                    color = PokeColors.TextLo,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

/** 顶部带红色强调条的面板标题。 */
@Composable
fun HudTitle(title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 22.dp, height = 3.dp).background(PokeColors.BrandRed))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = PokeColors.TextHi,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 分段进度条：墨色轨道 + 实色填充 + 等分刻度。 */
@Composable
fun HudBar(
    ratio: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    fill: Color = PokeColors.Mint,
    segments: Int = 10,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(PokeColors.Ink.copy(alpha = 0.14f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .background(fill),
        )
        Canvas(Modifier.fillMaxSize()) {
            val step = size.width / segments
            for (i in 1 until segments) {
                drawLine(
                    color = PokeColors.Paper.copy(alpha = 0.55f),
                    start = Offset(step * i, 0f),
                    end = Offset(step * i, size.height),
                    strokeWidth = 1.5f,
                )
            }
            drawRect(color = PokeColors.Ink.copy(alpha = 0.5f), style = Stroke(width = 1.5f))
        }
    }
}

/** 圆形状态点。 */
@Composable
fun StatusDot(color: Color, size: Dp = 6.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

private fun quad(a: Offset, b: Offset, c: Offset, d: Offset): Path = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    lineTo(d.x, d.y)
    close()
}
