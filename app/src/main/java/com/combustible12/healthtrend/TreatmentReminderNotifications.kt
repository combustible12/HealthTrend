package com.combustible12.healthtrend

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val REMINDER_CHANNEL_ID="treatment_reminders"
internal const val EXTRA_OPEN_TREATMENT_REMINDERS="open_treatment_reminders"
private const val EXTRA_REMINDER_ID="reminder_id"

internal fun treatmentReminderTriggerAt(
 reminder:TreatmentReminder,
 zoneId:ZoneId=ZoneId.systemDefault()
):Long?=if(reminder.triggerDate.isBlank())null else runCatching{
 LocalDate.parse(reminder.triggerDate)
  .atTime(LocalTime.parse(reminder.triggerTime.ifBlank{"09:00"}))
  .atZone(zoneId).toInstant().toEpochMilli()
}.getOrNull()

/**
 * Date reminders are owned by Android after they are saved. An inexact idle-safe alarm avoids
 * special "Alarms & reminders" access while still surviving ordinary app/process shutdown.
 */
object TreatmentReminderNotifications {
 fun ensureChannel(context:Context){
  if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
   val manager=context.getSystemService(NotificationManager::class.java)
   manager.createNotificationChannel(NotificationChannel(
    REMINDER_CHANNEL_ID,"治疗提醒",NotificationManager.IMPORTANCE_DEFAULT
   ).apply{description="HealthTrend 中设置的日期提醒"})
  }
 }

 fun sync(context:Context,reminder:TreatmentReminder){
  cancel(context,reminder.id)
  val triggerAt=treatmentReminderTriggerAt(reminder)?:return
  if(reminder.completedAt!=null)return
  val alarm=context.getSystemService(AlarmManager::class.java)
  val deliveryAt=maxOf(triggerAt,System.currentTimeMillis()+1_000L)
  alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,deliveryAt,alarmIntent(context,reminder.id))
 }

 fun cancel(context:Context,id:String){
  context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context,id))
  NotificationManagerCompat.from(context).cancel(notificationId(id))
 }

 fun rescheduleAll(context:Context){
  ensureChannel(context)
  HealthStore(context).treatmentReminders().forEach{sync(context,it)}
 }

 fun deliver(context:Context,id:String){
  val reminder=HealthStore(context).treatmentReminders().firstOrNull{it.id==id}?:return
  if(reminder.completedAt!=null||treatmentReminderTriggerAt(reminder)==null)return
  if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU&&
   ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
  ensureChannel(context)
  val open=Intent(context,MainActivity::class.java).apply{
   putExtra(EXTRA_OPEN_TREATMENT_REMINDERS,true)
   flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
  }
  val contentIntent=PendingIntent.getActivity(context,notificationId(id),open,
   PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=NotificationCompat.Builder(context,REMINDER_CHANNEL_ID)
   .setSmallIcon(android.R.drawable.ic_dialog_info)
   .setContentTitle(treatmentReminderDialogTitle(reminder))
   .setContentText(reminder.text)
   .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
   .setCategory(NotificationCompat.CATEGORY_REMINDER)
   .setPriority(NotificationCompat.PRIORITY_DEFAULT)
   .setAutoCancel(true)
   .setContentIntent(contentIntent)
   .build()
  NotificationManagerCompat.from(context).notify(notificationId(id),notification)
 }

 private fun alarmIntent(context:Context,id:String):PendingIntent{
  val intent=Intent(context,TreatmentReminderAlarmReceiver::class.java).apply{
   data=Uri.parse("healthtrend://treatment-reminder/${Uri.encode(id)}")
   putExtra(EXTRA_REMINDER_ID,id)
  }
  return PendingIntent.getBroadcast(context,0,intent,
   PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 }

 private fun notificationId(id:String)=id.hashCode()
}

class TreatmentReminderAlarmReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  intent.getStringExtra(EXTRA_REMINDER_ID)?.let{TreatmentReminderNotifications.deliver(context,it)}
 }
}

class TreatmentReminderRescheduleReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  TreatmentReminderNotifications.rescheduleAll(context)
 }
}
