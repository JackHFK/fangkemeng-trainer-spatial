package com.example.fangkemeng.battle

import android.content.Context
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshIndexSizeType
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.MeshVertexAttribute
import com.pico.spatial.core.ecs.resource.MeshVertexAttributeType
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PrimitiveType
import com.pico.spatial.core.ecs.resource.RawMesh
import com.pico.spatial.core.ecs.resource.RawMeshDescriptorBuilder
import com.pico.spatial.core.ecs.resource.RawMeshStreamAttribute
import com.pico.spatial.core.ecs.resource.RawMeshStreamLayout
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import org.json.JSONObject
import kotlin.math.*

/** Double precision retains the approved deterministic particle seeds. */
internal data class P(val x: Double, val y: Double, val z: Double) {
    operator fun plus(b: P) = P(x+b.x,y+b.y,z+b.z)
    operator fun minus(b: P) = P(x-b.x,y-b.y,z-b.z)
    operator fun times(k: Double) = P(x*k,y*k,z*k)
    fun cross(b: P) = P(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
    fun dot(b: P) = x*b.x+y*b.y+z*b.z
    fun length() = sqrt(dot(this))
    fun unit() = length().let { if(it<1e-10) v() else this*(1/it) }
    fun native() = Vector3(x.toFloat(),y.toFloat(),z.toFloat())
}
internal fun v(x: Number=0,y: Number=0,z: Number=0) = P(x.toDouble(),y.toDouble(),z.toDouble())
internal fun Vector3.point() = v(x,y,z)
internal fun mix(a: P,b: P,u: Double) = a+(b-a)*u
internal fun clamp(u: Double) = u.coerceIn(0.0,1.0)
internal const val TAU = PI*2
internal const val SOURCE_X = -1.5
internal fun rand(i: Number,j: Number=0): Double {
    val x=sin(i.toDouble()*127.1+j.toDouble()*311.7)*43758.5453
    return x-floor(x)
}
internal data class Tint(val r: Double,val g: Double,val b: Double) {
    operator fun times(k: Double) = Tint(r*k,g*k,b*k)
    fun lerp(c: Tint,u: Double) = Tint(r+(c.r-r)*u,g+(c.g-g)*u,b+(c.b-b)*u)
    fun native(a: Double=1.0) = Color4(r.toFloat(),g.toFloat(),b.toFloat(),a.toFloat())
    /** 材质基色接口接收 sRGB；顶点颜色继续使用线性 RGB。 */
    fun material(a: Double=1.0): Color4 {
        fun srgb(u: Double) = if(u<=.0031308) u*12.92 else 1.055*u.pow(1/2.4)-.055
        return Color4(srgb(r).toFloat(),srgb(g).toFloat(),srgb(b).toFloat(),a.toFloat())
    }
}
private val tintCache=java.util.concurrent.ConcurrentHashMap<String,Tint>()
internal fun tint(hex: String): Tint = tintCache.getOrPut(hex) {
    val c=hex.removePrefix("#").removePrefix("0x").take(6).toInt(16)
    fun linear(n: Int): Double {
        val u=n/255.0
        return if(u<=.04045) u/12.92 else ((u+.055)/1.055).pow(2.4)
    }
    Tint(linear(c shr 16 and 255),linear(c shr 8 and 255),linear(c and 255))
}
internal fun heatColor(u: Double): Tint {
    val white=tint("#fff5bb"); val gold=tint("#ffbd47")
    val orange=tint("#ff762a"); val red=tint("#d82d20")
    return if(u<.28) white.lerp(gold,u/.28) else if(u<.65) gold.lerp(orange,(u-.28)/.37) else orange.lerp(red,(u-.65)/.35)
}
/** Matches Three.js Euler XYZ: apply Z, then Y, then X. Angles are radians. */
internal fun rotate(p: P,r: P): P {
    val x=p.x*cos(r.z)-p.y*sin(r.z); val y=p.x*sin(r.z)+p.y*cos(r.z)
    val xx=x*cos(r.y)+p.z*sin(r.y); val z=-x*sin(r.y)+p.z*cos(r.y)
    return v(xx,y*cos(r.x)-z*sin(r.x),y*sin(r.x)+z*cos(r.x))
}
internal data class Primitive(val points: List<P>,val normals: List<P>,val indices: List<Int>)
internal object SkillPrimitives {
    private val meshes=mutableMapOf<String,Primitive>()
    fun init(context: Context) {
        if(meshes.isNotEmpty()) return
        val data=JSONObject(context.assets.open("skill-primitives.json").bufferedReader().use { it.readText() })
        data.keys().forEach { key ->
            val o=data.getJSONObject(key)
            fun triples(field: String): List<P> {
                val a=o.getJSONArray(field)
                return List(a.length()/3) { i -> v(a.getDouble(i*3),a.getDouble(i*3+1),a.getDouble(i*3+2)) }
            }
            val indices=o.getJSONArray("indices")
            meshes[key]=Primitive(triples("positions"),triples("normals"),List(indices.length()) { indices.getInt(it) })
        }
    }
    operator fun get(key: String) = requireNotNull(meshes[key]) { "Skill primitive not initialized: $key" }
}
internal class SkillBuffer {
    private val up=Vector3(0f,1f,0f)
    val positions=ArrayList<Vector3>()
    val normals=ArrayList<Vector3>()
    val colors=ArrayList<Color4>()
    val indices=ArrayList<Int>()
    fun reset() { positions.clear();normals.clear();colors.clear();indices.clear() }
    fun vertex(p: P,c: Tint,a: Double=1.0,n: P?=null) {
        require(p.x.isFinite()&&p.y.isFinite()&&p.z.isFinite()) { "Non-finite skill vertex" }
        positions.add(p.native()); normals.add(n?.native()?:up); colors.add(c.native(clamp(a)))
    }
    fun tri(a: Int,b: Int,c: Int) { indices.add(a);indices.add(b);indices.add(c) }
    fun bounds(): BoundingBox {
        val min=v(positions.minOf { it.x },positions.minOf { it.y },positions.minOf { it.z })
        val max=v(positions.maxOf { it.x },positions.maxOf { it.y },positions.maxOf { it.z })
        return BoundingBox(((min+max)*.5).native(),((max-min)*.5+v(.001,.001,.001)).native())
    }
}
/** 全部效果层共用两个通道；跨释放保留资源，退出容器时释放。 */
internal data class SkillRigid(val key: String,val position: P,val scale: P,val rotation: Quat,val color: Tint,val alpha: Double)
/** 只包含资源句柄；后台上传不读取或修改 Entity/Transform。 */
internal class SkillMaterialColor(private val material: Material) {
    private var applied: Color4?=null
    fun upload(color: Color4) {
        if(applied==color) return
        if(material is UnlitMaterial) material.setBaseColor(color)
        else (material as PhysicallyBasedMaterial).setBaseColor(color)
        applied=color
    }
}
internal data class SkillMaterialUpdate(val target: SkillMaterialColor,val color: Color4)
internal data class SkillPresentation(val channels: List<Boolean>)
internal class SkillLayerMesh(private val parent: Entity,private val name: String) {
    val lit=SkillBuffer(); val unlit=SkillBuffer()
    private val entities=arrayOf(Entity(),Entity())
    private val meshes=arrayOfNulls<MeshResource>(2)
    private val components=arrayOfNulls<ModelComponent>(2)
    private val visible=BooleanArray(2)
    private val materials=arrayOfNulls<Material>(2)
    private class Slot(val entity: Entity,val transform: TransformComponent,val material: Material) {
        val color=SkillMaterialColor(material)
        var visible=false
    }
    private val rigidMeshes=mutableMapOf<String,MeshResource>()
    private val slots=mutableMapOf<String,MutableList<Slot>>()
    private val warmCounts=SkillVfx.recipes.associateWith { recipe ->
        val counts=mutableMapOf<String,Int>()
        fun add(key: String,n: Int) { counts[key]=counts.getOrDefault(key,0)+n }
        recipe.layers.forEach { layer ->
            when(layer.kind) {
                "particles"->when(layer.shape) {
                    "leaf","feather"->Unit
                    "wood"->add("box",layer.count)
                    "rock","seed"->add(layer.shape,layer.count)
                    else->add("point:${if(layer.shape=="drop") "drop" else if(layer.shape=="spark") "spark" else "glow"}",layer.count)
                }
                "seeds"->{ add("seed",layer.count);add("cone",layer.count) }
                "orb"->if(layer.shape=="seed") { add("seed",1);add("cone",1) } else add("sphere",1)
                "armor"->add(if(layer.mode=="metal") "metal" else "rock",layer.count)
                "jaws"->add("cone",if(layer.mode=="bug") 2 else 8)
                "rocks"->add("rock",layer.count)
                "hammer"->add("box",1)
            }
        }
        counts
    }
    private val warmPlan=warmCounts.values.fold(linkedMapOf<String,Int>()) { maximum,counts ->
        counts.forEach { (key,count) -> maximum[key]=max(maximum.getOrDefault(key,0),count) }
        maximum
    }
    // 映射缓冲可能轮换，不能假设回调保留上次写入；每次提交完整快照。
    // 容量覆盖当前完整技能目录；未使用顶点透明、索引尾部为退化三角形。
    private val indexSizes=IntArray(2)
    private val vertexData=FloatArray(DYNAMIC_VERTICES*VERTEX_STRIDE/4)
    private val indexData=ShortArray(DYNAMIC_INDICES)
    private fun packVertices(b: SkillBuffer) {
        check(b.positions.size<=DYNAMIC_VERTICES) { "Skill vertex capacity exceeded: $name" }
        for(j in b.positions.indices) {
            val p=b.positions[j];val n=b.normals[j];val c=b.colors[j]
            val offset=j*10
            vertexData[offset]=p.x;vertexData[offset+1]=p.y;vertexData[offset+2]=p.z
            vertexData[offset+3]=n.x;vertexData[offset+4]=n.y;vertexData[offset+5]=n.z
            vertexData[offset+6]=c.red;vertexData[offset+7]=c.green
            vertexData[offset+8]=c.blue;vertexData[offset+9]=c.alpha
        }
    }
    /** 只在进场准备期创建一次；写回调只访问已生成的数据，不计算几何。 */
    fun prepareDynamic() {
        for(i in 0..1) {
            if(meshes[i]!=null) continue
            val descriptor=RawMeshDescriptorBuilder().use { builder ->
                builder.streamLayout(RawMeshStreamLayout(bufferSlot=0,stride=VERTEX_STRIDE))
                    .streamAttribute(RawMeshStreamAttribute(MeshVertexAttribute.POSITION,MeshVertexAttributeType.FLOAT3))
                    .streamAttribute(RawMeshStreamAttribute(MeshVertexAttribute.NORMAL,MeshVertexAttributeType.FLOAT3,
                        recordByteOffset=12))
                    .streamAttribute(RawMeshStreamAttribute(MeshVertexAttribute.COLOR,MeshVertexAttributeType.FLOAT4,
                        recordByteOffset=24))
                    .vertexCount(DYNAMIC_VERTICES).indexCount(DYNAMIC_INDICES)
                    .indexType(MeshIndexSizeType.UNSIGNED_SHORT).isDynamic(true)
                    .boundingBox(BoundingBox(Vector3.ZERO,Vector3(64f,64f,64f)))
                    .addSubMesh(0,DYNAMIC_INDICES,0,"skill-$i",PrimitiveType.TRIANGLES).build()
            }
            android.os.Trace.beginSection("FangkemengVfx.raw.create")
            try {
                meshes[i]=descriptor.use { desc ->
                    RawMesh.create(desc).use { raw ->
                        check(raw.populateVertexBuffer(0) { buffer ->
                            buffer.asFloatBuffer().put(vertexData)
                            true
                        })
                        check(raw.populateIndicesBuffer { buffer ->
                            buffer.asShortBuffer().put(indexData)
                            true
                        })
                        MeshResource.createWithRawMesh(raw,"skill-$name-$i").apply { toGlobal() }
                    }
                }
            } finally { android.os.Trace.endSection() }
        }
    }
    private fun material(i: Int): Material = materials[i]?:(
        if(i==0) PhysicallyBasedMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color4.WHITE);setRoughness(.72f);setMetallic(.05f)
            setCullingMode(MaterialCullingMode.NONE);setDepthWrite(false)
        } else UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color4.WHITE);setCullingMode(MaterialCullingMode.NONE)
            setDepthWrite(false)
        }
    ).also { materials[i]=it }
    init { entities.forEach { parent.addChild(it);it.enabled=false } }
    fun reset() { lit.reset();unlit.reset() }
    private fun visibility(i: Int, enabled: Boolean) {
        if (visible[i] != enabled) { entities[i].enabled=enabled; visible[i]=enabled }
    }
    fun hide() { for (i in 0..1) visibility(i,false) }
    /** 进场期间分帧建立有界对象池，释放技能时直接使用。 */
    fun prewarm(recipe: SkillRecipe?=null) {
        var remaining=2
        for((key,count) in (recipe?.let { warmCounts.getValue(it) }?:warmPlan)) {
            val group=slots.getOrPut(key) { mutableListOf() }
            while(group.size<count&&remaining>0) { group.add(createSlot(key));remaining-- }
            if(remaining==0) return
        }
    }
    fun upload(): SkillPresentation? {
        prepareDynamic()
        arrayOf(lit,unlit).forEachIndexed { i,b ->
            if(b.indices.isEmpty()&&indexSizes[i]==0) return@forEachIndexed
            check(b.indices.size<=DYNAMIC_INDICES&&b.indices.size%3==0) { "Skill index capacity exceeded: $name" }
            val mesh=requireNotNull(meshes[i])
            vertexData.fill(0f)
            indexData.fill(0)
            packVertices(b)
            for(j in b.indices.indices) {
                val index=b.indices[j]
                check(index in b.positions.indices)
                indexData[j]=index.toShort()
            }
            android.os.Trace.beginSection("FangkemengVfx.raw.vertices")
            try {
                val accepted=mesh.updateRawMeshVertexBuffer(0) { buffer ->
                    check(buffer.capacity()>=DYNAMIC_VERTICES*VERTEX_STRIDE)
                    buffer.asFloatBuffer().put(vertexData)
                    true
                }
                if(!accepted) {
                    android.util.Log.w("FangkemengVfx","upload deferred channel=$i buffer=vertices")
                    return null
                }
            }
            finally { android.os.Trace.endSection() }
            android.os.Trace.beginSection("FangkemengVfx.raw.indices")
            try {
                val accepted=mesh.updateRawMeshIndicesBuffer { buffer ->
                    check(buffer.capacity()>=DYNAMIC_INDICES*2)
                    buffer.asShortBuffer().put(indexData)
                    true
                }
                if(!accepted) {
                    android.util.Log.w("FangkemengVfx","upload deferred channel=$i buffer=indices")
                    return null
                }
                indexSizes[i]=b.indices.size
            } finally { android.os.Trace.endSection() }
        }
        return SkillPresentation(listOf(lit.indices.isNotEmpty(),unlit.indices.isNotEmpty()))
    }
    /** 仅在主线程绑定组件；后台上传通过 Presented 的 volatile 发布。 */
    fun bind(presentation: SkillPresentation) {
        for(i in 0..1) {
            val resource=meshes[i]
            if(components[i]==null&&resource!=null) {
                val component=ModelComponent(resource,material(i))
                components[i]=component
                entities[i].components.set(component)
            }
            visibility(i,presentation.channels[i]&&resource!=null)
        }
    }
    /** 刚性粒子与模型直接随 ECS 时钟更新，不等待后台动态网格。 */
    fun bindRigid(commands: List<SkillRigid>): List<SkillMaterialUpdate> {
        val used=mutableMapOf<String,Int>()
        val colors=ArrayList<SkillMaterialUpdate>(commands.size)
        commands.forEach { command ->
            val index=used.getOrDefault(command.key,0)
            val group=slots.getOrPut(command.key) { mutableListOf() }
            if(index==group.size) {
                android.os.Trace.beginSection("FangkemengVfx.rigid.create")
                try { group.add(createSlot(command.key)) }
                finally { android.os.Trace.endSection() }
            }
            val slot=group[index]
            slot.transform.setPosition(command.position.native())
            slot.transform.setScaleVector(command.scale.native())
            slot.transform.setQuaternion(command.rotation)
            colors.add(SkillMaterialUpdate(slot.color,command.color.material(command.alpha)))
            if(!slot.visible) { slot.entity.enabled=true;slot.visible=true }
            used[command.key]=index+1
        }
        slots.forEach { (key,group) ->
            for(i in used.getOrDefault(key,0) until group.size) {
                if(group[i].visible) { group[i].entity.enabled=false;group[i].visible=false }
            }
        }
        return colors
    }
    private fun createSlot(key: String): Slot {
        val mesh=rigidMeshes.getOrPut(key) {
            val buffer=SkillBuffer()
            if(key.startsWith("point:")) SkillDrawing.pointTemplate(key.substringAfter(':'),buffer)
            else {
                val g=SkillPrimitives[key]
                g.points.forEachIndexed { i,p -> buffer.vertex(p,Tint(1.0,1.0,1.0),n=g.normals[i]) }
                buffer.indices.addAll(g.indices)
            }
            MeshResource.createWithMeshModel(MeshModel(positions=buffer.positions,triangleIndices=buffer.indices,
                normals=buffer.normals,colors=buffer.colors),buffer.bounds(),"skill-rigid-$name-$key").apply { toGlobal() }
        }
        val material=if(key.startsWith("point:")) UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color4.WHITE);setCullingMode(MaterialCullingMode.NONE);setDepthWrite(false)
        } else PhysicallyBasedMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color4.WHITE);setRoughness(.72f);setMetallic(.05f)
            setCullingMode(MaterialCullingMode.NONE);setDepthWrite(false)
        }
        val entity=Entity()
        entity.enabled=false
        entity.components.set(ModelComponent(mesh,material))
        parent.addChild(entity)
        return Slot(entity,requireNotNull(entity.components.get(TransformComponent::class.java)),material)
    }
    fun dispose() {
        entities.forEach { it.destroy(true) }
        meshes.forEach { it?.close() };meshes.fill(null)
        materials.forEach { it?.close() }
        materials.fill(null);components.fill(null)
        slots.values.flatten().forEach { it.entity.destroy(true);it.material.close() }
        slots.clear()
        rigidMeshes.values.forEach { it.close() };rigidMeshes.clear()
        lit.reset();unlit.reset()
    }
    private companion object {
        const val VERTEX_STRIDE=40
        const val DYNAMIC_VERTICES=32768
        const val DYNAMIC_INDICES=196608
    }
}
internal class SkillDrawing(
    private val mesh: SkillLayerMesh,
    val dynamicEnabled: Boolean=true,
    private val rigidCommands: MutableList<SkillRigid>?=null,
) {
    val rigidEnabled get()=rigidCommands!=null
    fun ball(p: P,r: Double,c: Tint,a: Double=1.0,geo: String="sphere",scale: P=v(r,r,r),rotation: P=v()) {
        if(!rigidEnabled) return
        rigidCommands!!.add(SkillRigid(geo,p,scale,quatXYZ(rotation.x,rotation.y,rotation.z),c,clamp(a)))
    }
    fun dynamic(verts: List<P>,indices: List<Int>,colors: List<Tint>,a: Double=1.0) {
        if(!dynamicEnabled) return
        val b=mesh.unlit;val start=b.positions.size
        verts.forEachIndexed { i,p -> b.vertex(p,colors[i],a) }
        for(i in indices.indices step 3) b.tri(start+indices[i],start+indices[i+1],start+indices[i+2])
    }
    fun tube(pts: List<P>,r: Double,c: Tint,a: Double=1.0,sides: Int=6,rEnd: Double=.4) {
        if(!dynamicEnabled) return
        if(pts.size<2) return
        val b=mesh.unlit;val start=b.positions.size
        val ring=unitRings.getOrPut(sides) { makeRing(sides) }
        val shades=ring.map { c*(.72+.28*it.first) }
        pts.forEachIndexed { i,p ->
            val tangent=(pts[min(i+1,pts.lastIndex)]-pts[max(i-1,0)]).unit()
            var side=v(0,1,0).cross(tangent).unit()
            if(side.length()<.1) side=v(0,0,1).cross(tangent).unit()
            val up=tangent.cross(side).unit();val u=i.toDouble()/pts.lastIndex;val rr=r*(1-(1-rEnd)*u)
            repeat(sides) { j ->
                val (cosine,sine)=ring[j]
                b.vertex(p+side*(cosine*rr)+up*(sine*rr),shades[j],a)
                if(i<pts.lastIndex) {
                    val n=start+i*sides+j;val k=start+i*sides+(j+1)%sides
                    b.tri(n,k,n+sides);b.tri(k,k+sides,n+sides)
                }
            }
        }
    }
    fun strip(pts: List<P>,w: Double,c: Tint,a: Double=1.0,normal: P=v(0,.3,1)) {
        if(!dynamicEnabled) return
        if(pts.size<2) return
        val b=mesh.unlit;val start=b.positions.size
        pts.forEachIndexed { i,p ->
            val u=i.toDouble()/pts.lastIndex
            val side=(pts[min(i+1,pts.lastIndex)]-pts[max(i-1,0)]).unit().cross(normal).unit()
            val width=w*max(0.0,sin(PI*u)).pow(.65)
            val shade=c*(.7+.3*sin(PI*u))
            b.vertex(p+side*(width*-1),shade,a);b.vertex(p+side*width,shade,a)
            if(i<pts.lastIndex) { val k=start+i*2;b.tri(k,k+1,k+2);b.tri(k+1,k+3,k+2) }
        }
    }
    fun surface(cols: Int,rows: Int,fn: (Double,Double)->P,colorFn: (Double,Double,P)->Tint,a: Double=.6) {
        if(!dynamicEnabled) return
        val b=mesh.unlit;val start=b.positions.size
        for(i in 0..cols) for(j in 0..rows) {
            val u=i.toDouble()/cols;val vv=j.toDouble()/rows;val p=fn(u,vv)
            b.vertex(p,colorFn(u,vv,p),a)
            if(i<cols&&j<rows) { val k=start+i*(rows+1)+j;b.tri(k,k+1,k+rows+1);b.tri(k+1,k+rows+2,k+rows+1) }
        }
    }
    fun arc(c: P,r: Double,w: Double,color: Tint,a: Double=1.0,plane: String="xy",start: Double=0.0,end: Double=TAU) {
        if(!dynamicEnabled) return
        val pts=List(65) { i ->
            val angle=start+(end-start)*i/64
            c+when(plane) { "xz"->v(cos(angle)*r,0,sin(angle)*r);"yz"->v(0,cos(angle)*r,sin(angle)*r);else->v(cos(angle)*r,sin(angle)*r,0) }
        }
        tube(pts,w,color,a,4,1.0)
    }
    fun leaf(p: P,r: Double,c: Tint,angle: Double=0.0,unfold: Double=1.0) {
        if(!dynamicEnabled) return
        val dir=v(cos(angle),sin(angle),0)
        val pts=List(37) { i -> p+dir*(i/36.0*r)+v(0,0,sin(i/36.0*PI)*.04*(1-unfold)) }
        strip(pts,r*.22*unfold,c,.92,v(0,0,1));tube(pts,r*.008,tint("#e8ffbc"),.85,4,.25)
    }
    fun seed(p: P,r: Double,angle: Double) {
        ball(p,r,tint("#986242"),geo="seed",scale=v(r*.8,r,r*1.25),rotation=v(angle,0,angle*.6))
        ball(p+v(0,r,.02),r*.2,tint("#76d96a"),geo="cone",scale=v(r*.2,r*.35,r*.2),rotation=v(0,0,-.4))
        repeat(3) { k -> arc(p,r*.83,.004,tint("#cfad75"),.9,"xy",k*2.1,k*2.1+1.5) }
    }
    fun rock(p: P,r: Double,angle: Double=0.0) {
        ball(p,r,tint("#9c866b"),geo="rock",scale=v(r,r*.78,r*.9),rotation=v(angle*.7,angle,.4))
        repeat(3) { i ->
            tube(List(8) { k -> p+v((k/7.0-.5)*r*1.4,sin(k*1.1+i)*r*.23,r*.83) },.008,tint("#dbc399"),.75,3,.5)
        }
    }
    /** CPU equivalent of the approved point shader, on a camera-facing grid. */
    fun point(p: P,c: Tint,size: Double,style: String,right: P,up: P) {
        if(!rigidEnabled) return
        rigidCommands!!.add(SkillRigid("point:$style",p,v(size,size,size),basisRotation(right,up),c,1.0))
    }
    fun bubble(center: P,r: Double,time: Double,viewer: P) {
        if(!dynamicEnabled) return
        val g=SkillPrimitives["sphere"];val b=mesh.unlit;val first=b.positions.size
        g.points.forEachIndexed { i,q ->
            val deform=1+.025*sin(q.y*12+time*4)*sin(q.x*9-time*2)
            val pos=center+q*(r*deform)
            val f=(1-abs(g.normals[i].unit().dot((viewer-pos).unit()))).pow(2)
            // Shader colors are already linear RGB.
            b.vertex(pos,Tint(.08,.45,.72).lerp(Tint(.87,1.0,1.0),f),(.05+f*.72)*.55,g.normals[i])
        }
        for(i in g.indices.indices step 3) b.tri(first+g.indices[i],first+g.indices[i+1],first+g.indices[i+2])
    }
    companion object {
        private fun makeRing(sides: Int) =
            List(sides) { j -> (j.toDouble()/sides*TAU).let { cos(it) to sin(it) } }
        private val unitRings=java.util.concurrent.ConcurrentHashMap<Int,List<Pair<Double,Double>>>().apply {
            listOf(3,4,5,6,8,12).forEach { put(it,makeRing(it)) }
        }
        fun pointTemplate(style: String,buffer: SkillBuffer) {
            val alpha=pointAlpha.getValue(style)
            for(i in 0..8) for(j in 0..8)
                buffer.vertex(v((i*.25-1)*.5,(j*.25-1)*.5,0),Tint(1.0,1.0,1.0),alpha[i*9+j])
            pointIndices.forEach { buffer.indices.add(it) }
        }
        private fun basisRotation(right: P,up: P): Quat {
            val forward=right.cross(up)
            val m00=right.x;val m11=up.y;val m22=forward.z
            val trace=m00+m11+m22
            var x: Double;var y: Double;var z: Double;var w: Double
            if(trace>0) {
                val s=sqrt(trace+1)*2
                w=.25*s;x=(up.z-forward.y)/s;y=(forward.x-right.z)/s;z=(right.y-up.x)/s
            } else if(m00>m11&&m00>m22) {
                val s=sqrt(1+m00-m11-m22)*2
                w=(up.z-forward.y)/s;x=.25*s;y=(up.x+right.y)/s;z=(forward.x+right.z)/s
            } else if(m11>m22) {
                val s=sqrt(1+m11-m00-m22)*2
                w=(forward.x-right.z)/s;x=(up.x+right.y)/s;y=.25*s;z=(forward.y+up.z)/s
            } else {
                val s=sqrt(1+m22-m00-m11)*2
                w=(right.y-up.x)/s;x=(forward.x+right.z)/s;y=(forward.y+up.z)/s;z=.25*s
            }
            return Quat(x.toFloat(),y.toFloat(),z.toFloat(),w.toFloat())
        }
        private val pointAlpha=listOf("glow","drop","spark").associateWith { style ->
            DoubleArray(81) { k ->
                val x=(k/9)*.25-1;val y=(k%9)*.25-1
                val alpha=when(style) {
                    "drop"-> { val q=clamp((sqrt((x*1.8).pow(2)+(y*.75).pow(2))-1)/(.15-1));q*q*(3-2*q) }
                    "spark"->max(max(0.0,1-abs(x)).pow(9)*max(0.0,1-abs(y)),max(0.0,1-abs(y)).pow(9)*max(0.0,1-abs(x)))
                    else->max(0.0,1-sqrt(x*x+y*y)).pow(1.5)
                }
                alpha*.92
            }
        }
        private val pointIndices=IntArray(8*8*6) { k ->
            val cell=k/6;val i=cell/8;val j=cell%8;val n=i*9+j
            n+intArrayOf(0,1,9,1,10,9)[k%6]
        }
    }
}
