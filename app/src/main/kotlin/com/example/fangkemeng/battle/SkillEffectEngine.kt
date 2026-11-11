package com.example.fangkemeng.battle

import com.example.fangkemeng.model.Move
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

/**
 * ECS 驱动姿态、刚性粒子、命中和时钟；后台计算变形网格并上传网格与材质颜色。
 * 待处理队列只保存最新一帧，慢上传不会积累历史帧或阻塞交互。
 */
internal class SkillEffectEngine(private val content: SpatialViewContent) {
    private sealed interface Work { val epoch: Long }
    private data class Snapshot(
        override val epoch: Long,val recipe: SkillRecipe,val frame: SkillCastFrame,
        val origin: P,val sockets: Map<String,P>,val viewer: P,
        val time: Double,val landed: Boolean,val colors: List<SkillMaterialUpdate>,
    ): Work
    private data class Presented(val epoch: Long,val time: Double,val drawing: SkillPresentation)
    private val executor=Executors.newSingleThreadExecutor { task ->
        Thread(task,"FangkemengVfxWorker").apply { isDaemon=true }
    }
    private val pending=AtomicReference<Work?>()
    private val draining=AtomicBoolean()
    private val epoch=AtomicLong()
    @Volatile private var presented: Presented?=null
    @Volatile private var failure: Throwable?=null
    @Volatile private var disposed=false
    @Volatile private var published: Presented?=null
    private var root: Entity?=null
    private var mesh: SkillLayerMesh?=null
    private var recipe: SkillRecipe?=null
    private var frame: SkillCastFrame?=null
    private var sourcePose: SkillCreaturePose?=null
    private var targetPose: SkillCreaturePose?=null
    private var origin=v()
    private var age=0.0
    private var hit=false
    private var impactResolved=false
    private var externallyTimed=false
    private val rigidCommands=ArrayList<SkillRigid>()
    init {
        ensureResources()
        // 与后续上传同一个串行工作线程，技能释放前完成资源分配。
        executor.execute {
            try { mesh?.prepareDynamic() }
            catch(error: Throwable) { failure=error }
        }
    }
    var viewPosition=v(0,1.6,0)
    val active get()=recipe!=null
    fun controls(creature: BattleCreature?) =
        creature!=null&&(sourcePose?.creature===creature||targetPose?.creature===creature)

