package dev.fanfly.wingslog.core.ui.brand

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Pure geometry for morphing one closed outline into another. Both outlines arrive as the same
 * number of points in unit-square coordinates; this winds them the same way and rotates one so the
 * start points line up, so a straight lerp reads as one shape becoming the other. Free of Compose
 * types so it is unit-testable; [OutlineMorph] does the sampling and drawing.
 *
 * Points are flat `[x0, y0, x1, y1, ...]` arrays.
 */
object OutlineMath {

  /** Twice the signed area (shoelace); the sign says which way the outline winds. */
  fun signedArea(points: FloatArray): Float {
    var area = 0f
    val n = points.size / 2
    for (i in 0 until n) {
      val j = (i + 1) % n
      area += points[2 * i] * points[2 * j + 1] - points[2 * j] * points[2 * i + 1]
    }
    return area
  }

  /** Reverses the point order when the winding differs from [reference]. */
  fun matchWinding(points: FloatArray, reference: FloatArray): FloatArray {
    if (signedArea(points) * signedArea(reference) >= 0f) return points
    val n = points.size / 2
    return FloatArray(points.size) { i -> points[2 * (n - 1 - i / 2) + i % 2] }
  }

  /** Cyclically shifts the outline so it starts at point [offset]. */
  fun rotate(points: FloatArray, offset: Int): FloatArray {
    val n = points.size / 2
    return FloatArray(points.size) { i -> points[((i / 2 + offset) % n) * 2 + i % 2] }
  }

  /** The start offset for [candidate] that keeps its points closest to [target]'s, pointwise. */
  fun bestRotation(candidate: FloatArray, target: FloatArray): Int {
    val n = candidate.size / 2
    var best = 0
    var bestCost = Float.MAX_VALUE
    for (offset in 0 until n) {
      var cost = 0f
      for (i in 0 until n) {
        val c = ((i + offset) % n) * 2
        val dx = candidate[c] - target[2 * i]
        val dy = candidate[c + 1] - target[2 * i + 1]
        cost += dx * dx + dy * dy
        if (cost >= bestCost) break
      }
      if (cost < bestCost) {
        bestCost = cost
        best = offset
      }
    }
    return best
  }

  /** [to], rewound and rotated to pair with [from] point for point. */
  fun align(from: FloatArray, to: FloatArray): FloatArray {
    require(from.size == to.size) { "outlines must have the same sample count" }
    val b = matchWinding(to, from)
    return rotate(b, bestRotation(b, from))
  }

  fun lerp(a: FloatArray, b: FloatArray, t: Float, out: FloatArray) {
    for (i in a.indices) out[i] = a[i] + (b[i] - a[i]) * t
  }
}

/**
 * Morphs one outline into another. [from] and [to] are sampled with [sample] into the unit square
 * of the vector each belongs to, so a morph frame at `t = 0` or `1` sits exactly over that vector
 * drawn at the same size. [pathAt] returns the interpolated outline scaled to [size].
 */
class OutlineMorph(from: FloatArray, to: FloatArray) {
  private val from: FloatArray = from
  private val to: FloatArray = OutlineMath.align(from, to)
  private val scratch = FloatArray(from.size)

  fun pathAt(t: Float, size: Float, into: Path = Path()): Path {
    OutlineMath.lerp(from, to, t.coerceIn(0f, 1f), scratch)
    into.reset()
    into.moveTo(scratch[0] * size, scratch[1] * size)
    for (i in 2 until scratch.size step 2) {
      into.lineTo(scratch[i] * size, scratch[i + 1] * size)
    }
    into.close()
    return into
  }

  companion object {
    const val SAMPLES = 240

    /** The first closed contour of [pathData], as [n] equidistant points mapped through [toUnit]. */
    fun sample(pathData: String, n: Int = SAMPLES, toUnit: (Float, Float) -> Pair<Float, Float>): FloatArray {
      val path = PathParser().parsePathString(firstContour(pathData)).toPath()
      val measure = PathMeasure().apply { setPath(path, forceClosed = true) }
      val length = measure.length
      return FloatArray(n * 2).also { out ->
        for (i in 0 until n) {
          val p = measure.getPosition(length * i / n)
          val (x, y) = toUnit(p.x, p.y)
          out[2 * i] = x
          out[2 * i + 1] = y
        }
      }
    }

    /** Path data up to the second `M`, i.e. the outer outline without any holes. */
    fun firstContour(pathData: String): String {
      val second = pathData.indexOf('M', startIndex = 1)
      return if (second > 0) pathData.substring(0, second) else pathData
    }
  }
}
