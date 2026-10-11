package com.biletasistani.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/** Experimental accessible-control automation; never interacts with verification or payment. */
class CartWatchService : AccessibilityService() {
 private var lastAction = 0L
 private var lastAlert = 0L
 private var lastSignature = ""
 private var lastCategory = ""
 private val tr = Locale.forLanguageTag("tr-TR")
 override fun onInterrupt() {}
 override fun onAccessibilityEvent(event: AccessibilityEvent?) {
  val pkg = event?.packageName?.toString()?.lowercase(Locale.ROOT) ?: return
  if (!pkg.contains("passo")) return
  val root = rootInActiveWindow ?: return
  val nodes = mutableListOf<AccessibilityNodeInfo>()
  gather(root,nodes,0)
  val all = nodes.joinToString(" ") { label(it) }.lowercase(tr)
  val cat11=all.contains("11. kategori") || all.contains("11.kategori")
  val cat10=all.contains("10. kategori") || all.contains("10.kategori")
  getSharedPreferences("passo_diagnostic", MODE_PRIVATE).edit()
   .putLong("lastSeen", System.currentTimeMillis())
   .putInt("nodeCount", nodes.size)
   .putInt("clickableCount", nodes.count { it.isEnabled && it.isClickable })
   .putInt("seatCandidates", nodes.count { it.isClickable && label(it).contains("koltuk",true) })
   .putBoolean("seatScreen", all.contains("koltuk listesi"))
   .putBoolean("category11Visible",cat11)
   .putBoolean("category10Visible",cat10)
   .apply()
  // Stop for security checks and any payment form. No bypass or form filling.
  if (listOf("cloudflare","güvenlik doğrulaması","doğrulanıyor","captcha","kart numarası","cvv","ödeme yap").any { all.contains(it) }) return
  if (listOf("sepete eklendi","ödeme için kalan süre","ödeme süresi","sepetinizdeki bilet").any { all.contains(it) }) {
   if (System.currentTimeMillis()-lastAlert>60000) { lastAlert=System.currentTimeMillis(); AlarmHelper.alert(this) }
   return
  }
  val now=System.currentTimeMillis()
  if (now-lastAction<2000) return
  val clickables=nodes.filter { it.isEnabled && it.isClickable }
  // Only advance after screen explicitly reports one selected seat.
  if (Regex("""koltuk listesi\s*\(\s*1\s*/\s*1\s*\)""",RegexOption.IGNORE_CASE).containsMatchIn(all)) {
   val continueButton=clickables.firstOrNull { label(it).trim().equals("Devam Et",true) }
   if (continueButton!=null) { lastAction=now; continueButton.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
   return
  }
  // Never infer white seats from accessibility text: Passo map may be drawn as a canvas.
  if (all.contains("koltuk seç") || all.contains("koltuk listesi")) return
  // Find the category row, not merely a text child. Never tap sold-out rows.
  val categoryPattern = Regex("""(?:^|\s)([1-9]|1[0-9]|20)\s*\.?\s*kategori\b|\bkategori\s*([1-9]|1[0-9]|20)\b""", RegexOption.IGNORE_CASE)
  fun rowText(node:AccessibilityNodeInfo):String {
   val items=mutableListOf<AccessibilityNodeInfo>()
   gather(node,items,0)
   return items.joinToString(" ") { label(it) }.lowercase(tr)
  }
  val unavailable=listOf("şu anda uygun bilet bulunmamaktadır", "bilet bulunmamaktadır", "tükendi", "sold out")
  val settings=getSharedPreferences("ticket_config",MODE_PRIVATE)
  val priorities=(settings.getString("categories","11,10") ?: "11,10").split(",").mapNotNull { it.toIntOrNull() }.filter { it in 1..20 }
  for (category in priorities.map { it.toString() }) {
   val matches=nodes.filter { n ->
    val match=categoryPattern.find(label(n).lowercase(tr))
    match!=null && (match.groups[1]?.value==category || match.groups[2]?.value==category)
   }
   for (node in matches) {
    var ancestor:AccessibilityNodeInfo?=node
    var target:AccessibilityNodeInfo?=null
    var depth=0
    while (ancestor!=null && depth<5) {
     val text=rowText(ancestor)
     if (unavailable.any { text.contains(it) }) break
     if (ancestor.isEnabled && ancestor.isClickable) { target=ancestor; break }
     ancestor=ancestor.parent
     depth++
    }
    if (target!=null) {
     val signature="$category:${event.windowId}:${label(target)}"
     if (signature==lastSignature && now-lastAction<15000) return
     lastSignature=signature
     lastAction=now
     target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
     return
    }
   }
  }

 }
 private fun label(n:AccessibilityNodeInfo):String = listOfNotNull(n.text?.toString(),n.contentDescription?.toString()).joinToString(" ")
 private fun gather(n:AccessibilityNodeInfo,out:MutableList<AccessibilityNodeInfo>,depth:Int) {
  if (depth>18 || out.size>600) return
  out.add(n)
  for (i in 0 until n.childCount) n.getChild(i)?.let { gather(it,out,depth+1) }
 }
}
