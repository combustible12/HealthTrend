package com.combustible12.healthtrend

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

private val coursePhases=listOf("化疗日","恢复期","观察","其他")

@Composable fun CourseRecordsPage(
 modifier:Modifier,
 records:List<CourseRecord>,
 open:(CourseRecord)->Unit,
 add:()->Unit,
 remove:(CourseRecord)->Unit,
 view:(List<String>,Int)->Unit
){
 var filter by rememberSaveable{mutableStateOf("全部")}
 var search by rememberSaveable{mutableStateOf("")}
 val query=search.trim()
 val shown=records.filter{record->
  (filter=="全部"||record.phase==filter)&&(
   query.isBlank()||listOf(record.title,record.symptomText,record.checkText,record.medicineText,record.noteText).any{it.contains(query,ignoreCase=true)}
  )
 }
 val listState=rememberLazyListState();val scope=rememberCoroutineScope()
 var menuFor by remember{mutableStateOf<String?>(null)}
 var deleting by remember{mutableStateOf<CourseRecord?>(null)}
 Box(modifier.fillMaxSize()){
  Column(Modifier.fillMaxSize().padding(horizontal=20.dp)){
   Spacer(Modifier.height(12.dp))
   Text("病程记录",fontSize=28.sp,fontWeight=FontWeight.Bold)
   Spacer(Modifier.height(12.dp))
   Surface(shape=RoundedCornerShape(18.dp),color=Color.White){
    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
     Icon(Icons.Outlined.Timeline,null,tint=Accent)
     Spacer(Modifier.width(8.dp))
     Text("当前阶段：${if(filter=="全部")"全部" else filter}",Modifier.weight(1f),fontWeight=FontWeight.Medium)
     Text("共 ${shown.size} 条记录",color=Muted,fontSize=12.sp)
    }
   }
   OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(top=6.dp).height(48.dp),singleLine=true,leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(search.isNotEmpty())IconButton({search=""}){Icon(Icons.Outlined.Clear,"清空搜索")}})
   Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical=10.dp)){
    (listOf("全部")+coursePhases).forEach{phase->FilterChip(filter==phase,{filter=phase},label={Text(phase)},modifier=Modifier.padding(end=8.dp))}
   }
   if(shown.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(if(query.isBlank())"还没有病程记录" else "没有找到相关病程记录",color=Muted)}
   else Box(Modifier.fillMaxSize()){
    LazyColumn(Modifier.fillMaxSize().offset(x=(-6).dp).padding(end=8.dp),state=listState,contentPadding=PaddingValues(bottom=152.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    items(shown,key={it.id}){record->
     CourseTimelineCard(record,{open(record)},menuFor==record.id,{menuFor=record.id},{menuFor=null;open(record)},{menuFor=null;deleting=record},{menuFor=null},view)
    }
    if(listState.canScrollBackward)item(key="back_to_top"){Box(Modifier.fillMaxWidth().padding(top=8.dp,bottom=12.dp),contentAlignment=Alignment.Center){OutlinedButton({scope.launch{listState.animateScrollToItem(0)}}){Icon(Icons.Outlined.VerticalAlignTop,null);Spacer(Modifier.width(6.dp));Text("回到顶部")}}}
    }
    LazyScrollProgressRail(listState,Modifier.align(Alignment.CenterEnd).offset(x=16.dp).padding(top=8.dp,bottom=8.dp).width(10.dp).fillMaxHeight())
   }
  }
  FloatingActionButton(add,Modifier.align(Alignment.BottomEnd).padding(20.dp),containerColor=Accent,contentColor=Color.White){Icon(Icons.Outlined.Add,"新增病程记录")}
 }
 deleting?.let{target->DeleteConfirmation({deleting=null}){remove(target);deleting=null}}
}

