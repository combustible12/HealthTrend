package com.combustible12.healthtrend

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min

/** One image gesture engine for OCR and ordinary image viewers. */
internal class ImageGestureState(
 val zoom:Float,
 val x:Float,
 val y:Float,
 val modifier:Modifier,
 val reset:()->Unit,
 val focus:(Float,Float,Float)->Unit
)

@Composable internal fun rememberImageGestureState(
 key:Any,
 viewport:IntSize,
 imageWidth:Int,
 imageHeight:Int,
 previous:()->Unit,
 next:()->Unit,
 tap:()->Unit={},
 pinchIn:()->Unit={}
):ImageGestureState{
 var zoom by rememberSaveable(key){mutableFloatStateOf(1f)}
 var x by rememberSaveable(key){mutableFloatStateOf(0f)}
 var y by rememberSaveable(key){mutableFloatStateOf(0f)}
 var swipe by remember(key){mutableFloatStateOf(0f)}
 var pinch by remember(key){mutableFloatStateOf(1f)}
 var freePan by rememberSaveable(key){mutableStateOf(false)}
 val reset={zoom=1f;x=0f;y=0f;swipe=0f;freePan=false}
 val focus:(Float,Float,Float)->Unit={z,tx,ty->zoom=z;x=tx;y=ty}
 val modifier=Modifier.pointerInput(key,viewport,imageWidth,imageHeight){
  detectTapGestures(onTap={tap()},onDoubleTap={point->
   if(zoom>1.05f)reset()
   else if(viewport.width>0&&viewport.height>0&&imageWidth>0&&imageHeight>0){
    val fit=min(viewport.width.toFloat()/imageWidth,viewport.height.toFloat()/imageHeight)
    val target=(viewport.width/(imageWidth*fit)).coerceIn(1f,32f)
    if(target>1.01f){
     val ratio=target/zoom
     zoom=target;x=0f;freePan=false;swipe=0f
     y=transformedTranslation(y,point.y-viewport.height/2f,ratio,0f)
    }
   }
  })
 }.pointerInput(key,viewport,imageWidth,imageHeight){
  detectTransformGestures{centroid,pan,scale,_->
   val fit=if(viewport.width>0&&viewport.height>0&&imageWidth>0&&imageHeight>0)min(viewport.width.toFloat()/imageWidth,viewport.height.toFloat()/imageHeight) else 1f
   val width=(imageWidth*fit).coerceAtLeast(1f)
   val maxZoom=maxOf(8f,viewport.width/width*1.5f).coerceAtMost(32f)
   val newZoom=(zoom*scale).coerceIn(1f,maxZoom)
   val pinching=abs(scale-1f)>=.015f
   val overflow=width*newZoom>viewport.width+1f
   if(pinching&&scale>1.005f&&overflow)freePan=true
   if(pinching&&!overflow)freePan=false
   if(zoom<=1.01f&&scale<.995f){
    pinch*=scale;swipe=0f
    if(pinch<=.82f){pinch=1f;pinchIn()}
   }else{
    if(scale>=1f)pinch=1f
    if(abs(pan.x)>abs(pan.y)*1.15f&&!pinching&&!freePan){
     swipe+=pan.x
     val threshold=(viewport.width*.16f).coerceIn(56f,120f)
     if(swipe>=threshold){swipe=0f;previous()}
     else if(swipe<=-threshold){swipe=0f;next()}
    }else{
     swipe=0f
     val ratio=newZoom/zoom
     val cx=centroid.x-viewport.width/2f
     val cy=centroid.y-viewport.height/2f
     x=if(freePan)transformedTranslation(x,cx,ratio,pan.x) else 0f
     y=transformedTranslation(y,cy,ratio,pan.y)
     zoom=newZoom
    }
   }
  }
 }
 return ImageGestureState(zoom,x,y,modifier,reset,focus)
}
