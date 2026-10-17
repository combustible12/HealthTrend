package com.combustible12.healthtrend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Equal-height categories: magnitude, connecting lines and reference bands have no meaning here. */
@Composable fun TextResultTrend(points:List<Pair<LabReport,LabResult>>,onPointClick:(Pair<LabReport,LabResult>)->Unit) {
 val scroll=rememberScrollState();val measurer=rememberTextMeasurer();val density=LocalDensity.current
 LaunchedEffect(points){scroll.scrollTo(scroll.maxValue)}
 Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){
  points.forEach{point->
   val (report,result)=point
   val label=qualitativeDisplayText(label)
   val widthPx=maxOf(measurer.measure(label,TextStyle(fontSize=13.sp)).size.width,measurer.measure(trendShortDate(report.testedAtEpochMillis),TextStyle(fontSize=10.sp)).size.width)
   val width=with(density){widthPx.toDp()}.coerceAtLeast(48.dp) + 8.dp
   val color=qualitativeTrendColor(result)
   Column(Modifier.width(width).clickable{onPointClick(point)}.semantics{contentDescription="${result.rawName} ${dateText(report.testedAtEpochMillis)} ${report.hospitalKey} ${label}"},horizontalAlignment=Alignment.CenterHorizontally){
    Box(Modifier.height(72.dp).fillMaxWidth(),contentAlignment=Alignment.BottomCenter){
     Text(label,fontSize=12.sp,color=color,maxLines=1,modifier=Modifier.padding(bottom=8.dp).background(color.copy(alpha=0.10f),RoundedCornerShape(12.dp)).padding(horizontal=8.dp,vertical=5.dp))
    }
    Text(trendShortDate(report.testedAtEpochMillis),fontSize=10.sp,color=Muted,maxLines=1)
    Text(label,fontSize=13.sp,color=color,maxLines=1)
   }
  }
 }
}