    fun launch(move: Move,from: Vector3,to: Vector3,intensity: Float=1f,
        source: BattleCreature?=null,target: BattleCreature?=null,externalClock: Boolean=false) {
        check(!disposed)
        clear()
        val s=SkillVfx.recipe(move)
        val sourceRoot=source?.skillPlacement?.position?:v(from.x,0,from.z)
        val targetRoot=target?.skillPlacement?.position?:v(to.x,0,to.z)
        val f=SkillCastFrame(sourceRoot,targetRoot)
        val owner=requireNotNull(root)
        owner.components.get(TransformComponent::class.java)?.apply {
            setPosition(f.toWorld(v()).native())
            setEulerAngles(EulerAngles(0f,(atan2(-f.along.z,f.along.x)*180/PI).toFloat(),0f))
        }
        sourcePose=source?.let { SkillCreaturePose(it,f) }
        targetPose=target?.let { SkillCreaturePose(it,f,true) }
        sourcePose?.apply(s,0.0,false)
        origin=sourcePose?.socket("mouth")?:f.fromWorld(from.point())
        recipe=s;frame=f;age=0.0;hit=false;impactResolved=false
        externallyTimed=externalClock
        render()
    }
    fun impact(move: Move) = resolveImpact(move,true)
    fun resolveImpact(move: Move,landed: Boolean) {
        if(recipe?.name!=move.name) return
        hit=landed;impactResolved=true
        render()
    }
    fun tick(dt: Float) {
        prewarm()
        publish()
        val s=recipe
        if(s!=null&&!externallyTimed) {
            age+=dt.toDouble()
            if(age>=s.end) clear() else render()
        }
        // 唯一提交入口是 ECS 帧。launch/impact/seek/clear 只更新最新快照，
        // 不会在一帧内连续申请同一映射缓冲。
        schedule()
    }
    fun advanceTo(seconds: Double) {
        publish()
        val s=recipe?:return
        age=seconds
        if(age>=s.end) clear() else render()
    }
    private fun publish() {
        failure?.let { throw IllegalStateException("Skill geometry worker failed",it) }
        val ready=presented
        if(recipe!=null&&ready?.epoch==epoch.get()&&ready!==published) {
            android.os.Trace.beginSection("FangkemengVfx.bind")
            try { mesh?.bind(ready.drawing) }
            finally { android.os.Trace.endSection() }
            root?.enabled=true
            published=ready
        }
    }
    private fun ensureResources() {
        val owner=Entity()
        owner.enabled=false;content.addEntity(owner);root=owner
        mesh=SkillLayerMesh(owner,"all-layers-${System.identityHashCode(this)}")
    }
    private fun prewarm() {
        android.os.Trace.beginSection("FangkemengVfx.prewarm")
        try { mesh?.prewarm(recipe) }
        finally { android.os.Trace.endSection() }
    }
    private fun render() {
        publish()
        val s=recipe?:return;val f=frame?:return
        android.os.Trace.beginSection("FangkemengVfx.pose")
        try { sourcePose?.apply(s,age,hit);targetPose?.apply(s,age,hit&&impactResolved) }
        finally { android.os.Trace.endSection() }
        // 所有关节数据在 ECS 线程读取，后台不访问可变的精灵姿态。
        val sockets=(s.layers.map { it.socket }+listOf("mouth","bud")).distinct()
            .associateWith { sourcePose?.socket(it)?:origin }
        val viewer=f.fromWorld(viewPosition)
        val layerMesh=requireNotNull(mesh)
        val renderer=SkillLayerRenderer(s,f,null,origin,sockets).also { it.viewer=viewer }
        rigidCommands.clear()
        val drawing=SkillDrawing(layerMesh,dynamicEnabled=false,rigidCommands=rigidCommands)
        val colors: List<SkillMaterialUpdate>
        android.os.Trace.beginSection("FangkemengVfx.rigid")
        try {
            s.layers.forEach { l ->
                if(layerActive(s,l,age,hit&&impactResolved)) {
                    renderer.render(l,age,drawing)
                }
            }
            colors=layerMesh.bindRigid(rigidCommands)
            root?.enabled=true
        } finally { android.os.Trace.endSection() }
        pending.set(Snapshot(epoch.get(),s,f,origin,sockets,viewer,
            age,hit&&impactResolved,colors))
    }
    private fun layerActive(s: SkillRecipe,l: SkillLayer,time: Double,landed: Boolean): Boolean {
        val onlyHit=l.anchor=="target"&&(l.start>=s.hit-.01||l.kind in hitKinds)
        return time>=l.start&&time<l.end&&(!onlyHit||landed)
    }
    private fun schedule() {
        val ready=presented
        // 主线程完成组件绑定前不覆写资源，避免上传与场景绑定争用。
        if(disposed||(ready?.epoch==epoch.get()&&ready!==published)||
            !draining.compareAndSet(false,true)) return
        executor.execute {
            try {
                val packet=pending.getAndSet(null)
                if(packet!=null&&packet.epoch==epoch.get()) {
                    when(packet) {
                        is Snapshot->draw(packet)
                    }
                }
            } catch(error: Throwable) {
                failure=error
                android.util.Log.e("FangkemengVfx","geometry worker failed",error)
            } finally {
                draining.set(false)
                // SDK 返回未接受时，留给下一次 ECS tick；禁止递归紧循环重试。
            }
        }
    }
    private fun draw(packet: Snapshot) {
        val m=mesh?:return
        val s=packet.recipe
        val renderer=SkillLayerRenderer(s,packet.frame,null,packet.origin,packet.sockets)
        renderer.viewer=packet.viewer
        m.reset()
        val drawing=SkillDrawing(m)
        s.layers.forEach { l ->
            if(layerActive(s,l,packet.time,packet.landed)) {
                android.os.Trace.beginSection("FangkemengVfx.generate.${l.kind}")
                try { renderer.render(l,packet.time,drawing) }
                finally { android.os.Trace.endSection() }
            }
        }
        if(packet.epoch!=epoch.get()) return
        android.os.Trace.beginSection("FangkemengVfx.material")
        try {
            packet.colors.forEach {
                if(packet.epoch==epoch.get()) it.target.upload(it.color)
            }
        } finally { android.os.Trace.endSection() }
        if(packet.epoch!=epoch.get()) return
        android.os.Trace.beginSection("FangkemengVfx.upload")
        val presentation: SkillPresentation?
        try { presentation=m.upload() }
        finally { android.os.Trace.endSection() }
        if(presentation==null) {
            if(packet.epoch==epoch.get()) pending.compareAndSet(null,packet)
            return
        }
        if(packet.epoch==epoch.get()) presented=Presented(packet.epoch,packet.time,presentation)
    }
    fun clear() {
        epoch.incrementAndGet()
        pending.set(null)
        presented=null;published=null
        root?.enabled=false
        mesh?.hide()
        mesh?.bindRigid(emptyList())
        sourcePose?.restore();targetPose?.restore()
        sourcePose=null;targetPose=null;recipe=null;frame=null
        // 隐藏与代际作废立即完成清理。下一次释放仍写完整快照并清零尾部；
        // 对不可见资源另做空上传没有渲染收益，且会与帧内上传争用。
    }
    fun dispose() {
        if(disposed) return
        clear();disposed=true
        // 串行队列在最后一次上传之后销毁资源，不让退出 Stage 等待后台。
        executor.execute {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                mesh?.dispose();mesh=null
                root?.destroy(true);root=null
            }
        }
        executor.shutdown()
    }
    private companion object {
        val hitKinds=setOf("slash","flash","shock")
    }
}
