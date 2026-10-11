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
 private var categoryAttempts=mutableMapOf<Int,Long>()
 private var blockAttempts=mutableMapOf<String,Long>()
 private var lastStatus="Bekleniyor"
 private var searchSubmitted=false
 private var searchSubmittedAt=0L
 override fun onInterrupt() {}
 override fun onAccessibilityEvent(event:AccessibilityEvent?) {
  val pkg=event?.packageName?.toString()?.lowercase(Locale.ROOT)?:return
  val configured=getSharedPreferences("ticket_config",MODE_PRIVATE).getString("passoPackage","")?.lowercase(Locale.ROOT).orEmpty()
  if(pkg!=configured && !pkg.contains("passo"))return
  handler.post { inspect() }
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
  val root=rootInActiveWindow?:return
  val active=root.packageName?.toString()?.lowercase(Locale.ROOT).orEmpty()
  val configured=getSharedPreferences("ticket_config",MODE_PRIVATE).getString("passoPackage","")?.lowercase(Locale.ROOT).orEmpty()
  if(active!=configured && !active.contains("passo"))return
  val nodes=mutableListOf<AccessibilityNodeInfo>();gather(root,nodes)
  val all=nodes.joinToString(" "){label(it)}.lowercase(tr)
  val d=getSharedPreferences("passo_diagnostic",MODE_PRIVATE)
  d.edit().putLong("lastSeen",System.currentTimeMillis()).putInt("nodeCount",nodes.size)
   .putInt("clickableCount",nodes.count{it.isEnabled&&it.isClickable})
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
   if(pendingSeat!=null && System.currentTimeMillis()-pendingSeatTime<7000){status("Koltuk dokunuşunun sonucu bekleniyor");return}
   pendingSeat=null
   status("Boş koltuk görüntüden aranıyor")
   if(qty==1&&System.currentTimeMillis()-lastSeatAttempt>3500){
    lastSeatAttempt=System.currentTimeMillis();scanSeatImage(nodes)
   }else if(qty>1)status("Birden fazla bitişik koltuk için görsel seçim desteklenmiyor")
   return
  }
  // Passo category -> block -> seat map progression, only on recognizable pages.
  if(all.contains("blok seç")&&all.contains("koltuk seç")){
   val block=nodes.firstOrNull{Regex("^\\d{2,4}$").matches(label(it))&&it.isEnabled}
   if(block!=null){
    val key=label(block)
    if(System.currentTimeMillis()-(blockAttempts[key]?:0L)>12000){
     blockAttempts[key]=System.currentTimeMillis();status("Blok $key seçiliyor");click(block);return
    }
   }
   val button=nodes.firstOrNull{label(it).equals("Koltuk Seç",true)&&it.isEnabled}
   if(button!=null){status("Koltuk haritasına geçiliyor");click(button)}
   return
  }
  if(all.contains("blok seç")&&all.contains("oturma planı")){
   val block=nodes.firstOrNull{Regex("^\\d{2,4}$").matches(label(it))&&it.isEnabled}
   if(block!=null){status("Blok ${label(block)} seçiliyor");click(block)}
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
  // Detect compact, white, bordered square seats. Ignore the white page background.
  val w=bitmap.width;val h=bitmap.height
  val left=(w*0.10).toInt().coerceAtLeast(14)
  val right=(w*0.90).toInt().coerceAtMost(w-14)
  val top=(h*0.20).toInt().coerceAtLeast(14)
  val end=(field.top-25).coerceAtMost(bottom.top-80).coerceAtMost(h-14)
  if(end<=top+60){status("Koltuk alanı belirlenemedi");return}
  fun white(x:Int,y:Int):Boolean {
   if(x !in 0 until w || y !in 0 until h)return false
   val c=bitmap.getPixel(x,y)
   return Color.red(c)>225 && Color.green(c)>225 && Color.blue(c)>225
  }
  fun black(x:Int,y:Int):Boolean {
   if(x !in 0 until w || y !in 0 until h)return false
   val c=bitmap.getPixel(x,y)
   return Color.red(c)<85 && Color.green(c)<85 && Color.blue(c)<85
  }
  // Seat widths vary with device resolution and map zoom. Try a range of scales.
  val scales=listOf(6,8,10,12,15,18,22)
  var candidates=0
  for(size in scales){
   for(y in top+size until end-size step 3){
    for(x in left+size until right-size step 3){
     if(!white(x,y))continue
     val half=size/2
     // The candidate must be a coherent bright square, not a tiny gap in the black grid.
     val inner=listOf(-half/2 to -half/2,half/2 to -half/2,-half/2 to half/2,half/2 to half/2)
     if(inner.count{(dx,dy)->white(x+dx,y+dy)}<3)continue
     // Dark occupied seats to the left or right, and above or below.
     val offsets=listOf(size+3,size+5,size+8)
     val horizontal=offsets.any{black(x-it,y)||black(x+it,y)}
     val vertical=offsets.any{black(x,y-it)||black(x,y+it)}
     if(!horizontal||!vertical)continue
     // Reject broad blank patches: white cannot extend far beyond the seat.
     if(white(x-size-3,y)&&white(x+size+3,y))continue
     if(white(x,y-size-3)&&white(x,y+size+3))continue
     candidates++
     val d=getSharedPreferences("passo_diagnostic",MODE_PRIVATE)
     d.edit().putInt("visualSeatCandidates",candidates).putString("seatCoordinate","$x,$y").apply()
     pendingSeat=x to y;pendingSeatTime=System.currentTimeMillis()
     status("Boş koltuk adayı seçiliyor: $x,$y")
     if(!tap(x.toFloat(),y.toFloat())){pendingSeat=null;status("Koltuk dokunuşu gönderilemedi")}
     return
    }
   }
  }
  getSharedPreferences("passo_diagnostic",MODE_PRIVATE).edit().putInt("visualSeatCandidates",0).apply()
  status("Görselde doğrulanabilir boş koltuk bulunamadı; rastgele dokunulmadı")
 }
}
