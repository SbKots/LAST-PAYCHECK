package com.lastpaycheck.presentation.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.lastpaycheck.domain.model.RaceState
import com.lastpaycheck.domain.model.RaceStyle
import com.lastpaycheck.presentation.R

private fun carColor(id: String) = when (id) {
    "sport-coupe" -> Color(0xFFCF785D)
    "family-wagon" -> Color(0xFFD4B66F)
    else -> Color(0xFF65BAAA)
}

@Composable
internal fun VehicleArtwork(id: String, garage: Boolean = false) {
    Canvas(Modifier.fillMaxWidth().height(178.dp)) {
        scale(size.width / 100, size.height / 60, pivot = Offset.Zero) {
            pixel(0, 0, 100, 60, Color(0xFF253C43))
            pixel(3, 3, 94, 27, Color(0xFF69877E))
            for (x in 5..90 step 18) {
                pixel(x, 6, 14, 17, Color(0xFFADCCC0))
                pixel(x + 2, 8, 10, 12, Color(0xFF486873))
            }
            pixel(3, 30, 94, 27, Color(if (garage) 0xFF57686A else 0xFF8C9480))
            for (x in 3..97 step 8) pixel(x, 30, 1, 27, Color(0xFF718079))
            for (y in 30..57 step 7) pixel(3, y, 94, 1, Color(0xFF718079))
            pixel(15, 35, 70, 18, Color(0xFFDFC995))
            pixel(17, 37, 66, 14, Color(0xFF364E56))
            if (garage) {
                pixel(5, 24, 12, 12, Color(0xFFA35F4E))
                pixel(7, 26, 8, 2, Color(0xFFD3B982))
                pixel(88, 20, 5, 17, Color(0xFF28373F))
            } else {
                pixel(40, 3, 22, 5, Color(0xFF71D1B7))
                pixel(5, 25, 4, 10, Color(0xFF3C604D))
                pixel(90, 25, 4, 10, Color(0xFF3C604D))
            }
            if (id.isNotEmpty()) {
                val color = carColor(id)
                val wagon = id == "family-wagon"
                pixel(24, 36, 54, 11, color)
                pixel(33, 28, if (wagon) 35 else 27, 11, color)
                pixel(36, 30, 9, 8, Color(0xFFB9DCD3))
                pixel(48, 30, if (wagon) 16 else 9, 8, Color(0xFFB9DCD3))
                pixel(27, 44, 10, 8, Color(0xFF17282F))
                pixel(65, 44, 10, 8, Color(0xFF17282F))
                pixel(30, 46, 4, 4, Color(0xFF82989B))
                pixel(68, 46, 4, 4, Color(0xFF82989B))
                pixel(76, 38, 3, 4, Color(0xFFFFE29C))
                pixel(24, 39, 3, 3, Color(0xFFDC655B))
            }
        }
    }
}

@Composable
internal fun RaceArtwork(race: RaceState) {
    val laneDescription = stringResource(R.string.race_lane, race.lane + 1)
    val visualLane by animateFloatAsState(race.lane.toFloat(), tween(180), label = "lane")
    Canvas(
        Modifier.fillMaxWidth().height(330.dp).clipToBounds().testTag("race.track")
            .semantics { stateDescription = laneDescription }
    ) {
        scale(size.width / 100, size.height / 170, pivot = Offset.Zero) {
            val verge = when (race.definition.style) {
                RaceStyle.NIGHT -> Color(0xFF253F37)
                RaceStyle.CITY -> Color(0xFF596F6A)
                RaceStyle.INDUSTRIAL -> Color(0xFF69675A)
            }
            pixel(0, 0, 100, 170, verge)
            pixel(12, 0, 76, 170, Color(0xFF667A78))
            pixel(17, 0, 66, 170, Color(0xFF304249))
            for (x in listOf(17, 82)) pixel(x, 0, 1, 170, Color(0xFFD6CF96))
            for (y in -20..180 step 20) {
                val yy = y + (race.distanceMeters % 20).toInt()
                pixel(38, yy, 1, 10, Color(0xFFD6CF96))
                pixel(60, yy, 1, 10, Color(0xFFD6CF96))
            }
            for (y in -30..180 step 35) {
                val yy = y + (race.distanceMeters % 35).toInt()
                when (race.definition.style) {
                    RaceStyle.NIGHT -> {
                        pixel(5, yy, 2, 14, Color(0xFF859794))
                        pixel(3, yy, 6, 2, Color(0xFFFFDB8C))
                    }
                    RaceStyle.CITY -> {
                        pixel(0, yy, 10, 22, Color(0xFF83A79D))
                        pixel(2, yy + 3, 3, 4, Color(0xFFE1CD8D))
                        pixel(92, yy + 10, 8, 20, Color(0xFF83959E))
                    }
                    RaceStyle.INDUSTRIAL -> {
                        pixel(0, yy, 10, 24, Color(0xFF9E705A))
                        pixel(2, yy + 3, 2, 18, Color(0xFFD2AD80))
                        pixel(91, yy + 14, 8, 9, Color(0xFF405D64))
                    }
                }
            }
            race.definition.pickups.filter { it.id !in race.collectedPickupIds }.forEach {
                val distance = it.distanceMeters - race.distanceMeters
                if (distance in -5.0..140.0) {
                    val y = (142 - distance).toInt()
                    val x = 25 + it.lane * 22
                    pixel(x, y, 7, 9, Color(0xFFFFD56B))
                    pixel(x + 2, y + 2, 3, 5, Color(0xFF8C5A27))
                    pixel(x + 3, y + 3, 1, 3, Color(0xFFFFEAB1))
                }
            }
            race.definition.obstacles.forEach {
                val distance = it.distanceMeters - race.distanceMeters
                if (distance in -10.0..140.0) {
                    val y = (142 - distance / 140 * 140).toInt()
                    pixel(21 + it.lane * 22, y, 13, 5, Color(0xFFE4AB5E))
                    pixel(23 + it.lane * 22, y + 1, 3, 3, Color(0xFF9E5544))
                    pixel(29 + it.lane * 22, y + 1, 3, 3, Color(0xFF9E5544))
                }
            }
            val x = (22 + visualLane * 22).toInt()
            val color = carColor(race.vehicleId)
            pixel(x - 2, 139, 3, 14, Color(0xFF12222A))
            pixel(x + 10, 139, 3, 14, Color(0xFF12222A))
            pixel(x, 135, 11, 23, color)
            pixel(x + 1, 138, 9, 5, Color(0xFFACD6D1))
            pixel(x + 2, 148, 7, 4, Color(0xFF50787B))
            pixel(x, 156, 3, 2, Color(if (race.braking) 0xFFFF7761 else 0xFFAE5A50))
            pixel(x + 8, 156, 3, 2, Color(if (race.braking) 0xFFFF7761 else 0xFFAE5A50))
        }
    }
}

private fun DrawScope.pixel(x: Int, y: Int, width: Int, height: Int, color: Color) =
    drawRect(color, Offset(x.toFloat(), y.toFloat()), Size(width.toFloat(), height.toFloat()))
