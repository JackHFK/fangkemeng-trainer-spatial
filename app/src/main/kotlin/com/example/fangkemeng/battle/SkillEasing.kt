package com.example.fangkemeng.battle

import kotlin.math.*

internal fun ease(name: String,input: Double): Double {
    val u=clamp(input)
    return when(name) {
        "linear"->u
        "easeInQuad"->u*u
        "easeOutQuad"->1-(1-u).pow(2)
        "easeInCubic"->u.pow(3)
        "easeOutCubic"->1-(1-u).pow(3)
        "easeInOutCubic"->if(u<.5) 4*u.pow(3) else 1-(-2*u+2).pow(3)/2
        "easeInOutSine"->-(cos(PI*u)-1)/2
        "easeOutSine"->sin(u*PI/2)
        "easeOutBack"->1+2.70158*(u-1).pow(3)+1.70158*(u-1).pow(2)
        "easeInOutBack"->if(u<.5) (2*u).pow(2)*((2.5949095+1)*2*u-2.5949095)/2
            else ((2*u-2).pow(2)*((2.5949095+1)*(u*2-2)+2.5949095)+2)/2
        "easeOutElastic"->if(u==0.0||u==1.0) u else 2.0.pow(-10*u)*sin((u*10-.75)*(2*PI/3))+1
        "easeInOutElastic"->if(u==0.0||u==1.0) u else {
            val c=2*PI/4.5
            if(u<.5) -(2.0.pow(20*u-10)*sin((20*u-11.125)*c))/2
            else 2.0.pow(-20*u+10)*sin((20*u-11.125)*c)/2+1
        }
        else->error("Unknown approved easing: $name")
    }
}
