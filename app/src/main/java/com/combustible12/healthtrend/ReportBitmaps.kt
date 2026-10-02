package com.combustible12.healthtrend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/** The source file stays untouched. Decode a bounded, correctly oriented working bitmap. */
fun decodeReportBitmap(context:Context,uri:Uri,maxPixels:Long=8_000_000L,maxDimension:Int=4096):Bitmap {
 val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
 context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,bounds)}
 require(bounds.outWidth>0&&bounds.outHeight>0){"原图格式无法解码"}
 var sample=1
 while(bounds.outWidth.toLong()*bounds.outHeight/sample/sample>maxPixels || maxOf(bounds.outWidth,bounds.outHeight)/sample>maxDimension)sample*=2
 val bitmap=context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888})}?:error("原图无法解码")
 val orientation=runCatching{context.contentResolver.openInputStream(uri).use{ExifInterface(checkNotNull(it)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
 val matrix=Matrix()
 when(orientation){
  ExifInterface.ORIENTATION_FLIP_HORIZONTAL->matrix.setScale(-1f,1f)
  ExifInterface.ORIENTATION_ROTATE_180->matrix.setRotate(180f)
  ExifInterface.ORIENTATION_FLIP_VERTICAL->matrix.setScale(1f,-1f)
  ExifInterface.ORIENTATION_TRANSPOSE->{matrix.setRotate(90f);matrix.postScale(-1f,1f)}
  ExifInterface.ORIENTATION_ROTATE_90->matrix.setRotate(90f)
  ExifInterface.ORIENTATION_TRANSVERSE->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)}
  ExifInterface.ORIENTATION_ROTATE_270->matrix.setRotate(-90f)
 }
 if(matrix.isIdentity)return bitmap
 return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also{if(it!==bitmap)bitmap.recycle()}
}