@Composable private fun CourseTimelineCard(record:CourseRecord,onOpen:()->Unit,menuOpen:Boolean,onMenu:()->Unit,onEdit:()->Unit,onDelete:()->Unit,onDismiss:()->Unit,view:(List<String>,Int)->Unit){
 val phaseColors=coursePhaseColors(record.phase)
 Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),verticalAlignment=Alignment.Top){
  Column(Modifier.width(22.dp).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally){
   Spacer(Modifier.height(25.dp));Box(Modifier.size(10.dp).background(phaseColors.second,CircleShape));Box(Modifier.width(2.dp).weight(1f).background(Color(0xFFE7DDD6)))
  }
  Card(onClick=onOpen,modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
   Column(Modifier.padding(start=16.dp,top=14.dp,end=12.dp,bottom=14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){
     Text("${courseDate(record.date)} · ${record.title.ifBlank{"待填写"}}",Modifier.weight(1f).padding(top=4.dp),fontWeight=FontWeight.Bold,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
     Spacer(Modifier.width(6.dp))
     Surface(shape=RoundedCornerShape(14.dp),color=phaseColors.first){Text(record.phase,Modifier.padding(horizontal=7.dp,vertical=3.dp),color=phaseColors.second,fontSize=10.sp,fontWeight=FontWeight.Medium)}
     Box(Modifier.size(28.dp).clickable(onClick=onMenu),contentAlignment=Alignment.Center){Icon(Icons.Outlined.MoreVert,"更多",Modifier.size(18.dp));DropdownMenu(menuOpen,onDismiss){DropdownMenuItem({Text("编辑")},onEdit,leadingIcon={Icon(Icons.Outlined.Edit,null)});DropdownMenuItem({Text("删除",color=Bad)},onDelete,leadingIcon={Icon(Icons.Outlined.Delete,null,tint=Bad)})}}
    }
    if(record.symptomText.isNotBlank())CourseTextRow(Icons.Outlined.MonitorHeart,record.symptomText,true)
    if(record.checkText.isNotBlank()||record.checkImages.isNotEmpty())CourseSection(Icons.Outlined.FactCheck,"检查",record.checkText,record.checkImages,view)
    if(record.medicineText.isNotBlank()||record.medicineImages.isNotEmpty())CourseSection(Icons.Outlined.Medication,"药品 / 取药",record.medicineText,record.medicineImages,view)
    if(record.noteText.isNotBlank())CourseTextRow(Icons.Outlined.Notes,record.noteText,true)
   }
  }
 }
}

private fun coursePhaseColors(phase:String)=when(phase){
 "化疗日"->Color(0xFFFFEEE5) to Color(0xFFF2763D)
 "恢复期"->Color(0xFFE5F8EE) to Color(0xFF20A66A)
 "观察"->Color(0xFFE4F2FF) to Color(0xFF3285C8)
 else->Color(0xFFF0F0F0) to Color(0xFF707070)
}

@Composable private fun CourseTextRow(icon:androidx.compose.ui.graphics.vector.ImageVector,text:String,muted:Boolean=false){Row(verticalAlignment=Alignment.Top){Icon(icon,null,Modifier.size(20.dp),tint=Accent);Spacer(Modifier.width(9.dp));Text(text,Modifier.weight(1f),color=if(muted)Muted else Ink,fontSize=if(muted)13.sp else 15.sp,lineHeight=if(muted)17.sp else 19.sp)}}

@Composable private fun CourseSection(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,text:String,images:List<String>,view:(List<String>,Int)->Unit){
 Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
  if(text.isNotBlank())CourseTextRow(icon,text,true)
  else if(images.isNotEmpty())CourseTextRow(icon,label)
  if(images.isNotEmpty())CourseThumbnails(images,{view(images,it)})
 }
}

@Composable private fun CourseThumbnails(images:List<String>,open:(Int)->Unit,remove:((Int)->Unit)?=null){
 Row(Modifier.padding(start=29.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){images.forEachIndexed{i,uri->Box{CourseThumbnail(uri,Modifier.size(52.dp).clickable{open(i)});if(remove!=null)Surface(onClick={remove(i)},modifier=Modifier.align(Alignment.TopEnd).offset(x=5.dp,y=(-5).dp).size(22.dp),shape=CircleShape,color=Color.White,shadowElevation=2.dp){Icon(Icons.Outlined.Close,"移除图片",Modifier.padding(4.dp),tint=Muted)}}}}
}

@Composable private fun CourseThumbnail(uri:String,modifier:Modifier){
 val context=LocalContext.current
 val bitmap by produceState<android.graphics.Bitmap?>(null,uri){value=withContext(Dispatchers.IO){runCatching{decodeReportBitmap(context,Uri.parse(uri),maxPixels=250_000,maxDimension=600)}.getOrNull()}}
 Box(modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFFF0EDEA)),contentAlignment=Alignment.Center){if(bitmap!=null)Image(bitmap!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Icon(Icons.Outlined.Image,null,tint=Muted)}
}

