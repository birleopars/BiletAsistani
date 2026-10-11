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
  // Only counts are saved. No screen text, names, ticket IDs or credentials.
  val seatScreen = all.contains("koltuk listesi") || all.contains("koltuk seç")
  val seatCandidates = nodes.count { n ->
   n.isClickable && (label(n).contains("koltuk", true) || label(n).contains("seat", true))
  }
  getSharedPreferences("passo_diagnostic", MODE_PRIVATE).edit()
   .putLong("lastSeen", System.currentTimeMillis())
   .putInt("nodeCount", nodes.size)
   .putInt("clickableCount", nodes.count { it.isClickable && it.isEnabled })
   .putInt("seatCandidates", seatCandidates)
   .putBoolean("seatScreen", seatScreen)
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
  // Category selection only when accessibility exposes a distinct enabled control.
  val category11=Regex("""(?i)(?:11\s*\.?\s*kategori|kategori\s*11)""")
  val category10=Regex("""(?i)(?:10\s*\.?\s*kategori|kategori\s*10)""")
  val first=clickables.firstOrNull { category11.containsMatchIn(label(it)) }
  val second=clickables.firstOrNull { category10.containsMatchIn(label(it)) }
  val selected=first ?: second ?: return
  val signature=(event.windowId.toString()+":"+label(selected)+":"+all.take(120))
  if (signature==lastSignature && now-lastAction<15000) return
  lastSignature=signature; lastCategory=label(selected); lastAction=now
  selected.performAction(AccessibilityNodeInfo.ACTION_CLICK)
 }
 private fun label(n:AccessibilityNodeInfo):String = listOfNotNull(n.text?.toString(),n.contentDescription?.toString()).joinToString(" ")
 private fun gather(n:AccessibilityNodeInfo,out:MutableList<AccessibilityNodeInfo>,depth:Int) {
  if (depth>18 || out.size>600) return
  out.add(n)
  for (i in 0 until n.childCount) n.getChild(i)?.let { gather(it,out,depth+1) }
 }
}
