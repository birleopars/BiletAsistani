package com.biletasistani.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import kotlin.math.abs

/** User-controlled, experimental Passo UI assistant. Never touches verification, identity or payment. */
class CartWatchService : AccessibilityService() {
 private val handler=Handler(Looper.getMainLooper())
 private val tr=Locale.forLanguageTag("tr-TR")
 private var lastTap=0L
 private var lastAlert=0L
 private var busyScreenshot=false
 private var lastSeatAttempt=0L
 private var pendingSeat:Pair<Int,Int>?=null
 private var pendingSeatTime=0L
 private val rejectedSeats=mutableListOf<Pair<Int,Int>>()
 private var seatScreenSeenAt=0L
 private var categoryAttempts=mutableMapOf<Int,Long>()
 private var blockAttempts=mutableMapOf<String,Long>()
 private var lastStatus="Bekleniyor"
 private var lastInspectAt=0L
 private var blockPendingUntil=0L
 private var selectedBlockKey=""
 private var searchSubmitted=false
 private var searchSubmittedAt=0L
 override fun onInterrupt() {}
 override fun onAccessibilityEvent(event:AccessibilityEvent?) {
  val pkg=event?.packageName?.toString()?.lowercase(Locale.ROOT)?:return
  val configured=getSharedPreferences("ticket_config",MODE_PRIVATE).getString("passoPackage","")?.lowercase(Locale.ROOT).orEmpty()
  if(pkg!=configured && !pkg.contains("passo"))return
  handler.removeCallbacksAndMessages(null)
  handler.postDelayed({ inspect() }, 450)
 }
 private fun label(n:AccessibilityNodeInfo)=listOfNotNull(n.text?.toString(),n.contentDescription?.toString()).joinToString(" ").trim()
 private fun gather(n:AccessibilityNodeInfo,out:MutableList<AccessibilityNodeInfo>,depth:Int=0) {
  if(depth>25||out.size>=900)return
  out.add(n)
  for(i in 0 until n.childCount)n.getChild(i)?.let{gather(it,out,depth+1)}
 }
 private fun click(n:AccessibilityNodeInfo):Boolean {
  var current:AccessibilityNodeInfo?=n
  for(i in 0..5){
   if(current==null)break
   if(current.isEnabled&&current.isClickable&&current.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true
   current=current.parent
  }
  val rect=Rect();n.getBoundsInScreen(rect)
  if(!rect.isEmpty&&n.isEnabled)return tap(rect.centerX().toFloat(),rect.centerY().toFloat())
  return false
 }
 private fun tap(x:Float,y:Float):Boolean {
  if(System.currentTimeMillis()-lastTap<900)return false
  val p=Path().apply{moveTo(x,y)}
  val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,95)).build()
  val ok=dispatchGesture(gesture,null,null)
  if(ok)lastTap=System.currentTimeMillis()
  return ok
 }
 private fun status(s:String){lastStatus=s;getSharedPreferences("passo_diagnostic",MODE_PRIVATE).edit().putString("stage",s).apply()}
 private fun inspect(){
  if(System.currentTimeMillis()-lastInspectAt<650)return
  lastInspectAt=System.currentTimeMillis()
  val root=rootInActiveWindow?:return
  val active=root.packageName?.toString()?.lowercase(Locale.ROOT).orEmpty()
  val configured=getSharedPreferences("ticket_config",MODE_PRIVATE).getString("passoPackage","")?.lowercase(Locale.ROOT).orEmpty()
  if(active!=configured && !active.contains("passo"))return
  val nodes=mutableListOf<AccessibilityNodeInfo>();gather(root,nodes)
  val all=nodes.joinToString(" "){label(it)}.lowercase(tr)
  val d=getSharedPreferences("passo_diagnostic",MODE_PRIVATE)
  d.edit().putLong("lastSeen",System.currentTimeMillis()).putInt("nodeCount",nodes.size)
   .putInt("clickableCount",nodes.count{it.isEnabled&&(it.isClickable||it.parent?.isClickable==true)})
   .putInt("seatCandidates",nodes.count{label(it).contains("koltuk",true)&&it.isClickable})
   .putBoolean("seatScreen",all.contains("koltuk listesi"))
   .putBoolean("category11Visible",Regex("11\\s*\\.?\\s*kategori").containsMatchIn(all))
   .putBoolean("category10Visible",Regex("10\\s*\\.?\\s*kategori").containsMatchIn(all)).apply()
  val cfg=getSharedPreferences("ticket_config",MODE_PRIVATE)
  if(!cfg.getBoolean("automationEnabled",false)){status("Durduruldu: otomatik seçim kapalı");return}
  val blockers=listOf("cloudflare","güvenlik doğrulaması","doğrulanıyor","captcha","robot olmadığınızı","kart numarası","cvv","ödeme yap","kişi tanımla","t.c. kimlik")
  if(blockers.any{all.contains(it)}){status("Güvenlik/ödeme adımında durdu");return}
  if(listOf("özet - koltuk seçimi","biletlerim (","sepete eklendi","ödeme için kalan süre").any{all.contains(it)}){
   status("Sepet veya bilet özeti algılandı")
   if(System.currentTimeMillis()-lastAlert>60000){lastAlert=System.currentTimeMillis();AlarmHelper.alert(this)}
   return
  }
  if(System.currentTimeMillis()-lastTap<1300)return
  // Find the explicitly requested event before attempting category selection.
  val eventQuery=cfg.getString("eventQuery","")?.trim().orEmpty()
  if(eventQuery.length>=4 && !all.contains("koltuk listesi") && !all.contains("blok seç") && !all.contains("kategori")){
   val tokens=eventQuery.lowercase(tr).split(Regex("\\s+")).filter{it.length>=3}
   val result=nodes.firstOrNull{ n ->
    val t=label(n).lowercase(tr)
    t.isNotBlank() && tokens.isNotEmpty() && tokens.all{t.contains(it)} && n.isEnabled && (n.isClickable || n.parent?.isClickable==true)
   }
   if(result!=null){status("İstenen etkinlik açılıyor");click(result);return}
   val searchInput=nodes.firstOrNull{it.isEditable && it.isEnabled}
   if(searchInput!=null){
    val current=searchInput.text?.toString().orEmpty()
    if(current!=eventQuery){
     val args=android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,eventQuery)}
     if(searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)){
      searchSubmitted=false;status("Etkinlik adı arama alanına yazıldı");return
     }
    }else if(!searchSubmitted || System.currentTimeMillis()-searchSubmittedAt>15000){
     val search=nodes.firstOrNull{val t=label(it).lowercase(tr);it.isEnabled && (t=="ara" || t=="search" || t.contains("arama yap"))}
     if(search!=null && click(search)){
      searchSubmitted=true;searchSubmittedAt=System.currentTimeMillis();status("Etkinlik aranıyor");return
     }
    }
   }else{
    val search=nodes.firstOrNull{val t=label(it).lowercase(tr);it.isEnabled && (t=="ara" || t=="arama" || t=="search" || t.contains("etkinlik ara"))}
    if(search!=null){status("Passo arama ekranı açılıyor");click(search);return}
   }
   status("Etkinlik arama ekranı bekleniyor: $eventQuery")
   return
  }
  val qty=cfg.getInt("quantity",1).coerceIn(1,4)
  val selected=Regex("koltuk listesi\\s*\\(\\s*(\\d+)\\s*/\\s*(\\d+)\\s*\\)",RegexOption.IGNORE_CASE).find(all)
  if(selected!=null&&selected.groupValues[1].toIntOrNull()==qty){
   pendingSeat=null
   status("Koltuk seçildi, devam ediliyor")
   nodes.firstOrNull{label(it).equals("Devam Et",true)}?.let{click(it)}
   return
  }
  if(all.contains("koltuk listesi")&&all.contains("saha")){
   selectedBlockKey=""
   if(seatScreenSeenAt==0L)seatScreenSeenAt=System.currentTimeMillis()
   if(pendingSeat!=null && System.currentTimeMillis()-pendingSeatTime<7000){status("Koltuk dokunuşunun sonucu bekleniyor");return}
   pendingSeat?.let{rejectedSeats.add(it)}
   pendingSeat=null
   status("Boş koltuk görüntüden aranıyor")
   if(qty==1&&System.currentTimeMillis()-lastSeatAttempt>3500){
    lastSeatAttempt=System.currentTimeMillis();scanSeatImage(nodes)
   }else if(qty>1)status("Birden fazla bitişik koltuk için görsel seçim desteklenmiyor")
   return
  }
  seatScreenSeenAt=0L
  rejectedSeats.clear()
  // Passo category -> block -> seat map progression, only on recognizable pages.
  if(all.contains("blok seç")){
   // Wait for the asynchronous block list. Never click an empty area or scroll blindly.
   val blocks=nodes.filter { n ->
    Regex("^\\d{2,4}$").matches(label(n)) && n.isEnabled && n.isVisibleToUser
   }.filter { n ->
    val r=Rect();n.getBoundsInScreen(r)
    r.width()>0 && r.height()>0 && r.centerY()>0 && r.centerY()<resources.displayMetrics.heightPixels*0.80
   }
   val choose=nodes.firstOrNull{label(it).equals("Koltuk Seç",true)&&it.isEnabled&&(it.isClickable||it.parent?.isClickable==true)}
   val now=System.currentTimeMillis()
   if(choose!=null && selectedBlockKey.isNotEmpty() && now>=blockPendingUntil){
    status("Blok $selectedBlockKey seçildi; koltuk ekranına geçiliyor")
    if(click(choose))blockPendingUntil=now+4500
    return
   }
   if(now<blockPendingUntil){status("Blok seçimi doğrulanıyor");return}
   val block=blocks.firstOrNull{now-(blockAttempts[label(it)]?:0L)>18000}
   if(block!=null){
    val key=label(block)
    // Select only a visible block; do not retry the same target on every event.
    if(click(block)){
     selectedBlockKey=key
     blockAttempts[key]=now
     blockPendingUntil=now+1700
     status("Blok $key işaretlendi; buton bekleniyor")
    }
   }else status("Blok listesi veya seçim butonu bekleniyor")
   return
  }
  val categories=cfg.getString("categories","11,10")!!.split(",").mapNotNull{it.toIntOrNull()}.filter{it in 1..20}
  val unavailable=listOf("şu anda uygun bilet bulunmamaktadır","tükendi","sold out")
  val maxPrice=cfg.getInt("maxPrice",3500)
  for(cat in categories){
   val regex=Regex("^\\s*$cat\\s*\\.?\\s*kategori\\s*$",RegexOption.IGNORE_CASE)
   val n=nodes.firstOrNull{regex.matches(label(it))}?:continue
   var row:AccessibilityNodeInfo?=n
   var rowText=""
   for(i in 0..4){
    if(row==null)break
    val children=mutableListOf<AccessibilityNodeInfo>();gather(row,children)
    val txt=children.joinToString(" "){label(it)}.lowercase(tr)
    if(txt.contains("tl")||txt.contains("₺")||unavailable.any{txt.contains(it)}){rowText=txt;break}
    row=row.parent
   }
   if(unavailable.any{rowText.contains(it)})continue
   val price=Regex("([\\d.]+),\\d{2}\\s*(?:tl|₺)",RegexOption.IGNORE_CASE).find(rowText)?.groupValues?.get(1)?.replace(".","")?.toIntOrNull()
   if(price!=null&&price*qty>maxPrice)continue
   if(System.currentTimeMillis()-(categoryAttempts[cat]?:0L)<12000)continue
   categoryAttempts[cat]=System.currentTimeMillis()
   status("$cat. kategori deneniyor")
   if(click(n))return
  }
  // Only press Bilet Al if a category is visibly selected and the price is within budget.
  if(all.contains("toplam")&&all.contains("bilet al")&&all.contains("tam(transfer")){
   val price=Regex("toplam\\s*([\\d.]+),\\d{2}",RegexOption.IGNORE_CASE).find(all)?.groupValues?.get(1)?.replace(".","")?.toIntOrNull()
   if(price!=null&&price in 1..maxPrice){
    nodes.firstOrNull{label(it).equals("Bilet Al",true)}?.let{status("Bilet Al adımına geçiliyor");click(it)}
   }
  }
 }
 private fun scanSeatImage(nodes:List<AccessibilityNodeInfo>){
  if(Build.VERSION.SDK_INT<30||busyScreenshot){status("Ekran görüntüsü API 30+ gerekiyor");return}
  val seatList=nodes.firstOrNull{label(it).contains("Koltuk Listesi",true)}
  val saha=nodes.firstOrNull{label(it).equals("Saha",true)}
  if(seatList==null||saha==null){status("Koltuk haritası sınırları belirlenemedi");return}
  val bottom=Rect();seatList.getBoundsInScreen(bottom)
  val field=Rect();saha.getBoundsInScreen(field)
  busyScreenshot=true
  try{
   takeScreenshot(Display.DEFAULT_DISPLAY,mainExecutor,object:TakeScreenshotCallback{
    override fun onFailure(errorCode:Int){busyScreenshot=false;status("Görüntü alınamadı: $errorCode")}
    override fun onSuccess(result:ScreenshotResult){
     try{
      val buffer=result.hardwareBuffer
      val bitmap=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)
      if(bitmap!=null){
       val b=bitmap.copy(Bitmap.Config.ARGB_8888,false)
       bitmap.recycle()
       if(b!=null){findSeat(b,field,bottom);b.recycle()}
      }
      buffer.close()
     }catch(e:Exception){status("Koltuk analizi hatası: ${e.javaClass.simpleName}")}
     finally{busyScreenshot=false}
    }
   })
  }catch(e:Exception){busyScreenshot=false;status("Görüntü izni veya erişim hatası")}
 }
 private fun findSeat(bitmap:Bitmap,field:Rect,bottom:Rect){
  // Conservative image-only seat detection: a bright seat center must sit on a
  // regular dark-seat grid. Never tap blank page space or unknown UI elements.
  val w=bitmap.width;val h=bitmap.height
  val screenW=resources.displayMetrics.widthPixels.coerceAtLeast(1)
  val screenH=resources.displayMetrics.heightPixels.coerceAtLeast(1)
  val sx=w.toFloat()/screenW;val sy=h.toFloat()/screenH
  val left=(w*0.11).toInt().coerceAtLeast(30)
  val right=(w*0.88).toInt().coerceAtMost(w-30)
  val top=(h*0.23).toInt().coerceAtLeast(30)
  val end=(field.top*sy-20).toInt().coerceAtMost((bottom.top*sy-90).toInt()).coerceAtMost(h-30)
  if(end<=top+70){status("Koltuk alanı belirlenemedi");return}
  fun rgb(x:Int,y:Int):Int=if(x in 0 until w&&y in 0 until h)bitmap.getPixel(x,y) else Color.GRAY
  fun bright(x:Int,y:Int):Boolean {val c=rgb(x,y);return Color.red(c)>238&&Color.green(c)>238&&Color.blue(c)>238}
  fun dark(x:Int,y:Int):Boolean {val c=rgb(x,y);return Color.red(c)<65&&Color.green(c)<65&&Color.blue(c)<65}
  fun gray(x:Int,y:Int):Boolean {val c=rgb(x,y);val v=Color.red(c);return v in 125..235&&kotlin.math.abs(v-Color.green(c))<20&&kotlin.math.abs(v-Color.blue(c))<20}
  data class Candidate(val x:Int,val y:Int,val score:Int)
  val candidates=mutableListOf<Candidate>()
  // Pitch is the distance between centers of adjacent seats, not seat width.
  for(pitch in 13..31 step 2){
   val radius=(pitch*0.39).toInt().coerceAtLeast(4)
   for(y in top+pitch until end-pitch step 3){
    for(x in left+pitch until right-pitch step 3){
     if(!bright(x,y))continue
     if(!bright(x-radius/2,y)||!bright(x+radius/2,y)||!bright(x,y-radius/2)||!bright(x,y+radius/2))continue
     // The outline distinguishes a vacant white square from white background.
     val edge=listOf(gray(x-radius,y),gray(x+radius,y),gray(x,y-radius),gray(x,y+radius))
     if(edge.count{it}<2)continue
     val neighbors=listOf(dark(x-pitch,y),dark(x+pitch,y),dark(x,y-pitch),dark(x,y+pitch))
     val count=neighbors.count{it}
     if(count<2 || !(neighbors[0]||neighbors[1]) || !(neighbors[2]||neighbors[3]))continue
     // Reject gaps: the bright square must be bounded on at least 2 edges.
     if(bright(x-radius-2,y)&&bright(x+radius+2,y))continue
     val point=(x/sx).toInt() to (y/sy).toInt()
     if(rejectedSeats.any{ kotlin.math.abs(it.first-point.first)<pitch/sx && kotlin.math.abs(it.second-point.second)<pitch/sy })continue
     val score=count*10+edge.count{it}*3
     candidates.add(Candidate(x,y,score))
    }
   }
  }
  val best=candidates.maxByOrNull{it.score}
  getSharedPreferences("passo_diagnostic",MODE_PRIVATE).edit().putInt("visualSeatCandidates",candidates.size).apply()
  if(best==null){status("Doğrulanabilir boş koltuk bulunamadı; rastgele dokunulmadı");return}
  val x=(best.x/sx).toInt();val y=(best.y/sy).toInt()
  pendingSeat=x to y;pendingSeatTime=System.currentTimeMillis()
  getSharedPreferences("passo_diagnostic",MODE_PRIVATE).edit().putString("seatCoordinate","$x,$y").apply()
  status("Beyaz koltuk adayı seçiliyor: $x,$y")
  if(!tap(x.toFloat(),y.toFloat())){pendingSeat=null;status("Koltuk dokunuşu gönderilemedi")}
 }
}
