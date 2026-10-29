package com.combustible12.healthtrend

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * HealthTrend input rule: labels stay outside the field and fields use a solid
 * fill with no outline or focus indicator.
 */
@Composable
fun AppInputField(
 value:String,
 onValueChange:(String)->Unit,
 modifier:Modifier=Modifier,
 label:String?=null,
 placeholder:(@Composable ()->Unit)?=null,
 prefix:(@Composable ()->Unit)?=null,
 trailingIcon:(@Composable ()->Unit)?=null,
 readOnly:Boolean=false,
 enabled:Boolean=true,
 singleLine:Boolean=false,
 minLines:Int=1,
 visualTransformation:VisualTransformation=VisualTransformation.None,
 containerColor:Color=Color.White,
 onFocusedChange:((Boolean)->Unit)?=null,
){
 Column(modifier=modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){
  if(label!=null)Text(label,color=Muted,fontSize=13.sp)
  val focusModifier=if(onFocusedChange==null)Modifier else Modifier.onFocusChanged{onFocusedChange(it.isFocused)}
  TextField(
   value=value,
   onValueChange=onValueChange,
   modifier=Modifier.fillMaxWidth().then(focusModifier),
   placeholder=placeholder,
   prefix=prefix,
   trailingIcon=trailingIcon,
   readOnly=readOnly,
   enabled=enabled,
   singleLine=singleLine,
   minLines=minLines,
   visualTransformation=visualTransformation,
   shape=RoundedCornerShape(14.dp),
   colors=TextFieldDefaults.colors(
    focusedContainerColor=containerColor,
    unfocusedContainerColor=containerColor,
    disabledContainerColor=containerColor,
    errorContainerColor=containerColor,
    focusedIndicatorColor=Color.Transparent,
    unfocusedIndicatorColor=Color.Transparent,
    disabledIndicatorColor=Color.Transparent,
    errorIndicatorColor=Color.Transparent,
   ),
  )
 }
}
