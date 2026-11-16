package com.example.engine

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 0f, 1f)
    }
}

data class Quat(val w: Float = 1f, val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    fun normalized(): Quat {
        val len = sqrt(w * w + x * x + y * y + z * z)
        return if (len > 1e-6f) Quat(w / len, x / len, y / len, z / len) else IDENTITY
    }

    operator fun times(o: Quat): Quat = Quat(
        w = w * o.w - x * o.x - y * o.y - z * o.z,
        x = w * o.x + x * o.w + y * o.z - z * o.y,
        y = w * o.y - x * o.z + y * o.w + z * o.x,
        z = w * o.z + x * o.y - y * o.x + z * o.w
    )

    companion object {
        val IDENTITY = Quat(1f, 0f, 0f, 0f)

        fun fromEulerDeg(rotXDeg: Float, rotYDeg: Float, rotZDeg: Float): Quat {
            val rx = Math.toRadians(rotXDeg.toDouble()).toFloat() * 0.5f
            val ry = Math.toRadians(rotYDeg.toDouble()).toFloat() * 0.5f
            val rz = Math.toRadians(rotZDeg.toDouble()).toFloat() * 0.5f

            val qx = Quat(cos(rx), sin(rx), 0f, 0f)
            val qy = Quat(cos(ry), 0f, sin(ry), 0f)
            val qz = Quat(cos(rz), 0f, 0f, sin(rz))
            return (qy * qx * qz).normalized()
        }

        fun slerp(a: Quat, b: Quat, t: Float): Quat {
            val qa = a.normalized()
            var qb = b.normalized()
            var dot = qa.w * qb.w + qa.x * qb.x + qa.y * qb.y + qa.z * qb.z
            if (dot < 0f) {
                qb = Quat(-qb.w, -qb.x, -qb.y, -qb.z)
                dot = -dot
            }
            if (dot > 0.9995f) {
                return Quat(
                    qa.w + t * (qb.w - qa.w),
                    qa.x + t * (qb.x - qa.x),
                    qa.y + t * (qb.y - qa.y),
                    qa.z + t * (qb.z - qa.z)
                ).normalized()
            }
            val theta0 = acos(dot.coerceIn(-1f, 1f))
            val theta = theta0 * t
            val sinTheta = sin(theta)
            val sinTheta0 = sin(theta0)
            val s0 = cos(theta) - dot * sinTheta / sinTheta0
            val s1 = sinTheta / sinTheta0
            return Quat(
                s0 * qa.w + s1 * qb.w,
                s0 * qa.x + s1 * qb.x,
                s0 * qa.y + s1 * qb.y,
                s0 * qa.z + s1 * qb.z
            ).normalized()
        }
    }
}

class Mat4(val m: FloatArray) {
    operator fun times(o: Mat4): Mat4 {
        val out = FloatArray(16)
        for (r in 0..3) {
            for (c in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += m[r * 4 + k] * o.m[k * 4 + c]
                }
                out[r * 4 + c] = sum
            }
        }
        return Mat4(out)
    }

    fun transformPoint(v: Vec3): Vec3 {
        val x = m[0] * v.x + m[1] * v.y + m[2] * v.z + m[3]
        val y = m[4] * v.x + m[5] * v.y + m[6] * v.z + m[7]
        val z = m[8] * v.x + m[9] * v.y + m[10] * v.z + m[11]
        val w = m[12] * v.x + m[13] * v.y + m[14] * v.z + m[15]
        return if (abs(w) > 1e-6f && abs(w - 1f) > 1e-6f) Vec3(x / w, y / w, z / w) else Vec3(x, y, z)
    }

    fun transformDirection(v: Vec3): Vec3 {
        val x = m[0] * v.x + m[1] * v.y + m[2] * v.z
        val y = m[4] * v.x + m[5] * v.y + m[6] * v.z
        val z = m[8] * v.x + m[9] * v.y + m[10] * v.z
        return Vec3(x, y, z)
    }

    companion object {
        fun identity(): Mat4 = Mat4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            )
        )

        fun translation(x: Float, y: Float, z: Float): Mat4 = Mat4(
            floatArrayOf(
                1f, 0f, 0f, x,
                0f, 1f, 0f, y,
                0f, 0f, 1f, z,
                0f, 0f, 0f, 1f
            )
        )

        fun scale(sx: Float, sy: Float, sz: Float): Mat4 = Mat4(
            floatArrayOf(
                sx, 0f, 0f, 0f,
                0f, sy, 0f, 0f,
                0f, 0f, sz, 0f,
                0f, 0f, 0f, 1f
            )
        )

        fun fromQuat(q: Quat): Mat4 {
            val n = q.normalized()
            val xx = n.x * n.x
            val yy = n.y * n.y
            val zz = n.z * n.z
            val xy = n.x * n.y
            val xz = n.x * n.z
            val yz = n.y * n.z
            val wx = n.w * n.x
            val wy = n.w * n.y
            val wz = n.w * n.z

            return Mat4(
                floatArrayOf(
                    1f - 2f * (yy + zz), 2f * (xy - wz), 2f * (xz + wy), 0f,
                    2f * (xy + wz), 1f - 2f * (xx + zz), 2f * (yz - wx), 0f,
                    2f * (xz - wy), 2f * (yz + wx), 1f - 2f * (xx + yy), 0f,
                    0f, 0f, 0f, 1f
                )
            )
        }
    }
}
