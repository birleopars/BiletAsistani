package com.biletasistani.app

import android.app.*
import android.content.*
import android.media.RingtoneManager
import android.os.*

object AlarmHelper {
 fun alert(context:Context, test:Boolean=false) {
  val manager=context.getSystemService(NotificationManager::class.java)
  val channel="cart_alert"
  manager.createNotificationChannel(NotificationChannel(channel,"Sepet uyarıları",NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true); setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),null) })
  val launch=context.packageManager.getLaunchIntentForPackage(context.packageName)
  val pending=PendingIntent.getActivity(context,0,launch,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val message=if(test) "Bu bir test alarmıdır." else "Sepet onayı algılandı! Passo'ya dön ve 10 dakikalık ödeme süresini kontrol et."
  val notification=Notification.Builder(context,channel).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(if(test) "Test alarmı" else "Bilet sepeti kontrolü!").setContentText(message).setContentIntent(pending).setAutoCancel(true).build()
  try { manager.notify(if(test) 2 else 1,notification) } catch (_:SecurityException) {}
  (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createWaveform(longArrayOf(0,400,200,400),-1))
 }
}
