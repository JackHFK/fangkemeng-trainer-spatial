package com.example.fangkemeng.battle

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class AttackParticleType { LEAF, BUBBLE, HEAL, FIRE, SPARK, ROCK, GUST }

/**
 * 代码侧粒子引擎：Spatial SDK 的 ParticleComponent 只能控制 AssetBundle 中已有 emitter，
 * 这里用轻量 ECS 实体池模拟持续发射、飞行、缩放和淡入淡出，避免每次技能创建/销毁大量节点。
 */
class AttackParticleEngine(
    private val content: SpatialViewContent,
) {
    private val effects = mutableListOf<AttackEffect>()
    private val particles = mutableListOf<ParticleSlot>()

    /** [intensity] 蓄力强度（1.0=普通）：放大发射密度与粒子尺寸，让蓄满的技能更壮观。 */
    fun play(type: AttackParticleType, from: Vector3, to: Vector3, intensity: Float = 1f) {
        val dir = normalized(Vector3(to.x - from.x, to.y - from.y, to.z - from.z), Vector3(0f, 0f, -1f))
        val right = horizontalRight(dir)
        effects += AttackEffect(
            type = type,
            from = from,
            to = to,
            dir = dir,
            right = right,
            intensity = intensity.coerceIn(0.6f, 2.2f),
        )
    }

    fun tick(dtSec: Float) {
        effects.forEach { effect ->
            effect.age += dtSec
            if (effect.age <= EFFECT_DURATION) emit(effect, dtSec)
        }
        particles.forEach { particle ->
            if (particle.active) updateParticle(particle, dtSec)
        }
        effects.removeAll { it.age > EFFECT_DURATION + PARTICLE_MAX_LIFE }
    }

    fun clear() {
        effects.clear()
        particles.forEach { slot ->
            slot.active = false
            slot.entity.enabled = false
        }
    }

    fun dispose() {
        clear()
        particles.forEach { slot -> runCatching { slot.entity.destroy(true) } }
        particles.clear()
    }

    private fun emit(effect: AttackEffect, dtSec: Float) {
        val intensity = effect.alpha
        if (intensity <= 0.03f) return
        val rate = when (effect.type) {
            AttackParticleType.LEAF -> LEAF_RATE
            AttackParticleType.BUBBLE -> BUBBLE_RATE
            AttackParticleType.HEAL -> HEAL_RATE
            AttackParticleType.FIRE -> FIRE_RATE
            AttackParticleType.SPARK -> SPARK_RATE
            AttackParticleType.ROCK -> ROCK_RATE
            AttackParticleType.GUST -> GUST_RATE
        }
        effect.spawnAccumulator += dtSec * rate * intensity * effect.intensity
        val spawnCount = effect.spawnAccumulator.toInt().coerceAtMost(MAX_SPAWN_PER_FRAME)
        if (spawnCount <= 0) return
        effect.spawnAccumulator -= spawnCount
        repeat(spawnCount) { spawnParticle(effect) }
    }

    private fun spawnParticle(effect: AttackEffect) {
        val slot = obtainSlot(effect.type) ?: return
        val sideJitter = randomSigned(0.08f)
        val upJitter = randomSigned(0.055f)
        val start = Vector3(
            effect.from.x + effect.right.x * sideJitter,
            effect.from.y + upJitter,
            effect.from.z + effect.right.z * sideJitter,
        )
        val travelDistance = distance(effect.from, effect.to) * Random.nextFloat().lerp(0.88f, 1.08f)
        val speed = when (effect.type) {
            AttackParticleType.LEAF -> Random.nextFloat().lerp(1.65f, 2.10f)
            AttackParticleType.BUBBLE -> Random.nextFloat().lerp(1.20f, 1.65f)
            AttackParticleType.HEAL -> Random.nextFloat().lerp(0.55f, 0.95f)
            AttackParticleType.FIRE -> Random.nextFloat().lerp(1.35f, 1.85f)
            AttackParticleType.SPARK -> Random.nextFloat().lerp(2.30f, 2.90f)
            AttackParticleType.ROCK -> Random.nextFloat().lerp(0.95f, 1.35f)
            AttackParticleType.GUST -> Random.nextFloat().lerp(1.50f, 2.00f)
        }
        val life = (travelDistance / speed).coerceIn(0.85f, PARTICLE_MAX_LIFE)
        val sizeScale = (0.85f + (effect.intensity - 1f) * 0.5f).coerceIn(0.8f, 1.8f)
        val size = when (effect.type) {
            AttackParticleType.LEAF -> Random.nextFloat().lerp(0.85f, 1.25f)
            AttackParticleType.BUBBLE -> Random.nextFloat().lerp(0.72f, 1.18f)
            AttackParticleType.HEAL -> Random.nextFloat().lerp(0.70f, 1.05f)
            AttackParticleType.FIRE -> Random.nextFloat().lerp(0.68f, 1.05f)
            AttackParticleType.SPARK -> Random.nextFloat().lerp(0.55f, 0.90f)
            AttackParticleType.ROCK -> Random.nextFloat().lerp(1.05f, 1.55f)
            AttackParticleType.GUST -> Random.nextFloat().lerp(0.80f, 1.20f)
        } * sizeScale
        slot.active = true
        slot.effect = effect
        slot.age = 0f
        slot.life = life
        slot.start = start
        slot.dir = effect.dir
        slot.right = effect.right
        slot.speed = speed
        slot.sideDrift = randomSigned(if (effect.type == AttackParticleType.LEAF) 0.07f else 0.035f)
        slot.upDrift = when (effect.type) {
            AttackParticleType.LEAF -> randomSigned(0.035f)
            AttackParticleType.BUBBLE -> Random.nextFloat().lerp(0.07f, 0.16f)
            AttackParticleType.HEAL -> Random.nextFloat().lerp(0.22f, 0.40f)
            AttackParticleType.FIRE -> Random.nextFloat().lerp(0.06f, 0.14f)
            AttackParticleType.SPARK -> randomSigned(0.05f)
            AttackParticleType.ROCK -> Random.nextFloat().lerp(-0.04f, 0.02f)
            AttackParticleType.GUST -> randomSigned(0.06f)
        }
        slot.spin = randomSigned(if (effect.type == AttackParticleType.LEAF) 520f else 180f)
        slot.phase = Random.nextFloat() * PI.toFloat() * 2f
        slot.baseScale = size
        slot.entity.enabled = true
        slot.material.setOpacity(0f)
    }

    private fun updateParticle(slot: ParticleSlot, dtSec: Float) {
        slot.age += dtSec
        val p = (slot.age / slot.life).coerceIn(0f, 1f)
        if (slot.age >= slot.life) {
            slot.active = false
            slot.entity.enabled = false
            return
        }

        val waveAmp = if (slot.type == AttackParticleType.LEAF) 0.055f else 0.035f
        val wave = sin(slot.phase + slot.age * if (slot.type == AttackParticleType.LEAF) 11f else 7f) * waveAmp
        val travel = slot.speed * slot.age
        val side = slot.sideDrift * slot.age + wave
        val up = slot.upDrift * slot.age + if (slot.type == AttackParticleType.BUBBLE) sin(slot.phase + slot.age * 9f) * 0.018f else 0f
        val pos = Vector3(
            slot.start.x + slot.dir.x * travel + slot.right.x * side,
            slot.start.y + slot.dir.y * travel + up,
            slot.start.z + slot.dir.z * travel + slot.right.z * side,
        )

        val effectAlpha = slot.effect?.alpha ?: 0f
        val lifeAlpha = particleEnvelope(p)
        val opacity = when (slot.type) {
            AttackParticleType.LEAF -> 0.90f
            AttackParticleType.BUBBLE -> 0.55f
            AttackParticleType.HEAL -> 0.85f
            AttackParticleType.FIRE -> 0.92f
            AttackParticleType.SPARK -> 0.95f
            AttackParticleType.ROCK -> 0.88f
            AttackParticleType.GUST -> 0.45f
        } * effectAlpha * lifeAlpha
        val scale = slot.baseScale * (0.75f + lifeAlpha * 0.35f)
        slot.material.setOpacity(opacity)
        slot.entity.components.get(TransformComponent::class.java)?.apply {
            setPosition(pos)
            setEulerAngles(EulerAngles(
                if (slot.type == AttackParticleType.LEAF) 14f * sin(slot.phase + slot.age * 6f) else 0f,
                yawDeg(slot.dir),
                slot.spin * slot.age,
            ))
            setScaleVector(Vector3(scale, scale, scale))
        }
    }

    private fun obtainSlot(type: AttackParticleType): ParticleSlot? {
        particles.firstOrNull { !it.active && it.type == type }?.let { return it }
        if (particles.size >= MAX_PARTICLES) return null

        val mesh = when (type) {
            AttackParticleType.LEAF -> MeshResource.createBox(Vector3(0.060f, 0.006f, 0.026f), 0.006f)
            AttackParticleType.BUBBLE -> MeshResource.createSphere(0.030f)
            AttackParticleType.HEAL -> MeshResource.createBox(Vector3(0.028f, 0.028f, 0.028f), 0.006f)
            AttackParticleType.FIRE -> MeshResource.createBox(Vector3(0.034f, 0.034f, 0.034f), 0.008f)
            AttackParticleType.SPARK -> MeshResource.createBox(Vector3(0.018f, 0.018f, 0.018f), 0.004f)
            AttackParticleType.ROCK -> MeshResource.createBox(Vector3(0.048f, 0.040f, 0.042f), 0.008f)
            AttackParticleType.GUST -> MeshResource.createBox(Vector3(0.070f, 0.006f, 0.040f), 0.006f)
        }
        val material = PhysicallyBasedMaterial.create(BlendingMode.TRANSPARENT).apply {
            when (type) {
                AttackParticleType.LEAF -> {
                    setBaseColor(Color4.fromSRGBHex(if (Random.nextBoolean()) "0x69D84FFF" else "0xA2F064FF"))
                    setEmissiveColor(Color4.fromSRGBHex("0x123F10FF"))
                    setRoughness(0.82f)
                }
                AttackParticleType.BUBBLE -> {
                    setBaseColor(Color4.fromSRGBHex("0x9EEAFFFF"))
                    setEmissiveColor(Color4.fromSRGBHex("0x1D5A78FF"))
                    setRoughness(0.12f)
                    setMetallic(0.02f)
                }
                AttackParticleType.HEAL -> {
                    setBaseColor(Color4.fromSRGBHex("0xB8F5C8FF"))
                    setEmissiveColor(Color4.fromSRGBHex("0x2E9E4FFF"))
                    setRoughness(0.5f)
                }
                AttackParticleType.FIRE -> {
                    setBaseColor(Color4.fromSRGBHex(if (Random.nextBoolean()) "0xFF8A3DFF" else "0xFFC15AFF"))
                    setEmissiveColor(Color4.fromSRGBHex("0x7A2108FF"))
                    setRoughness(0.6f)
                }
                AttackParticleType.SPARK -> {
                    setBaseColor(Color4.fromSRGBHex("0xFFE24AFF"))
                    setEmissiveColor(Color4.fromSRGBHex("0x8A6A00FF"))
                    setRoughness(0.35f)
                }
                AttackParticleType.ROCK -> {
                    setBaseColor(Color4.fromSRGBHex(if (Random.nextBoolean()) "0x9C9487FF" else "0xB7AE9EFF"))
                    setRoughness(0.9f)
                }
                AttackParticleType.GUST -> {
                    setBaseColor(Color4.fromSRGBHex("0xFFFFFFFF"))
                    setRoughness(0.7f)
                }
            }
            setOpacity(0f)
        }
        return ParticleSlot(
            type = type,
            entity = Entity().apply {
                components.set(ModelComponent(mesh, material))
                enabled = false
            }.also { content.addEntity(it) },
            material = material,
        ).also { particles += it }
    }

    private data class AttackEffect(
        val type: AttackParticleType,
        val from: Vector3,
        val to: Vector3,
        val dir: Vector3,
        val right: Vector3,
        val intensity: Float = 1f,
        var age: Float = 0f,
        var spawnAccumulator: Float = 0f,
    ) {
        val alpha: Float
            get() = effectEnvelope((age / EFFECT_DURATION).coerceIn(0f, 1f))
    }

    private data class ParticleSlot(
        val type: AttackParticleType,
        val entity: Entity,
        val material: PhysicallyBasedMaterial,
        var active: Boolean = false,
        var effect: AttackEffect? = null,
        var age: Float = 0f,
        var life: Float = 1f,
        var start: Vector3 = Vector3(0f, 0f, 0f),
        var dir: Vector3 = Vector3(0f, 0f, -1f),
        var right: Vector3 = Vector3(1f, 0f, 0f),
        var speed: Float = 1f,
        var sideDrift: Float = 0f,
        var upDrift: Float = 0f,
        var spin: Float = 0f,
        var phase: Float = 0f,
        var baseScale: Float = 1f,
    )

    companion object {
        private const val EFFECT_DURATION = 3.0f
        private const val PARTICLE_MAX_LIFE = 1.75f
        private const val LEAF_RATE = 26f
        private const val BUBBLE_RATE = 16f
        private const val HEAL_RATE = 10f
        private const val FIRE_RATE = 24f
        private const val SPARK_RATE = 34f
        private const val ROCK_RATE = 16f
        private const val GUST_RATE = 20f
        private const val MAX_PARTICLES = 96
        private const val MAX_SPAWN_PER_FRAME = 5
    }
}

