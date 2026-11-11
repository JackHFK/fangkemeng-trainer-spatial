package com.example.fangkemeng.battle

import com.example.fangkemeng.model.Element
import kotlin.math.*

/** Direct port of the approved effects.js geometry and particle equations. */
internal class SkillLayerRenderer(
    private val s: SkillRecipe,private val frame: SkillCastFrame,
    private val player: SkillCreaturePose?,private val origin: P,
    private val sockets: Map<String,P>?=null,
) {
    var viewer=v(0,1.6,3)
    private val targetX=frame.targetX
    private val target=v(targetX,.42,0)
    private val colors=when(s.element) {
        Element.GRASS->listOf("#e9ffc0","#74db66","#2b7242")
        Element.WATER->listOf("#e7ffff","#49c8ee","#1763b4")
        Element.FIRE->listOf("#fff3ae","#ff9e36","#da3926")
        Element.ELECTRIC->listOf("#fffadb","#f8dc53","#a899ff")
        Element.ROCK->listOf("#efe0b7","#bca176","#665746")
        Element.NORMAL->listOf("#fffaed","#d8d9d8","#87969c")
    }.map(::tint)
    private fun body(t: Double) = rootPose(s,t,targetX)
    private fun socket(name: String) = sockets?.get(name)?:player?.socket(name)?:origin
    private fun anchor(l: SkillLayer,t: Double): P = when(l.anchor) {
        "target"->target
        "body"->body(t).let { v(it.x,.32+it.y,0) }
        else->socket(l.socket)
    }
    private fun travel(l: SkillLayer,u: Double,offset: Double=0.0): P {
        val p=mix(origin,target,u)
        return p+v(0,l.height*4*u*(1-u),offset)
    }
    private fun wave(l: SkillLayer,t: Double,u: Double,vv: Double): P {
        val move=ease(l.ease,(t-s.cast)/(s.hit-s.cast))
        val fade=1-ease("easeInOutCubic",(t-s.hit)/(l.end-s.hit))
        val span=(s.cast-l.start).let { if(it==0.0) .5 else it }
        val grow=ease("easeOutCubic",(t-l.start)/min(.5,span))
        val peak=if(l.mode=="surf") SOURCE_X+.57+(targetX-SOURCE_X-.45)*move*fade else SOURCE_X+(targetX-SOURCE_X)*move*fade
        val x=peak-.92+1.15*u+.18*sin(PI*u).pow(2)
        val crest=max(0.0,sin(PI*u)).pow(1.7)
        return v(x,.025+l.radius*crest*fade*grow*(.88+.12*cos(vv*TAU*2+t*4)),(vv-.5)*l.width)
    }
    private fun vortex(l: SkillLayer,t: Double,u: Double,vv: Double,fade: Double=1.0): P {
        val a=u*TAU+t*TAU*1.5
        val r=(.18+(l.radius-.18)*vv*vv)*(1+.05*sin(a*3-vv*12))
        return v(cos(a)*r,.025+vv*l.width*fade,sin(a)*r)
    }
    private fun particles(l: SkillLayer,t: Double,d: SkillDrawing) {
        val base=anchor(l,t);val age=t-l.start;val life=l.life;val span=l.end-l.start
        val seed=s.key.sumOf { it.code };val r=l.radius;val mode=l.mode
        val continuous=mode in setOf("trail","crest","beam","vortex","spray","orbit","groundTravel")
        val emission=max(.05,(l.emitEnd?: (l.start+span*(if(mode=="gather") 1.0 else .6)))-l.start)
        repeat(l.count) { i ->
            val j=i+seed;val u0=rand(j);val v0=rand(j,1);val w0=rand(j,2);val a0=u0*TAU
            var birth=if(continuous) (floor(age/life)-1)*life+i.toDouble()/l.count*life else i.toDouble()/l.count*(if(mode=="burst") min(.18,span*.2) else emission)
            if(mode=="gather") {
                val rate=if(l.batch>0&&l.interval>0) l.batch/l.interval else l.count/span
                val seq=i+floor((age*rate-i)/l.count)*l.count
                if(seq<0) return@repeat
                birth=if(l.batch>0&&l.interval>0) floor(seq/l.batch)*l.interval+(seq%l.batch)*.002 else seq/rate
            }
            if(continuous&&age-birth>life) birth+=life
            val a=age-birth
            if(a<0||a>life||birth>emission) return@repeat
            val u=a/life;val fade=1-ease("easeOutQuad",(u-.65)/.35)
            var p=base;var c=colors[1];var size=.025+v0*.025
            when(mode) {
                "gather"-> {
                    var start=base+v(cos(a0)*r,sin(a0)*r,(w0-.5)*r);var to=base
                    if(l.anchor=="target") { start+=v(0,1.1,0);to+=v(0,1.1,0) }
                    p=mix(start,to,ease(l.ease,u))+v(0,sin(u*PI)*r*.22,0)
                    size=.018+v0*.012;c=colors[if(i%4==0) 0 else 1]
                }
                "burst"-> {
                    val pulse=if(l.pulses>0) i%l.pulses else 0
                    val delay=pulse*(if(l.interval>0) l.interval else .18)
                    val aa=a-delay
                    if(aa<0||aa>life) return@repeat
                    val vv=aa/life;val rad=r*(.2+.8*v0)*ease("easeOutQuad",vv)
                    p+=v(cos(a0)*rad,.15+(.4+w0)*r*vv-1.1*vv*vv,sin(a0)*rad)
                    if(p.y<.02) p=v(p.x,.02+abs(p.y)*.25,p.z)
                }
                "trail"-> {
                    val bt=max(s.cast,t-a);val q=ease("easeInOutCubic",(bt-s.cast)/(s.hit-s.cast))
                    p=if(l.anchor=="body") body(bt).let { v(it.x,.14+it.y,0) } else travel(l,q)
                    p+=v(-a*.3,(v0-.5)*r-a*.22,(w0-.5)*r);size=.014+v0*.02
                }
                "crest"-> {
                    p=wave(s.layers.firstOrNull { it.kind=="wave" }?:l,t,.65+u0*.15,v0)+v(a*.1,a*.22-a*a*.7,(w0-.5)*.12)
                    c=tint("#e5ffff");size=.02+v0*.018
                }
                "beam"-> {
                    val front=ease("easeOutCubic",(t-s.cast)/(s.hit-s.cast))
                    val ax=min(front,clamp(u0+a*.9));val theta=a0+t*TAU*1.8;val rad=r*(.3+.5*v0)+a*.08
                    p=mix(origin,target,ax)+v(0,cos(theta)*rad,sin(theta)*rad)
                    if(l.shape=="ember") p+=v(0,a*.28,0)
                    size=.015+v0*.027
                }
                "vortex"-> {
                    p=v(targetX,0,0)+vortex(s.layers.firstOrNull { it.kind=="vortex" }?:l,t,u0,clamp(v0+u*.55))
                    c=tint(if(i%3!=0) "#b2f3ff" else "#fffcec");size=.015+v0*.025
                }
                "spray"-> { p+=v((u0-.5)*r*2*u,(.3+w0)*r*2*u-1.4*u*u,sin(a0)*r*u);size=.02+v0*.04 }
                "orbit"-> { val angle=a0+t*5;p+=v(cos(angle)*r,sin(angle)*r*.65,(w0-.5)*r);size=.018+v0*.01 }
                "ground","groundTravel"-> {
                    val center=if(mode=="groundTravel") SOURCE_X+(targetX-SOURCE_X)*clamp((t-s.cast)/(s.hit-s.cast)) else base.x
                    p=v(center+cos(a0)*r*u,.03+sin(u*PI)*.14,sin(a0)*r*u)
                    c=tint(if(l.shape=="foam") "#eeffff" else "#c4b49d");size=.045+v0*.04
                }
                "rise"-> { p+=v(cos(a0)*r*v0,u*.6,sin(a0)*r*v0);size=.025;c=tint(if(i%3!=0) "#ffaf40" else "#fff2b0") }
                "travel"-> { p=travel(l,clamp((t-s.cast)/(s.hit-s.cast)),u0*r-r*.5);p=v(p.x,.04+v0*.18,p.z) }
                else-> {
                    p+=v((u0-.5)*r*2,.05+(v0-.5)*r+u*.22,(w0-.5)*r*2)
                    c=tint(when(s.element) { Element.FIRE->"#938681";Element.WATER->"#b5d4d5";else->"#b9ad99" });size=.08+v0*.08
                }
            }
            if(l.shape in setOf("rock","wood","seed","leaf","feather")) {
                if(l.shape=="leaf"||l.shape=="feather") d.leaf(p,.055+v0*.04,if(l.shape=="feather") tint("#e5e4d9") else colors[1],a0+t*2,.9)
                else {
                    val geo=when(l.shape) { "wood"->"box";"seed"->"seed";else->"rock" }
                    val col=tint(when(l.shape) { "wood"->"#ae7950";"seed"->"#b69b65";else->"#a2947f" })
                    val radius=.018+v0*.035
                    d.ball(p,radius,col,fade*.92,geo,if(l.shape=="wood") v(.045,.014,.018) else v(radius,radius,radius),v(t*2+a0,t+a0,t*1.5))
                }
            } else {
                val view=(viewer-p).unit()
                val right=v(0,1,0).cross(view).unit().let { if(it.length()<.1) v(1,0,0) else it }
                val up=view.cross(right).unit()
                d.point(p,c*fade,size*fade,if(l.shape=="drop") "drop" else if(l.shape=="spark") "spark" else "glow",right,up)
            }
        }
    }
    fun render(l: SkillLayer,t: Double,d: SkillDrawing) {
        // 同一套方程分别输出逐帧刚性变换和后台变形网格，互不等待。
        val particleRigid=l.kind=="particles"&&l.shape!="leaf"&&l.shape!="feather"
        val hasRigid=particleRigid||l.kind in rigidKinds
        if(!d.dynamicEnabled&&!hasRigid) return
        if(!d.rigidEnabled&&particleRigid) return
        val age=t-l.start;val p=clamp(age/(l.end-l.start));val base=anchor(l,t)
        val bright=colors[0];val color=colors[1];val dark=colors[2]
        val out=1-ease("easeOutQuad",(p-.65)/.35)
        when(l.kind) {
            "particles"->particles(l,t,d)
            "leaf"-> { val grow=clamp(ease(l.ease,age/.35));d.leaf(base,l.radius*grow,color,-.4,max(.08,grow)) }
            "leaves"->repeat(l.count) { i ->
                val u=if(l.mode=="travel") ease(l.ease,p) else ease(l.ease,age/.18)
                val pos=if(l.mode=="travel") travel(l,u,(i-(l.count-1)/2.0)*.1) else base+v(cos(i.toDouble()/l.count*TAU)*l.radius*u,.18*u,sin(i.toDouble()/l.count*TAU)*l.radius*u)
                d.leaf(pos,l.radius*(if(l.mode=="travel") 1.0 else out),color,if(l.mode=="travel") sin(t*9)*.1 else i.toDouble()/l.count*TAU)
            }
            "seeds"->repeat(l.count) { i ->
                val flight=if(l.flight>0) l.flight else l.end-l.start
                val u=(t-l.start-i*l.interval)/flight
                if(u in 0.0..1.0) d.seed(travel(l,ease(l.ease,u),(i-(l.count-1)/2.0)*.1),l.radius,t*3.8+i)
            }
            "orb"-> {
                val grow=ease(l.ease,age/(l.end-l.start)*.95).coerceIn(0.0,1.12)
                if(l.shape=="seed") { d.seed(base,l.radius*grow,t*.3);return }
                val center=if(s.key=="hydroCannon") base+v(l.radius*grow+.04,0,0) else base
                d.ball(center,l.radius*grow*.63,bright,.88)
                d.surface(32,20,{ u,vv ->
                    val a=u*TAU;val b=vv*PI;val r=l.radius*grow*(1+.025*sin(a*3+t*4)*sin(b*4-t*3))
                    center+v(sin(b)*cos(a)*r,cos(b)*r,sin(b)*sin(a)*r)
                },{ _,_,_->color },.22)
                repeat(3) { k -> d.arc(center,l.radius*grow*(.86+k*.04),.005,color,.55,if(k%2==1) "xy" else "yz",t+k,t+k+PI*1.4) }
            }
            "ribbon"->repeat(l.count) { k ->
                val pts=List(49) { i ->
                    val bt=t-(1-i/48.0)*(if(l.mode=="claw") .14 else .25)
                    val q=clamp((bt-s.cast)/(s.hit-s.cast))
                    when(l.mode) {
                        "body"->body(bt).let { v(it.x,it.y+.32+(k-(l.count-1)/2.0)*l.radius,.05) }
                        "tail"-> { val r=body(bt);val a=-.6+q*PI;v(r.x+cos(a)*.35,.23+sin(a)*.1,sin(a)*.35) }
                        "hammer"->v(SOURCE_X+(targetX-SOURCE_X)*q,.55+sin(q*PI)*.9,0)
                        "fall"->v(targetX-.35*(1-q),1.6*(1-q)+.2,(k-(l.count-1)/2.0)*.15)
                        "claw"-> { val r=body(bt);val a=-1.1+q*2.3;v(r.x+.25+.22*cos(a),.5+.25*sin(a),(k-1)*.05) }
                        "whip"-> { val a=q*TAU;v(SOURCE_X+(targetX-SOURCE_X)*q,.6+.4*sin(a+k),cos(a)*.15) }
                        "wings"->base+v(i/48.0*.25,sin(i/48.0*PI)*.12,(if(k!=0) 1 else -1)*(.2+.2*q))
                        else->travel(l,ease("easeOutQuad",q),(k-(l.count-1)/2.0)*.07)
                    }
                }
                d.strip(pts,l.width,color,.6*out)
            }
            "vines"->repeat(l.count) { k ->
                val grow=ease("easeOutCubic",age/.55);val recover=1-ease("easeInOutCubic",(t-s.hit)/(l.end-s.hit))
                val launch=ease("easeInOutCubic",(t-s.cast)/(s.hit-s.cast));val start=socket("bud")
                val end=if(l.mode=="hammer") v(targetX,.15,0) else v(targetX,.45+(if(k!=0) -.07 else .07),0)
                val pts=List(49) { i ->
                    val q=i/48.0;val phase=clamp(launch-(1-q)*.08)
                    val extent=(.65+(end.x-start.x-.65)*phase)*clamp(grow)*recover
                    start+v(extent*q,.35*sin(q*PI)*(1-phase)+sin(q*TAU-t*4+k)*.10*phase,(if(k==0) 1 else -1)*(.16*sin(q*PI)+.1*sin(q*TAU+phase*4)))
                }
                d.tube(pts,l.radius,dark,.98,6,.16);d.tube(pts.map { it+v(0,.01,.015) },l.radius*.18,color,.85,4,.15)
                for(i in 8 until 40 step 10) d.leaf(pts[i],.12,color,if(k==0) .7 else -.7,.9)
            }
            "slash","wind"->repeat(l.count) { k ->
                val q=clamp((age-k*.10)/max(.12,(l.end-l.start)*.55))
                if(q<=0) return@repeat
                val center=if(l.anchor=="target") base else travel(l,ease(l.ease,p),(k-(l.count-1)/2.0)*.12)
                val pts=List(37) { i ->
                    val a=-1.2+2.4*i/36.0*q;val angle=if(l.kind=="slash") .6 else 0.0
                    val x=sin(a)*l.radius;val y=(cos(a)-.5)*l.radius
                    center+v(x*cos(angle)-y*sin(angle),x*sin(angle)+y*cos(angle)+(k-(l.count-1)/2.0)*.1,.09)
                }
                d.strip(pts,l.width,color,out);d.strip(pts,l.width*.27,bright,out)
            }
            "hammer"-> {
                val u=clamp((t-s.cast)/(s.hit-s.cast));val lift=clamp(age/.6);val back=ease("easeInOutCubic",(t-s.hit-.12)/(l.end-s.hit-.12))
                val pos=v(SOURCE_X+.6+(targetX-SOURCE_X-.6)*ease("easeInCubic",u),.4+(.9*sin(PI*u)+.95*(1-u))*lift+.4*back,0)
                d.ball(pos,.3,tint("#a47443"),geo="box",scale=v(.62,.28,.28),rotation=v(0,0,-.3+u*1.8))
                d.tube(listOf(socket("bud"),pos+v(-.1,-.18,0)),.032,tint("#6b4d32"),sides=6,rEnd=1.0)
                repeat(3) { j -> d.arc(pos+v(0,0,.145),.045+j*.03,.004,tint("#e3c087")) }
                repeat(4) { j -> d.tube(listOf(pos+v(-.26,-.1+j*.06,.147),pos+v(.26,-.08+j*.06,.147)),.003,tint("#64452d"),.9,3,1.0) }
            }
            "armor"-> {
                val grow=ease("easeOutCubic",age/.6);val fade=1-ease("easeInOutCubic",(p-.75)/.25);val r=body(t)
                val b=v(r.x,.4+r.y,0)
                repeat(l.count) { k ->
                    val a=k.toDouble()/l.count*TAU;val radius=l.radius+.25*(1-grow);val metal=l.mode=="metal"
                    val pos=b+if(metal) v(.24+grow*.1,0,(if(k==0) 0 else if(k==1) -1 else 1)*(.28+.2*(1-grow))) else v(cos(a)*radius,sin(a)*radius*.6,sin(a+.4)*radius*.6)
                    d.ball(pos,1.0,tint(if(metal) "#7e939f" else "#927e65"),fade*.92,if(metal) "metal" else "rock",if(metal) v(1,1,1) else v(.14,.2,.09),v(0,0,if(metal) PI/2 else a))
                    d.arc(pos+v(.04,0,.06),if(metal) .21 else .10,.005,if(metal) tint("#e9cd8f") else bright,.55*fade)
                }
            }
            "ring","shock"-> {
                val q=if(l.kind=="shock") ease(l.ease,p) else if(l.mode=="compress") 1-.55*ease(l.ease,p) else ease(l.ease,age/.35)
                val c=if(l.kind=="shock"||l.mode=="ground") v(base.x,.025,0) else base
                val plane=if(l.mode=="shell"||l.mode=="compress") "yz" else "xz"
                d.arc(c,l.radius*q,l.width*(if(l.kind=="shock") 1-p*.8 else 1.0),if(l.kind=="shock") tint("#c7bca8") else color,out*.6,plane)
                if(l.kind=="shock") repeat(9) { k -> d.arc(c,l.radius*q*(.95+rand(k)*.1),l.width*.2,tint("#ece0c4"),out*.4,"xz",k*.7,k*.7+.4) }
            }
            else->renderRemaining(l,t,d,base,p,age,out,bright,color,dark)
        }
    }
    private fun renderRemaining(l: SkillLayer,t: Double,d: SkillDrawing,base: P,p: Double,age: Double,out: Double,bright: Tint,color: Tint,dark: Tint) {
        when(l.kind) {
            "sound"->repeat(l.count) { k ->
                val u=(t-l.start-k*(if(l.interval>0) l.interval else .18))/(s.hit-l.start+.3)
                if(u<0||u>1.6) return@repeat
                val travel=ease(l.ease,clamp(u));val r=l.radius*(.2+.8*travel)*(1+k*.12)
                var b=mix(origin,target,travel)
                if(l.mode=="low") b=v(b.x,.18,b.z)
                val aperture=when(l.mode) { "screech"->.48;"howl"->1.3;else->1.1 };val depth=.28/tan(aperture*.5)
                d.surface(40,8,{ a,vv -> val theta=a*TAU;val rr=r*(.72+.28*vv);b+v(-r*depth*(1-vv),sin(theta)*rr*.68,cos(theta)*rr) },{ _,vv,_->color.lerp(bright,vv*.5) },.17*(1-clamp(u-.8)))
                d.arc(b,r*.82,min(.025,l.width*.45),bright,.48*(1-clamp(u-.8)),"yz",-aperture,aperture)
                d.arc(b+v(-r*depth,0,0),r*.72,l.width*.18,color,.35*(1-clamp(u-.8)),"yz")
            }
            "bubbles"->repeat(l.count) { k ->
                val off=v((rand(k)-.5)*.12,(rand(k,1)-.5)*.35,(rand(k,2)-.5)*.55)
                val q=if(l.mode=="pop") ease(l.ease,p) else ease(l.ease,clamp((t-s.cast)/(s.hit-s.cast)))
                val center=(if(l.anchor=="target") base else travel(l,q))+off+v(0,sin(t*4+k)*.035,0)
                val r=l.radius*(.6+rand(k)*.5)*(if(l.mode=="pop") 1+p*.8 else clamp(age/.25))
                if(l.mode=="pop") { d.arc(center,r,.008,tint("#d9ffff"),out,"yz");return@repeat }
                // Same deformed sphere and Fresnel formula, evaluated at vertices.
                d.bubble(center,r,t,viewer)
                d.arc(center+v(0,0,r*.3),r*.8,.006,tint("#efffff"),.8,"xy",.6,1.8)
            }
            "pool"-> {
                val c=v(base.x,.02,0);val g=ease(l.ease,age/.5)*out
                d.surface(32,32,{ u,vv ->
                    val x=(u-.5)*2*l.radius;val z=(vv-.5)*2*l.radius;val r=hypot(x,z)
                    c+v(x,.01*sin(r*18-t*5)*g,z)
                },{ u,vv,_->tint(if(hypot(u-.5,vv-.5)<.48) "#2f9fc4" else "#4892ad") },.16*g)
                repeat(3) { k -> val q=(p+k/3.0)%1;val rr=l.radius*(if(l.mode=="return") q else 1-q);d.arc(c+v(0,.015,0),rr,.004,tint("#b7edec"),.5*g,"xz") }
            }
            "wave"-> {
                d.surface(40,24,{ u,vv->wave(l,t,u,vv) },{ _,_,pos->tint(if(pos.y>l.radius*.72) "#b1f7fa" else if(pos.y>l.radius*.3) "#38b7da" else "#1757a0") },.78*out)
                d.tube(List(49) { i->wave(l,t,.69,i/48.0) },.018,tint("#e4ffff"),.8*out,5,1.0)
            }
            "jet","helix"-> {
                val front=ease(l.ease,(t-s.cast)/(s.hit-s.cast));val tail=ease("easeOutQuad",(t-(s.hit+.5))/(l.end-s.hit-.5))
                val length=max(.01,front-tail)*(targetX-origin.x);val a=origin+v(tail*(targetX-origin.x),0,0)
                if(l.kind=="jet") {
                    d.surface(48,12,{ u,vv->a+v(u*length,cos(vv*TAU)*l.radius*(.7+.3*u)*(1+.08*sin(u*30-t*20)),sin(vv*TAU)*l.radius*(.7+.3*u)) },{ _,_,_->color },.52)
                    d.tube(listOf(a,a+v(length,0,0)),l.radius*.5,bright,.88,12,1.0)
                } else repeat(l.count) { k ->
                    val pts=List(97) { i->val u=i/96.0;val theta=u*TAU*l.turns-t*TAU*1.8+k*PI;val r=l.radius*(if(k==0) .84 else 1.28);a+v(u*length,cos(theta)*r,sin(theta)*r) }
                    d.strip(pts,l.width,if(k==0) bright else color,.68*out)
                }
            }
            "vortex"-> {
                val grow=ease(l.ease,age/.75);val collapse=1-ease("easeInOutCubic",(t-s.hit-.9)/(l.end-s.hit-.9));val c=v(targetX,0,0)
                d.surface(48,20,{ u,vv->c+vortex(l,t,u,vv,grow*collapse) },{ _,vv,_->tint(if(vv>.75) "#b5f2ff" else if(vv>.3) "#38a9d2" else "#175d9e") },.48*collapse)
                repeat(3) { k -> d.tube(List(97) { i -> c+vortex(l,t,i/96.0*1.6+k/3.0,i/96.0,grow*collapse) },.012,tint("#b6f7ff"),.75*collapse,4,1.0) }
            }
            "flash"-> {
                val duration=if(l.pulses>0) .17 else l.end-l.start
                val a=if(l.pulses>0) (age%(if(l.interval>0) l.interval else .18))/duration else p
                val r=l.radius*sin(PI*clamp(a));val pts=List(l.count*2+1) { i ->
                    val angle=i.toDouble()/(l.count*2)*TAU;val rr=if(i%2==1) r*.22 else r
                    base+v(cos(angle)*rr,sin(angle)*rr,.1)
                }
                val verts=listOf(base+v(0,0,.1))+pts;val indices=(1 until pts.size).flatMap { listOf(0,it,it+1) }
                d.dynamic(verts,indices,List(verts.size) { bright },1-clamp(a))
            }
            "jaws"-> {
                val close=ease("easeInCubic",age/(s.hit-l.start));val open=ease("easeInOutCubic",(t-s.hit-.12)/(l.end-s.hit-.12));val gap=.55*(1-close)+.07+.42*open
                repeat(2) { side ->
                    val dir=if(side!=0) 1 else -1;val rows=if(l.mode=="bug") 1 else 4
                    repeat(rows) { k ->
                        val pos=base+if(l.mode=="bug") v(0,0,dir*gap*.5) else v((k-1.5)*.13,dir*gap*.5,.05)
                        d.ball(pos,1.0,if(l.mode=="bug") color else tint("#fff3c9"),out,"cone",if(l.mode=="bug") v(.16,.035,.12) else v(.04,.14,.05),v(if(l.mode=="bug") dir*PI/2 else 0.0,0,if(side!=0) PI else 0.0))
                    }
                    d.arc(base+v(0,dir*(gap*.5+.06),0),l.radius,.02,if(l.mode=="electric") tint("#e7c448") else dark,.85*out,"xy",if(side!=0) 0.0 else PI,if(side!=0) PI else TAU)
                }
            }
            "rocks"->repeat(l.count) { k ->
                if(l.mode=="quake") {
                    val u=k.toDouble()/(l.count-1);val wave=clamp((t-s.cast)/(s.hit-s.cast));val w=sin(PI*clamp((wave-u)*6))
                    d.rock(v(SOURCE_X+(targetX-SOURCE_X)*u,.04+w*.18,(if(k%2!=0) 1 else -1)*.17),l.radius,t*.05);return@repeat
                }
                val start=s.cast+(if(l.mode=="slide") k*.16 else 0.0);val flight=if(l.mode=="slide") .55 else s.hit-s.cast
                val u=clamp((t-start)/flight);val grow=clamp((t-l.start)/.55)
                if(t>start+flight+.15) return@repeat
                val pos=v(targetX+(if(l.mode=="slide") -.6+.55*u else 0.0),.1+(if(l.mode=="slide") 1.2 else 1.6)*(1-ease("easeInQuad",u)),(k-(l.count-1)/2.0)*.17)
                d.rock(pos,l.radius*grow,t*.6+k)
            }
            "fire"-> {
                if(l.mode=="pillar") {
                    repeat(l.count) { k ->
                        val c=base+v((k-(l.count-1)/2.0)*.13,-.3,sin(k.toDouble())*.1);val h=l.radius*2*out
                        d.surface(32,8,{ u,vv->c+v(sin(vv*TAU)*(1-u)*l.radius*.25*(1+.2*sin(u*20-t*13+k)),u*h*(.9+.1*sin(vv*TAU*3+t*8+k)),cos(vv*TAU)*(1-u)*l.radius*.25) },{ u,_,_->heatColor(u) },.72*out)
                    };return
                }
                val front=ease(l.ease,(t-s.cast)/(s.hit-s.cast));val tail=clamp((t-s.hit-.35)/(l.end-s.hit-.35))
                val len=(targetX-origin.x)*max(.01,front-tail);val a=origin+v((targetX-origin.x)*tail,0,0)
                repeat(l.count) { k ->
                    val angle=k.toDouble()*TAU/l.count;val length=if(k==0) 1.0 else .82+.16*sin(t*9+k).pow(2)
                    d.surface(48,12,{ u,vv ->
                        val theta=angle+(vv-.5)*TAU/l.count*1.25;val r=l.radius*max(0.0,sin(PI*u)).pow(.65)*(.25+.75*u)*(1+.25*sin(u*24-t*16+k))
                        a+v(u*len*length,cos(theta)*r+sin(u*10-t*11+k)*u*.035,sin(theta)*r)
                    },{ u,vv,_->heatColor(clamp(u*.9+vv*.1)) },.62*(1-tail))
                }
                d.tube(List(49) { i->a+v(i/48.0*len*.72,sin(i/48.0*12-t*9)*.018*i/48.0,0) },.04,tint("#fff7c8"),.8*(1-tail),8,.1)
            }
            "bolt"-> {
                val keyframe=floor(t/.05).toInt();val front=ease(l.ease,(t-s.cast)/(s.hit-s.cast))
                repeat(l.count) { k ->
                    if(l.mode=="branch"&&(keyframe+k)%3>0) return@repeat
                    var a=origin;var b=target
                    if(l.anchor=="body"||l.mode=="local") {
                        a=base+v((rand(keyframe+k)-.5)*l.radius,sin(t*3+k)*l.radius,(rand(keyframe,3)-.5)*l.radius)
                        b=base+v((rand(keyframe+k,1)-.5)*l.radius,-sin(t*3+k)*l.radius,(rand(keyframe,4)-.5)*l.radius)
                    } else if(l.anchor=="target") { a=base+v(-.2,.15,0);b=base+v(.2,-.15,0) } else b=mix(a,b,front)
                    if(l.mode=="branch") { a=mix(a,b,.35+k*.2);b=a+v(.5,(if(k!=0) 1 else -1)*.25,.1) }
                    val pts=List(25) { i->val u=i/24.0;val seg=i/4;mix(a,b,u)+v(0,sin(PI*u)*(rand(seg+keyframe+k)-.5)*l.radius,sin(PI*u)*(rand(seg+keyframe+k,2)-.5)*l.radius) }
                    d.tube(pts,l.width,color,.28*out,4,.6);d.tube(pts,l.width*.28,bright,.95*out,4,.6)
                }
            }
            "discharge"->repeat(l.count) { k ->
                val r=.3+(l.radius-.3)*ease(l.ease,clamp((age-k*.06)/.7))
                repeat(16) { j ->
                    val theta=j/16.0*TAU
                    val pts=List(25) { i->val a=i/24.0*PI;val rr=r*(1+.04*sin(i*4+floor(t*20)));base+v(cos(a)*rr,sin(a)*cos(theta)*rr,sin(a)*sin(theta)*rr) }
                    d.tube(pts,l.width*.3,if(k==0) bright else color,.65*out,3,1.0)
                }
            }
            "quake"-> {
                val front=clamp((t-s.cast)/(s.hit-s.cast))
                repeat(l.count) { k ->
                    val pts=List(49) { i->val u=i/48.0;v(SOURCE_X+(targetX-SOURCE_X)*u,.02+.08*sin(PI*clamp((front-u)*6)),(k-(l.count-1)/2.0)*.12+sin(i*.8+k)*.05) }.filterIndexed { i,_->i/48.0<=front }
                    if(pts.size>1) { d.tube(pts,l.width*.2,tint("#ddb974"),out*.8,4,.4);d.strip(pts,l.width,tint("#5b4e40"),out,v(0,1,0)) }
                }
            }
            else->error("Unrendered approved skill layer: ${l.kind}")
        }
    }
    private companion object {
        val rigidKinds=setOf("seeds","orb","hammer","armor","jaws","rocks")
    }
}