@Composable fun CourseRecordEditor(initial:CourseRecord,store:HealthStore,onClose:()->Unit,onSave:(CourseRecord)->Unit,onDelete:()->Unit,onView:(List<String>,Int)->Unit){
 val context=LocalContext.current
 var record by rememberSaveable(initial,stateSaver=diskStateSaver<CourseRecord>(context,"course-record-editor")){mutableStateOf(initial)}
 var date by rememberSaveable{mutableStateOf(courseEditorDate(initial.date))}
 var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var deleting by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 fun addImages(target:String,uris:List<Uri>){if(uris.isEmpty())return;busy=true;scope.launch{try{val owned=withContext(Dispatchers.IO){uris.map{store.ownImage(it)}};record=if(target=="check")record.copy(checkImages=record.checkImages+owned)else record.copy(medicineImages=record.medicineImages+owned)}catch(e:Exception){error="图片保存失败：${e.message}"}finally{busy=false}}}
 val checkPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){addImages("check",it)}
 val medicinePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){addImages("medicine",it)}
 val exists=remember(initial.id){store.courseRecords().any{it.id==initial.id}}
 val remembered=remember(initial.id){store.courseRecords().sortedByDescending{it.updatedAt}}
 val rememberedTitles=remember(remembered){remembered.map{it.title}.filter{it.isNotBlank()}.distinctBy(::courseTitleMemoryKey)}
 val rememberedChecks=remember(remembered){remembered.map{it.checkText}.filter{it.isNotBlank()}.distinct()}
 val rememberedMedicines=remember(remembered){remembered.map{it.medicineText}.filter{it.isNotBlank()}.distinct()}
 val selectedDate=courseDateMillis(date,initial.date);val valid=record.title.isNotBlank()&&selectedDate!=null&&!busy
 FullPage(if(exists)"编辑病程记录" else "新增病程记录",onClose,bottom={Row(verticalAlignment=Alignment.CenterVertically){Button({onSave(record.copy(date=selectedDate!!))},Modifier.weight(1f),enabled=valid){Text("保存记录")};if(exists)TextButton({deleting=true}){Text("删除",color=Bad)}}}){m->
  Column(m.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   CourseDateField(date){date=it}
   Text("阶段",fontWeight=FontWeight.Medium)
   Row(Modifier.horizontalScroll(rememberScrollState())){coursePhases.forEach{phase->FilterChip(record.phase==phase,{record=record.copy(phase=phase)},label={Text(phase)},modifier=Modifier.padding(end=8.dp))}}
   CourseRememberedField(record.title,{record=record.copy(title=it)},"标题",rememberedTitles)
   CourseEditorHeading(Icons.Outlined.MonitorHeart,"症状")
   OutlinedTextField(record.symptomText,{record=record.copy(symptomText=it)},label={Text("症状内容")},modifier=Modifier.fillMaxWidth(),minLines=2)
   CourseEditorHeading(Icons.Outlined.FactCheck,"检查")
   CourseRememberedField(record.checkText,{record=record.copy(checkText=it)},"检查内容",rememberedChecks,2)
   OutlinedButton({checkPicker.launch(arrayOf("image/*"))},enabled=!busy){Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(6.dp));Text("添加检查图片")}
   if(record.checkImages.isNotEmpty())CourseThumbnails(record.checkImages,{onView(record.checkImages,it)}){i->record=record.copy(checkImages=record.checkImages.filterIndexed{j,_->j!=i})}
   CourseEditorHeading(Icons.Outlined.Medication,"药品 / 取药")
   CourseRememberedField(record.medicineText,{record=record.copy(medicineText=it)},"药品 / 取药内容",rememberedMedicines,2)
   OutlinedButton({medicinePicker.launch(arrayOf("image/*"))},enabled=!busy){Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(6.dp));Text("添加药品图片")}
   if(record.medicineImages.isNotEmpty())CourseThumbnails(record.medicineImages,{onView(record.medicineImages,it)}){i->record=record.copy(medicineImages=record.medicineImages.filterIndexed{j,_->j!=i})}
   CourseEditorHeading(Icons.Outlined.Notes,"备注")
   OutlinedTextField(record.noteText,{record=record.copy(noteText=it)},label={Text("备注（选填）")},modifier=Modifier.fillMaxWidth(),minLines=3)
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(error.isNotBlank())Text(error,color=Bad)
  }
 }
 if(deleting)DeleteConfirmation({deleting=false}){onDelete();deleting=false}
}

