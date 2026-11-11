package com.example.fangkemeng.battle

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** 每段连续接触只触发一次；离开或丢失手部追踪后重新计时。 */
internal class PetTouchTimer {
    var seconds=0f
        private set
    private var fired=false

    fun update(dt: Float,touching: Boolean): Boolean {
        if(!touching) { reset();return false }
        seconds+=dt.coerceAtLeast(0f)
        if(fired||seconds<3f) return false
        fired=true
        return true
    }

    fun reset() { seconds=0f;fired=false }
}

/** 爱心只建立一次网格，释放时复用实体；动画完全由场景 ECS 帧驱动。 */
internal class CompanionHearts(content: SpatialViewContent) {
    private class Slot(val entity: Entity,val transform: TransformComponent,val material: UnlitMaterial) {
        val color=SkillMaterialColor(material)
        var age=0f
        var active=false
        var start=Vector3.ZERO
        var index=0
    }
    private val root=Entity()
    private val mesh: MeshResource
    private val slots: List<Slot>
    private val colors=AtomicReference<List<SkillMaterialUpdate>?>()
    private val uploading=AtomicBoolean()
    private val executor=Executors.newSingleThreadExecutor { task ->
        Thread(task,"FangkemengPetWorker").apply { isDaemon=true }
    }
    @Volatile private var disposed=false

    init {
        content.addEntity(root)
        val buffer=SkillBuffer()
        buffer.vertex(v(0,0,0),tint("#ffd5e1"))
        repeat(49) { i ->
            val a=i/48.0*TAU
            val x=16*sin(a).pow3()/32
            val y=(13*cos(a)-5*cos(2*a)-2*cos(3*a)-cos(4*a))/32
            buffer.vertex(v(x,y,0),tint("#ff5e91").lerp(tint("#ffe1ec"),clamp((y+.5)*.5)))
            if(i<48) buffer.tri(0,i+1,i+2)
        }
        mesh=MeshResource.createWithMeshModel(
            MeshModel(positions=buffer.positions,triangleIndices=buffer.indices,
                normals=buffer.normals,colors=buffer.colors),
            BoundingBox(Vector3(0f,-.06f,0f),Vector3(.51f,.48f,.001f)),
            "companion-heart",
        ).apply { toGlobal() }
        slots=List(6) { i ->
            val material=UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(Color4.WHITE)
                setCullingMode(MaterialCullingMode.NONE)
                setDepthWrite(false)
            }
            val entity=Entity().apply {
                enabled=false
                components.set(ModelComponent(mesh,material))
                root.addChild(this)
            }
            Slot(entity,requireNotNull(entity.components.get(TransformComponent::class.java)),material).also { it.index=i }
        }
    }

    fun play(headTop: Vector3) {
        if(disposed) return
        slots.forEach { slot ->
            slot.age=-slot.index*.12f
            slot.active=true
            slot.start=headTop
            slot.entity.enabled=false
        }
    }

    fun tick(dt: Float,viewer: Vector3) {
        if(disposed) return
        val updates=ArrayList<SkillMaterialUpdate>(6)
        slots.forEach { slot ->
            if(!slot.active) return@forEach
            slot.age+=dt
            if(slot.age<0f) return@forEach
            if(slot.age>=LIFE) {
                slot.active=false
                slot.entity.enabled=false
                return@forEach
            }
            val progress=slot.age/LIFE
            val pop=ease("easeOutBack",clamp(slot.age/.28)).toFloat()
            val fade=(1-ease("easeInQuad",clamp((progress-.55)/.45))).toFloat()
            val phase=slot.index*2.4f
            val side=if(slot.index==0) 0f else cos(phase)*.06f+cos(phase)*slot.age*.06f
            val position=Vector3(
                slot.start.x+side,
                slot.start.y+slot.age*(if(slot.index==0) .20f else .13f)+sin(progress*PI).toFloat()*.025f,
                slot.start.z+if(slot.index==0) 0f else sin(phase)*.05f+sin(phase)*slot.age*.035f,
            )
            val dx=viewer.x-position.x;val dy=viewer.y-position.y;val dz=viewer.z-position.z
            val yaw=(atan2(dx,dz)*180/PI).toFloat()
            val pitch=(-atan2(dy,max(.001f,sqrt(dx*dx+dz*dz)))*180/PI).toFloat()
            val size=(if(slot.index==0) .16f else .07f+slot.index*.007f)*pop*(.85f+.15f*fade)
            slot.transform.apply {
                setPosition(position)
                setEulerAngles(EulerAngles(pitch,yaw,sin(slot.age*3+phase)*9f))
                setScaleVector(Vector3(size,size,size))
            }
            updates.add(SkillMaterialUpdate(slot.color,Color4(1f,1f,1f,fade)))
            slot.entity.enabled=true
        }
        if(updates.isNotEmpty()) {
            colors.set(updates)
            uploadColors()
        }
    }

    @Synchronized fun dispose() {
        if(disposed) return
        disposed=true
        colors.set(null)
        root.destroy(true)
        executor.execute {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                slots.forEach { it.material.close() }
                mesh.close()
            }
        }
        executor.shutdown()
    }

    @Synchronized private fun uploadColors() {
        if(disposed||!uploading.compareAndSet(false,true)) return
        executor.execute {
            try {
                val packet=colors.getAndSet(null)
                if(!disposed) packet?.forEach { if(!disposed) it.target.upload(it.color) }
            } finally {
                uploading.set(false)
                if(colors.get()!=null) uploadColors()
            }
        }
    }

    private fun Double.pow3()=this*this*this
    private companion object { const val LIFE=1.8f }
}
