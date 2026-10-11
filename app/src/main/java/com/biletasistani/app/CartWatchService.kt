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
 private var lastCategoryRefresh=0L
 private var lastPlusAttempt=0L
 private var footballEntered=false
 private val eventInspect=Runnable { inspect() }
 private val poll=object:Runnable { override fun run(){
  if(getSharedPreferences("ticket_config",MODE_PRIVATE).getBoolean("automationEnabled",false)) inspect()
  handler.postDelayed(this,3000L)
 } }
 override fun onServiceConnected(){super.onServiceConnected();handler.removeCallbacks(poll);handler.post(poll)}
 override fun onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy()}
 override fun onInterrupt() {}
 override fun onAccessibilityEvent(event:AccessibilityEvent?) {
  val pkg=event?.packageName?.toString()?.lowercase(Locale.ROOT)?:return
  val configured=getSharedPreferences("ticket_config",MODE_PRIVATE).getString("passoPackage","")?.lowercase(Locale.ROOT).orEmpty()
  if(pkg!=configured && !pkg.contains("passo"))return
  handler.removeCallbacks(eventInspect)
  handler.postDelayed(eventInspect, 250)
 }
 private fun label(n:AccessibilityNodeInfo)=listOfNotNull(n.text?.toString(),n.contentDescription?.toString()).joinToString(" ").trim()
 private fun gather(n:AccessibilityNodeInfo,out:MutableList<AccessibilityNodeInfo>,depth:Int=0) {
  if(depth>35||out.size>=2500)return
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
  if(System.currentTimeMillis()-lastTap<450)return false
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
  if(System.currentTimeMillis()-lastTap<600)return
  val seatView=all.contains("saha") && (all.contains("koltuk listesi") || (all.contains("devam et") && all.contains("koltuk")))
  d.edit().putBoolean("seatScreen",seatView).apply()
  // Passo home: the global search can target shop items instead of football matches.
  // Enter the Football section first, without touching an editable search field.
  val passoHome=all.contains("passo'yu keşfet") || (all.contains("şehir seç") && all.contains("tarih seç") && all.contains("futbol"))
  if(passoHome){
   val football=nodes.firstOrNull{label(it).equals("Futbol",true) && it.isVisibleToUser && it.isEnabled}
   if(football!=null){status("Futbol bölümüne geçiliyor");click(football)}
   else status("Passo ana sayfası: Futbol kutusu bekleniyor")
   return
  }
  // Find the explicitly requested event before attempting category selection.
  val eventQuery=cfg.getString("eventQuery","")?.trim().orEmpty()
  if(eventQuery.length>=4 && !seatView && !all.contains("blok seç") && !all.contains("kategori")){
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
   nodes.firstOrNull{label(it).equals("Devam Et",true) && it.isEnabled && it.isVisibleToUser}?.let{click(it)}
   return
  }
  if(seatView){
   selectedBlockKey=""
   if(seatScreenSeenAt==0L)seatScreenSeenAt=System.currentTimeMillis()
   if(pendingSeat!=null && System.currentTimeMillis()-pendingSeatTime<2400){status("Koltuk dokunuşunun sonucu bekleniyor");return}
   pendingSeat?.let{rejectedSeats.add(it)}
   pendingSeat=null
   status("Beyaz koltuk adayı görüntüden aranıyor (seçim doğrulanacak)")
   if(qty==1&&System.currentTimeMillis()-lastSeatAttempt>=3000){
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
   if(blockAttempts.size>100)blockAttempts.clear()
   // Some Passo block pages require ticket quantity to be raised from 0 to 1.
   // Only use an explicitly labeled + control when the quantity is visibly zero.
   val zeroCount=nodes.any{label(it).trim()=="0" && it.isVisibleToUser}
   val plus=nodes.firstOrNull{n ->
    val t=label(n).trim().lowercase(tr)
    n.isVisibleToUser && n.isEnabled && (t=="+" || t=="artır" || t=="arttir" || t=="adet artır")
   }
   if(zeroCount && plus!=null && now-lastPlusAttempt>=1500){
    lastPlusAttempt=now
    if(click(plus)){status("Bilet adedi 0 → 1 yapılıyor");return}
   }
   if(zeroCount){status("Bilet adedi 1 olarak doğrulanmayı bekliyor");return}
   if(choose!=null && selectedBlockKey.isNotEmpty() && now>=blockPendingUntil){
    status("Blok $selectedBlockKey seçildi; koltuk ekranına geçiliyor")
    if(click(choose))blockPendingUntil=now+1800
    return
   }
   if(now<blockPendingUntil){status("Blok seçimi doğrulanıyor");return}
   val block=blocks.firstOrNull{now-(blockAttempts[label(it)]?:0L)>6000}
   if(block!=null){
    val key=label(block)
    // Select only a visible block; do not retry the same target on every event.
    if(click(block)){
     selectedBlockKey=key
     blockAttempts[key]=now
     blockPendingUntil=now+850
     status("Blok $key işaretlendi; buton bekleniyor")
    }
   }else status("Blok listesi veya seçim butonu bekleniyor")
   return
  }
  // Category actions are only permitted on a recognizable category-selection page.
  val categoryPage=all.contains("kategori") && all.contains("bilet al") && !all.contains("blok seç")
  if(!categoryPage){status("Kategori sayfası bekleniyor; bilinmeyen ekrana dokunulmadı");return}
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
    if(txt.contains("tl")||txt.contains("₺")||unavailable.any{txt.contains(it)}){
     rowText=txt
     if(unavailable.any{txt.contains(it)})break
     // The price and availability can be in different descendants of the same card.
     if(txt.contains("kategori") && (txt.contains("tl")||txt.contains("₺")))break
    }
    row=row.parent
   }
   if(unavailable.any{rowText.contains(it)})continue
   // Do not tap an unavailable/disabled category, even if its text node is enabled.
   if(!n.isVisibleToUser)continue
   val price=Regex("([\\d.]+),\\d{2}\\s*(?:tl|₺)",RegexOption.IGNORE_CASE).find(rowText)?.groupValues?.get(1)?.replace(".","")?.toIntOrNull()
   if(price!=null&&price*qty>maxPrice)continue
   if(System.currentTimeMillis()-(categoryAttempts[cat]?:0L)<12000)continue
   categoryAttempts[cat]=System.currentTimeMillis()
   status("$cat. kategori deneniyor")
   if(click(n))return
  }
  // Check again periodically even when Passo emits no accessibility events.
  // Refresh only when the category list is at its top and every visible row is unavailable.
  val now=System.currentTimeMillis()
  val noTickets=all.contains("şu anda uygun bilet bulunmamaktadır") &&
   categories.none { cat ->
    val re=Regex("(?:^|\\s)$cat\\s*\\.?\\s*kategori",RegexOption.IGNORE_CASE)
    val match=nodes.firstOrNull{re.containsMatchIn(label(it))}
    if(match==null) false else {
     var ancestor:AccessibilityNodeInfo?=match
     var unavailableRow=false
     for(i in 0..4){
      val a=ancestor?:break
      val subset=mutableListOf<AccessibilityNodeInfo>();gather(a,subset)
      val t=subset.joinToString(" "){label(it)}.lowercase(tr)
      if(t.contains("şu anda uygun bilet bulunmamaktadır")){unavailableRow=true;break}
      ancestor=a.parent
     }
     !unavailableRow
    }
   }
  if(noTickets && now-lastCategoryRefresh>=3000L){
   lastCategoryRefresh=now
   val first=nodes.firstOrNull{Regex("^\\s*1\\s*\\.?\\s*kategori",RegexOption.IGNORE_CASE).containsMatchIn(label(it)) && it.isVisibleToUser}
   if(first!=null){
    val screen=resources.displayMetrics
    val x=screen.widthPixels*0.5f
    val path=Path().apply{moveTo(x,screen.heightPixels*0.28f);lineTo(x,screen.heightPixels*0.69f)}
    val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,480)).build()
    if(dispatchGesture(gesture,null,null)){lastTap=now;status("Kategori listesi yenileniyor (3 sn)");return}
   }
   // Scroll towards the top instead of swiping on an unknown region.
   val scroller=nodes.firstOrNull{it.isScrollable && it.isVisibleToUser && it.actionList.any{a->a.id==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD}}
   if(scroller!=null && scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)){
    status("Kategori listesi başına gidiliyor; sonraki kontrol bekleniyor")
    return
   }
   status("Bilet yok; sayfa yenileme desteklenmiyor")
  }else if(noTickets)status("Uygun kategori yok; 3 saniyelik kontrol sürüyor")
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
  if(saha==null){status("Saha etiketi bulunamadı; koltuk alanı belirlenemedi");return}
  val bottom=Rect();if(seatList!=null)seatList.getBoundsInScreen(bottom) else bottom.set(0,resources.displayMetrics.heightPixels*3/4,resources.displayMetrics.widthPixels,resources.displayMetrics.heightPixels)
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
  // Identify bounded bright seat shapes rather than requiring exact grid pitch.
  val w=bitmap.width;val h=bitmap.height
  val sx=w.toFloat()/resources.displayMetrics.widthPixels.coerceAtLeast(1)
  val sy=h.toFloat()/resources.displayMetrics.heightPixels.coerceAtLeast(1)
  val left=(w*0.08).toInt().coerceAtLeast(10)
  val right=(w*0.92).toInt().coerceAtMost(w-11)
  val top=(h*0.16).toInt().coerceAtLeast(10)
  val end=(field.top*sy-12).toInt().coerceAtMost((bottom.top*sy-45).toInt()).coerceAtMost(h-11)
  if(end<=top+35){status("Koltuk tarama alanı bulunamadı");return}
  fun bright(x:Int,y:Int):Boolean {
   if(x !in 0 until w||y !in 0 until h)return false
   val c=bitmap.getPixel(x,y)
   return Color.red(c)>210&&Color.green(c)>210&&Color.blue(c)>210
  }
  fun dark(x:Int,y:Int):Boolean {
   if(x !in 0 until w||y !in 0 until h)return false
   val c=bitmap.getPixel(x,y)
   return Color.red(c)<140&&Color.green(c)<140&&Color.blue(c)<140
  }
  data class Candidate(val x:Int,val y:Int,val score:Int)
  val candidates=mutableListOf<Candidate>()
  // A real vacant seat is a compact white island bounded by darker pixels.
  // Multiple sizes cover zoomed-in and zoomed-out seating maps.
  for(radius in listOf(3,4,5,6,8,10,12,15)){
   for(y in top+radius+3 until end-radius-3 step 3){
    for(x in left+radius+3 until right-radius-3 step 3){
     if(!bright(x,y))continue
     val inside=listOf(-radius/2 to 0,radius/2 to 0,0 to -radius/2,0 to radius/2)
     if(inside.count{(dx,dy)->bright(x+dx,y+dy)}<4)continue
     val ring=radius+3
     val edge=listOf(dark(x-ring,y),dark(x+ring,y),dark(x,y-ring),dark(x,y+ring),dark(x-ring,y-ring),dark(x+ring,y-ring),dark(x-ring,y+ring),dark(x+ring,y+ring))
     val count=edge.count{it}
     if(count<5)continue
     val far=radius*3+6
     // Avoid selecting bright labels or large white background patches.
     if(bright(x-far,y)&&bright(x+far,y)&&bright(x,y-far)&&bright(x,y+far))continue
     val px=(x/sx).toInt();val py=(y/sy).toInt()
     if(rejectedSeats.any{kotlin.math.abs(it.first-px)<24&&kotlin.math.abs(it.second-py)<24})continue
     candidates.add(Candidate(x,y,count*10+radius))
    }
   }
  }
  val best=candidates.maxByOrNull{it.score}
  getSharedPreferences("passo_diagnostic",MODE_PRIVATE).edit().putInt("visualSeatCandidates",candidates.size).apply()
  if(best==null){status("Beyaz koltuk bulunamadı: görsel aday 0");return}
  val x=(best.x/sx).toInt();val y=(best.y/sy).toInt()
  pendingSeat=x to y;pendingSeatTime=System.currentTimeMillis()
  getSharedPreferences("passo_diagnostic",MODE_PRIVATE).edit().putString("seatCoordinate","$x,$y").apply()
  status("Beyaz koltuk deneniyor: $x,$y")
  if(!tap(x.toFloat(),y.toFloat())){pendingSeat=null;status("Koltuk dokunuşu gönderilemedi")}

 }
}
