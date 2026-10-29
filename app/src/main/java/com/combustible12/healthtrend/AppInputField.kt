package com.combustible12.healthtrend

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The only text-input foundation used by HealthTrend. */
@Composable
private fun HtBasicInput(
 value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,
 placeholder:(@Composable ()->Unit)?=null,prefix:(@Composable ()->Unit)?=null,
 leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,
 enabled:Boolean=true,readOnly:Boolean=false,singleLine:Boolean=false,minLines:Int=1,maxLines:Int=if(singleLine)1 else Int.MAX_VALUE,
 visualTransformation:VisualTransformation=VisualTransformation.None,
 keyboardOptions:KeyboardOptions=KeyboardOptions.Default,keyboardActions:KeyboardActions=KeyboardActions.Default,
 textStyle:TextStyle=LocalTextStyle.current.copy(color=Ink,fontSize=16.sp),containerColor:Color=Color.White,
 shape:Shape=RoundedCornerShape(14.dp),contentPadding:PaddingValues=PaddingValues(horizontal=16.dp,vertical=14.dp),
 minHeight:Dp=56.dp,onFocusedChange:((Boolean)->Unit)?=null,
){
 val interaction=remember{MutableInteractionSource()}
 val focused by interaction.collectIsFocusedAsState()
 LaunchedEffect(focused){onFocusedChange?.invoke(focused)}
 Row(modifier.fillMaxWidth().defaultMinSize(minHeight=minHeight).background(containerColor,shape).alpha(if(enabled)1f else .55f).padding(contentPadding),verticalAlignment=if(singleLine)Alignment.CenterVertically else Alignment.Top){
  leadingIcon?.invoke();if(leadingIcon!=null)Spacer(Modifier.width(10.dp))
  prefix?.invoke();if(prefix!=null)Spacer(Modifier.width(6.dp))
  Box(Modifier.weight(1f),contentAlignment=Alignment.CenterStart){
   if(value.isEmpty()&&placeholder!=null)placeholder()
   BasicTextField(value=value,onValueChange=onValueChange,modifier=Modifier.fillMaxWidth(),enabled=enabled,readOnly=readOnly,textStyle=textStyle,keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,singleLine=singleLine,minLines=minLines,maxLines=maxLines,visualTransformation=visualTransformation,interactionSource=interaction,cursorBrush=SolidColor(Accent))
  }
  trailingIcon?.invoke()
 }
}

/** Standard field: its label always remains outside the solid input surface. */
@Composable fun AppInputField(
 value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,label:String?=null,
 placeholder:(@Composable ()->Unit)?=null,prefix:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,
 readOnly:Boolean=false,enabled:Boolean=true,singleLine:Boolean=false,minLines:Int=1,maxLines:Int=if(singleLine)1 else Int.MAX_VALUE,
 visualTransformation:VisualTransformation=VisualTransformation.None,
 keyboardOptions:KeyboardOptions=KeyboardOptions.Default,keyboardActions:KeyboardActions=KeyboardActions.Default,
 containerColor:Color=Color.White,isError:Boolean=false,minHeight:Dp=56.dp,onFocusedChange:((Boolean)->Unit)?=null,
){
 Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){
  if(label!=null)Text(label,color=if(isError)Bad else Muted,fontSize=13.sp)
  HtBasicInput(value,onValueChange,placeholder=placeholder,prefix=prefix,trailingIcon=trailingIcon,readOnly=readOnly,enabled=enabled,singleLine=singleLine,minLines=minLines,maxLines=maxLines,visualTransformation=visualTransformation,keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,containerColor=if(isError)Color(0xFFFFF1F1) else containerColor,minHeight=minHeight,onFocusedChange=onFocusedChange)
 }
}

/** Borderless search field with an optional search icon and clear action. */
@Composable fun AppSearchField(value:String,onValueChange:(String)->Unit,hint:String,modifier:Modifier=Modifier,height:Dp=48.dp,showSearchIcon:Boolean=false,shape:Shape=RoundedCornerShape(14.dp)){
 HtBasicInput(value,onValueChange,modifier,placeholder={Text(hint,color=Muted,fontSize=14.sp)},leadingIcon=if(showSearchIcon){{Icon(Icons.Outlined.Search,null,tint=Muted,modifier=Modifier.size(20.dp))}}else null,trailingIcon=if(value.isNotEmpty()){{IconButton({onValueChange("")},modifier=Modifier.size(28.dp)){Icon(Icons.Outlined.Clear,"清空搜索",modifier=Modifier.size(18.dp),tint=Muted)}}}else null,singleLine=true,textStyle=LocalTextStyle.current.copy(fontSize=14.sp,color=Ink),shape=shape,contentPadding=PaddingValues(horizontal=12.dp,vertical=0.dp),minHeight=height)
}

/** Compact numeric field used by the existing blood-pressure grid. */
@Composable fun AppCompactInput(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,hint:String="",containerColor:Color=Color.White,textStyle:TextStyle=LocalTextStyle.current.copy(fontSize=17.sp,color=Ink)){
 HtBasicInput(value,onValueChange,modifier,placeholder=if(hint.isNotBlank()){{Text(hint,fontSize=12.sp,color=Muted)}}else null,singleLine=true,textStyle=textStyle,containerColor=containerColor,shape=RoundedCornerShape(9.dp),contentPadding=PaddingValues(horizontal=5.dp,vertical=0.dp),minHeight=(38f*2f/3f).dp)
}

/** Inline editor for an existing solid container. */
@Composable fun AppInlineInput(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,placeholder:(@Composable ()->Unit)?=null,singleLine:Boolean=false,minLines:Int=1,maxLines:Int=if(singleLine)1 else Int.MAX_VALUE,textStyle:TextStyle=LocalTextStyle.current.copy(fontSize=16.sp,color=Ink)){
 HtBasicInput(value,onValueChange,modifier,placeholder=placeholder,singleLine=singleLine,minLines=minLines,maxLines=maxLines,textStyle=textStyle,shape=RoundedCornerShape(0.dp),contentPadding=PaddingValues(0.dp),minHeight=0.dp)
}