private fun normalized(v: Vector3, fallback: Vector3): Vector3 {
    val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
    return if (len > 1e-4f) Vector3(v.x / len, v.y / len, v.z / len) else fallback
}

private fun horizontalRight(dir: Vector3): Vector3 {
    val len = sqrt(dir.x * dir.x + dir.z * dir.z)
    return if (len > 1e-4f) Vector3(-dir.z / len, 0f, dir.x / len) else Vector3(1f, 0f, 0f)
}

private fun distance(a: Vector3, b: Vector3): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    val dz = a.z - b.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

private fun effectEnvelope(t: Float): Float = when {
    t < 0.22f -> smooth(t / 0.22f)
    t > 0.70f -> smooth((1f - t) / 0.30f)
    else -> 1f
}

private fun particleEnvelope(t: Float): Float = when {
    t < 0.12f -> smooth(t / 0.12f)
    t > 0.78f -> smooth((1f - t) / 0.22f)
    else -> 1f
}

private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun Float.lerp(min: Float, max: Float): Float = min + (max - min) * this

private fun randomSigned(maxAbs: Float): Float = (Random.nextFloat() * 2f - 1f) * maxAbs

private fun yawDeg(dir: Vector3): Float =
    atan2(dir.x, dir.z) * 180f / PI.toFloat()
