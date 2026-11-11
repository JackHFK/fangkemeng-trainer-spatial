package com.example.fangkemeng.battle

import com.pico.spatial.core.math.Quat
import kotlin.math.*

/** Rigid cast basis. Distance changes flight length, never effect thickness. */
internal class SkillCastFrame(val source: P,val destination: P) {
    val along=v(destination.x-source.x,0,destination.z-source.z).unit().let {
        if(it.length()<.1) v(1,0,0) else it
    }
    val side=along.cross(v(0,1,0))
    val targetX=SOURCE_X+v(destination.x-source.x,0,destination.z-source.z).length()
    val targetY=destination.y-source.y+.42
    private val angle=atan2(-along.z,along.x)
    fun toWorld(p: P) = source+along*(p.x-SOURCE_X)+v(0,p.y,0)+side*p.z
    fun fromWorld(p: P): P {
        val d=p-source
        return v(SOURCE_X+d.dot(along),d.y,d.dot(side))
    }
    fun worldRotation(rx: Double,ry: Double,rz: Double): Quat {
        val q=quatXYZ(rx,ry,rz)
        val x=q.x;val y=q.y;val z=q.z;val w=q.w
        val ca=cos(angle/2);val sa=sin(angle/2)
        return Quat((ca*x+sa*z).toFloat(),(ca*y+sa*w).toFloat(),(ca*z-sa*x).toFloat(),(ca*w-sa*y).toFloat())
    }
}
internal fun quatXYZ(rx: Double,ry: Double,rz: Double): Quat {
        val c1=cos(rx/2);val c2=cos(ry/2);val c3=cos(rz/2)
        val s1=sin(rx/2);val s2=sin(ry/2);val s3=sin(rz/2)
        val x=s1*c2*c3+c1*s2*s3;val y=c1*s2*c3-s1*c2*s3
        val z=c1*c2*s3+s1*s2*c3;val w=c1*c2*c3-s1*s2*s3
        return Quat(x.toFloat(),y.toFloat(),z.toFloat(),w.toFloat())
}
