package com.example.fangkemeng.battle

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import kotlin.math.*

internal data class SkillJoint(val entity: Entity,val position: P,val rotation: P,val scale: P,val parent: String?)
private data class JointPose(val position: P,val rotation: P,val scale: P)
internal data class SkillRootPose(
    val x: Double=SOURCE_X,val y: Double=0.0,
    val rx: Double=0.0,val ry: Double=PI/2,val rz: Double=0.0,val sy: Double=1.0,
)
internal fun rootPose(s: SkillRecipe,t: Double,targetX: Double): SkillRootPose {
    val p=ease("easeInOutCubic",t/s.cast)
    val r=ease("easeInOutCubic",(t-s.cast)/(s.hit-s.cast))
    val back=ease("easeInOutCubic",(t-s.hit-.1)/(s.end-s.hit-.25))
    var x=SOURCE_X;var y=0.0;var rx=0.0;var ry=PI/2;var rz=0.0;var sy=1.0
    if(s.action in setOf("rush","dash","claw","bite","peck","pounce","dive","tail","shell")) {
        var travel=r
        if(t>=s.hit) travel=1-back
        x+=(targetX-SOURCE_X-.42)*travel
        if(t<s.cast) x-=.07*p
        if(s.action=="pounce") y=.55*sin(PI*r)*(1-back)
        if(s.action=="dive") {
            y=if(t<s.cast) .95*ease("easeInOutSine",t/s.cast) else .95*(1-ease("easeInCubic",(t-s.cast)/(s.hit-s.cast)))
            if(t>s.hit) y=.28*sin(PI*back)
            rz=if(t>s.cast&&t<s.hit) -.6*sin(PI*r) else 0.0
        }
        if(s.action=="shell") { ry+=r*PI*5*(1-back);rx=.35*(1-back) }
        if(s.action=="tail") ry+=if(t<s.cast) .6*p else PI*r*(1-back)
    } else if(s.action=="surf") {
        val grow=ease("easeOutCubic",(t-.55)/.5);val returning=ease("easeInOutCubic",(t-s.hit)/.7)
        x+=(targetX-SOURCE_X-.45)*r*(1-returning);y=.39*grow*(1-returning)
    } else if(s.action=="cannon"||s.action=="overload") {
        x-=if(t>=s.cast) .08*sin(PI*min(1.0,(t-s.cast)/.45)) else 0.0
    }
    if(s.action=="stomp") y=if(t<s.cast) .12*p else if(t<s.cast+.16) .12*(1-ease("easeInCubic",(t-s.cast)/.16)) else 0.0
    if(s.action=="inflate") {
        val b=if(t<s.cast) ease("easeInOutSine",t/s.cast) else 1-ease("easeOutElastic",(t-s.cast)/.7)
        sy=1+.12*max(-.15,b)
    }
    return SkillRootPose(x,y,rx,ry,rz,sy)
}
/** A reversible pose track, evaluated at the effect's exact clock time. */
internal class SkillCreaturePose(val creature: BattleCreature,private val frame: SkillCastFrame,private val target: Boolean=false) {
    private val rootTransform=requireNotNull(creature.root.components.get(TransformComponent::class.java))
    private val rootPosition=creature.skillPlacement.position
    private val rootScale=creature.skillPlacement.scale.native()
    private val rootRotation=EulerAngles(0f,creature.skillPlacement.yawDeg,0f)
    var current=SkillRootPose()
        private set
    private val n=creature.joints
    private val jointPoses=mutableMapOf<SkillJoint,JointPose>()
    private fun node(vararg names: String) = names.firstNotNullOfOrNull { n[it] }
    private fun pose(j: SkillJoint?,dx: Double=0.0,dy: Double=0.0,dz: Double=0.0,
        rx: Double=0.0,ry: Double=0.0,rz: Double=0.0,sx: Double=1.0,sy: Double=1.0,sz: Double=1.0) {
        j?:return
        jointPoses[j]=JointPose(j.position+v(dx,dy,dz),
            v((j.rotation.x+rx)*PI/180,(j.rotation.y+ry)*PI/180,(j.rotation.z+rz)*PI/180),
            v(j.scale.x*sx,j.scale.y*sy,j.scale.z*sz))
    }
    private fun flushJoints() {
        jointPoses.forEach { (j,p) -> j.entity.components.get(TransformComponent::class.java)?.apply {
            setPosition(p.position.native())
            setQuaternion(quatXYZ(p.rotation.x,p.rotation.y,p.rotation.z))
            setScaleVector(p.scale.native())
        } }
    }
    fun restore() {
        n.values.forEach { pose(it) }
        flushJoints()
        rootTransform.setPosition(rootPosition.native())
        rootTransform.setScaleVector(rootScale)
        rootTransform.setEulerAngles(rootRotation)
    }
    fun socket(name: String): P {
        val j=when(name) {
            "bud"->node("bulb","crown")
            "paw"->node("pawL","frontLegL","foreLegL")
            "chest"->node("body","torso","belly")
            "head"->node("head","face","neck")
            "tail"->node("tailTip","tail")
            else->node("head","face","neck","body")
        }
        val local=v(0,if(name=="bud") .14 else 0.0,if(name=="mouth") .16 else 0.0)
        var pos=local
        var currentJoint=j
        while(currentJoint!=null) {
            val state=jointPoses[currentJoint]?:JointPose(currentJoint.position,currentJoint.rotation*(PI/180),currentJoint.scale)
            pos=rotate(v(pos.x*state.scale.x,pos.y*state.scale.y,pos.z*state.scale.z),state.rotation)+state.position
            currentJoint=currentJoint.parent?.let { n[it] }
        }
        pos=rotate(v(pos.x*rootScale.x,pos.y*rootScale.y,pos.z*rootScale.z),v(current.rx,current.ry,current.rz))
        return pos+v(current.x,current.y,0)
    }
    fun apply(s: SkillRecipe,t: Double,hit: Boolean) {
        n.values.forEach { pose(it) }
        if(t<=0||t>=s.end) { restore();current=SkillRootPose();return }
        if(target) {
            rootTransform.setPosition(rootPosition.native())
            rootTransform.setEulerAngles(rootRotation)
            if(hit&&t>=s.hit) {
                val u=(t-s.hit)/(s.end-s.hit);val w=exp(-u*7)*sin(PI*min(1.0,u*6))
                val y=if(s.action=="stomp") .07*abs(sin(u*38))*exp(-u*5) else 0.0
                rootTransform.setPosition((rootPosition+frame.along*(.08*w)+v(0,y,0)).native())
                rootTransform.setQuaternion(frame.worldRotation(0.0,-PI/2,-.12*w))
            }
            return
        }
        current=rootPose(s,t,frame.targetX)
        val p=current
        rootTransform.setPosition(frame.toWorld(v(p.x,p.y,0)).native())
        // Convert the authored XYZ rotation to the cast's world basis.
        val q=frame.worldRotation(p.rx,p.ry,p.rz)
        rootTransform.setQuaternion(q)
        val a=ease("easeInOutCubic",t/s.cast)*(1-ease("easeInOutCubic",(t-s.hit)/(s.end-s.hit)))
        val u=clamp((t-s.cast)/(s.hit-s.cast));val swing=sin(PI*u);val act=s.action
        val body=node("body","torso","belly");val head=node("head","face","neck")
        val left=node("pawL","frontLegL","foreLegL","armL","legFL","legL")
        val right=node("pawR","frontLegR","foreLegR","armR","legFR","legR")
        pose(body,dy=-.035*a,rx=-5*a,sy=p.sy);pose(head,rx=-8*a,dz=.04*swing)
        if(act in setOf("bud","whip","lob","hammer")) {
            pose(node("bulb","crown"),rx=18*a-30*swing,sy=1+.07*a)
            pose(left,rx=10*a);pose(right,rx=10*a)
        }
        if(act in setOf("rush","dash","pounce")) {
            pose(body,dy=-.06*a,rx=-12*swing,sy=if(act=="pounce") 1-.22*a+.22*swing else 1-.07*a)
            pose(left,rx=-35*swing+15*a);pose(right,rx=-30*swing+15*a)
        }
        if(act in setOf("roar","howl","screech")) {
            pose(head,rx=-(if(act=="howl") 24 else 18)*a,dz=.035*swing)
            pose(body,sx=1+(if(act=="howl") .07 else .05)*a,sz=1+.05*a)
        }
        if(act in setOf("spit","wave","vortex","surf")) {
            pose(head,dz=.06*swing,rx=-12*a)
            pose(node("finL","armL"),rz=-24*a);pose(node("finR","armR"),rz=24*a)
            pose(node("tailBase","tail"),ry=sin(t*12)*20*a);pose(n["tailTip"],ry=sin(t*12-.4)*30*a)
        }
        if(act in setOf("shell","withdraw")) {
            pose(head,dz=-.16*a,sy=1-.45*a)
            listOf(n["armL"],n["armR"],n["legL"],n["legR"]).forEachIndexed { i,j ->
                pose(j,dx=(if(i%2==1) -.1 else .1)*a,dy=.04*a,sz=1-.4*a,sy=1-.45*a)
            }
            pose(n["shell"],rx=20*a,dy=-.02*a+(if(t>s.cast) .04*sin(u*PI) else 0.0))
        }
        if(act in setOf("guard","stomp","rocks")) {
            pose(n["armL"],rx=(if(act=="guard") -25 else 45)*a,rz=if(act=="guard") 20*a else 0.0)
            pose(n["armR"],rx=(if(act=="guard") -25 else 45)*a,rz=if(act=="guard") -20*a else 0.0)
            pose(n["finL"],rx=-25*a,rz=20*a);pose(n["finR"],rx=-25*a,rz=-20*a)
            pose(left,rx=(if(act=="rocks") 25 else -20)*a)
        }
        if(act=="wings"||act=="dive") {
            val flap=if(t<s.cast) 45*a else 45-75*ease("easeInOutCubic",u)
            val recover=1-ease("easeInOutCubic",(t-s.hit)/.6)
            pose(n["wingL"],rz=-flap*recover);pose(n["wingR"],rz=flap*recover)
            if("earL" in n && "wingL" !in n) pose(body,sy=1-.15*a+.17*swing,dy=.04*swing)
        }
        if(act=="claw") {
            pose(left,rx=-35*a,ry=(-25+65*ease("easeInCubic",u))*a,dz=.08*swing)
            pose(body,ry=-12*a+24*swing)
        }
        if(act=="bite") {
            pose(head,rx=-15*a,dz=.09*swing);pose(n["jaw"],rx=24*a*(1-swing))
            pose(n["antennaL"],rx=20*a);pose(n["antennaR"],rx=20*a)
        }
        if(act=="peck") {
            val w=if(t<s.cast) 0.0 else max(0.0,sin((t-.89)*PI/.18+.5*PI))*(if(t<s.hit+.1) 1 else 0)
            pose(head,rx=22*a,dz=.06*w);pose(n["wingL"],rz=20*a);pose(n["wingR"],rz=-20*a)
        }
        if(act=="tail") {
            pose(node("tailBase","tail"),ry=(55-110*ease("easeInOutCubic",u))*a)
            pose(n["tailTip"],ry=(55-110*ease("easeInOutCubic",max(0.0,u-.09/(s.hit-s.cast))))*a)
        }
        if(act in setOf("breath","cannon")) {
            pose(node("neck","head"),dz=-.035*a+.11*swing,rx=-16*a);pose(n["jaw"],rx=24*a)
            pose(body,sx=1+.04*a,sz=1+.04*a,dy=-.045*a)
            pose(node("wingL","finL"),rz=-24*a);pose(node("wingR","finR"),rz=24*a)
        }
        if(act=="electric"||act=="overload") {
            pose(n["tailBase"],rx=-55*a);pose(n["tailTip"],rx=-30*a)
            pose(n["antennaL"],rz=18*a);pose(n["antennaR"],rz=-18*a)
            val flutter=if(act=="overload") sin(t*(18+8*a))*20*a else 0.0
            pose(n["wingL"],rz=-flutter);pose(n["wingR"],rz=flutter)
        }
        if(act=="inflate") {
            pose(body,sx=p.sy,sy=p.sy,sz=p.sy);pose(n["earL"],rz=-15*a);pose(n["earR"],rz=15*a)
        }
        flushJoints()
    }
}