@Composable private fun CourseRememberedField(value:String,onChange:(String)->Unit,label:String,options:List<String>,minLines:Int=1){
 var focused by remember{mutableStateOf(false)}
 val query=value.trim()
 val suggestions=options.filter{it!=query&&(query.isBlank()||it.contains(query,ignoreCase=true))}.take(5)
 Column(Modifier.fillMaxWidth()){
  OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth().onFocusChanged{focused=it.isFocused},singleLine=minLines==1,minLines=minLines)
  if(focused&&suggestions.isNotEmpty())Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(bottomStart=12.dp,bottomEnd=12.dp),color=Color.White,shadowElevation=3.dp){
   Column{suggestions.forEachIndexed{index,option->
    Text(option,Modifier.fillMaxWidth().clickable{onChange(option)}.padding(horizontal=14.dp,vertical=11.dp),maxLines=2,overflow=TextOverflow.Ellipsis)
    if(index<suggestions.lastIndex)HorizontalDivider(color=Color(0xFFEDE8E3))
   }}
  }
 }
}

@Composable private fun CourseDateField(value:String,onChange:(String)->Unit){
 val context=LocalContext.current
 Box(Modifier.fillMaxWidth()){
  OutlinedTextField(value,{},Modifier.fillMaxWidth(),label={Text("日期")},trailingIcon={Icon(Icons.Outlined.CalendarMonth,null)},readOnly=true,singleLine=true)
  Box(Modifier.matchParentSize().clickable{
   val initial=runCatching{LocalDate.parse(value)}.getOrElse{LocalDate.now()}
   android.app.DatePickerDialog(context,{_,year,month,day->
    onChange(LocalDate.of(year,month+1,day).toString())
   },initial.year,initial.monthValue-1,initial.dayOfMonth).show()
  })
 }
}

@Composable private fun CourseEditorHeading(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Accent);Spacer(Modifier.width(8.dp));Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp)}}

private fun courseDate(epoch:Long):String{val d=Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault());return "${d.monthValue}/${d.dayOfMonth}"}
private fun courseEditorDate(epoch:Long)=Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate().toString()
private fun courseTitleMemoryKey(value:String)=Regex("第[零一二三四五六七八九十百两\\d]+天").replace(value.trim(),"第X天")
private fun courseDateMillis(value:String,original:Long):Long?=runCatching{
 val date=LocalDate.parse(value);val originalTime=Instant.ofEpochMilli(original).atZone(ZoneId.systemDefault()).toLocalTime()
 date.atTime(originalTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

@Composable private fun LazyScrollProgressRail(state:androidx.compose.foundation.lazy.LazyListState,modifier:Modifier=Modifier){
 var dragging by remember{mutableStateOf(false)};var visible by remember{mutableStateOf(false)};var height by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope()
 val total=state.layoutInfo.totalItemsCount
 LaunchedEffect(state.isScrollInProgress,dragging,state.firstVisibleItemIndex,state.firstVisibleItemScrollOffset){if(state.isScrollInProgress||dragging)visible=true else{kotlinx.coroutines.delay(850);visible=false}}
 AnimatedVisibility(visible=visible&&total>1,modifier=modifier,enter=fadeIn(),exit=fadeOut()){
  androidx.compose.foundation.Canvas(Modifier.fillMaxSize().onSizeChanged{height=it.height}.pointerInput(total){
   fun seek(y:Float){if(height>0&&total>0)scope.launch{state.scrollToItem(((y/height)*(total-1)).roundToInt().coerceIn(0,total-1))}}
   detectDragGestures(onDragStart={dragging=true;seek(it.y)},onDragEnd={dragging=false},onDragCancel={dragging=false}){change,_->seek(change.position.y)}
  }){val x=size.width/2f;drawLine(Color(0x337B7B82),androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Offset(x,size.height),4.dp.toPx());val progress=state.firstVisibleItemIndex.toFloat()/(total-1).coerceAtLeast(1);drawCircle(Accent,7.dp.toPx(),androidx.compose.ui.geometry.Offset(x,(progress*size.height).coerceIn(7.dp.toPx(),size.height-7.dp.toPx())))}
 }
}
