package com.biletasistani.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.ActivityNotFoundException
import android.net.Uri

class MainActivity : Activity() {
 private val navy=Color.rgb(13,29,49)
 private val blue=Color.rgb(18,102,211)
 private val green=Color.rgb(7,151,93)
 private val bg=Color.rgb(244,247,251)
 private val ink=Color.rgb(22,37,57)
 private val muted=Color.rgb(101,114,133)
 private val prefs by lazy { getSharedPreferences("ticket_config",MODE_PRIVATE) }
 private val selected=mutableListOf<Int>()
 private val events=mutableListOf<String>()
 private var priceField:EditText?=null
 private var qtySpinner:Spinner?=null
 private var intervalSpinner:Spinner?=null
 private var notifications:Switch?=null
 private var summary:TextView?=null
 private var main:LinearLayout?=null
 private var page="home"
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=navy; window.navigationBarColor=navy
  selected.addAll((prefs.getString("categories","11,10")?:"11,10").split(",").mapNotNull{it.toIntOrNull()}.distinct())
  show("home")
 }
 private fun dp(v:Int)= (resources.displayMetrics.density*v).toInt()
 private fun shape(color:Int, radius:Int=18, stroke:Int?=null)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat(); if(stroke!=null)setStroke(dp(1),stroke) }
 private fun text(value:String,size:Float=16f,bold:Boolean=false,color:Int=ink)=TextView(this).apply {
  this.text=value; textSize=size; setTextColor(color); if(bold)setTypeface(null,Typeface.BOLD)
 }
 private fun pad(v:View,h:Int=16,vertical:Int=12)=v.apply{setPadding(dp(h),dp(vertical),dp(h),dp(vertical))}
 private fun col()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
 private fun card(parent:LinearLayout):LinearLayout=col().apply {
  background=shape(Color.WHITE); setPadding(dp(17),dp(16),dp(17),dp(16))
  parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
 }
 private fun title(parent:LinearLayout,s:String)=text(s,16f,true,navy).also{parent.addView(pad(it,0,10))}
 private fun line(parent:LinearLayout,head:String,detail:String,click:()->Unit) {
  val c=card(parent)
  c.addView(text(head,15f,true)); c.addView(pad(text(detail,14f,false,muted),0,6));c.setOnClickListener{click()}
 }
 private fun action(parent:LinearLayout,label:String,color:Int=blue,onClick:()->Unit) {
  val b=TextView(this).apply{ text=label; textSize=15f; setTypeface(null,Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER;background=shape(color,14);setPadding(dp(12),dp(18),dp(12),dp(18));setOnClickListener{onClick()} }
  parent.addView(b,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10);bottomMargin=dp(14)})
 }
 private fun show(target:String) {
  page=target
  val outer=col().apply{setBackgroundColor(bg)}
  setContentView(outer)
  val head=col().apply{setBackgroundColor(navy);setPadding(dp(20),dp(20),dp(20),dp(18))}
  outer.addView(head)
  val header=text(when(target){"home"->"Bilet Asistanı";"categories"->"‹   Kategori Ayarları";"settings"->"‹   Diğer Ayarlar";"diagnostic"->"‹   Ekran Analizi";"history"->"‹   Geçmiş Kayıtları";else->"‹   Nasıl Kullanılır?"},24f,true,Color.WHITE)
  head.addView(header)
  if(target!="home")header.setOnClickListener{show("home")}
  if(target=="home")head.addView(pad(text("PASSO",16f,true,Color.rgb(255,83,113)),0,3))
  val scroll=ScrollView(this).apply{isFillViewport=true}
  outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val content=col().apply{setPadding(dp(16),dp(18),dp(16),dp(12))}
  scroll.addView(content);main=content
  when(target){"home"->home(content);"categories"->categories(content);"settings"->settings(content);"diagnostic"->diagnostic(content);"history"->history(content);else->help(content)}
  val bar=LinearLayout(this).apply{gravity=Gravity.CENTER;background=shape(Color.WHITE,0)}
  outer.addView(bar)
  listOf("⌂\nAna Sayfa" to "home","⚙\nAyarlar" to "settings","◷\nGeçmiş" to "history","?\nYardım" to "help").forEach{(label,id)->
   val t=text(label,12f,id==target,if(id==target)blue else muted).apply{gravity=Gravity.CENTER;setPadding(0,dp(12),0,dp(12));setOnClickListener{show(id)}}
   bar.addView(t,LinearLayout.LayoutParams(0,-2,1f))
  }
 }
 private fun log(message:String){
  val stamp=java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(java.util.Date())
  val old=prefs.getString("activityLog","")?:""
  prefs.edit().putString("activityLog",("$stamp  $message\n"+old).take(2500)).apply()
 }
 private fun startSearch(){
  if(selected.isEmpty()){Toast.makeText(this,"Önce kategori seçmelisin",Toast.LENGTH_LONG).show();show("categories");return}
  saveCategories()
  prefs.edit().putBoolean("automationEnabled",true).apply()
  log("Arama başlatıldı; Passo ekranı bekleniyor")
  val launcher=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val apps=packageManager.queryIntentActivities(launcher,0)
  val match=apps.firstOrNull { info ->
   val pkg=info.activityInfo.packageName.lowercase(java.util.Locale.ROOT)
   val name=info.loadLabel(packageManager).toString().lowercase(java.util.Locale.ROOT)
   pkg.contains("passo") || name == "passo" || name.startsWith("passo ")
  }
  if(match!=null){
   val packageName=match.activityInfo.packageName
   prefs.edit().putString("passoPackage",packageName).apply()
   val intent=packageManager.getLaunchIntentForPackage(packageName)
    ?: Intent(launcher).setClassName(packageName,match.activityInfo.name)
   try { startActivity(intent) } catch(e:Exception){
    prefs.edit().putBoolean("automationEnabled",false).apply()
    Toast.makeText(this,"Passo uygulaması açılamadı: ${e.javaClass.simpleName}",Toast.LENGTH_LONG).show()
   }
  }else{
   prefs.edit().putBoolean("automationEnabled",false).apply()
   Toast.makeText(this,"Passo uygulaması bulunamadı. Passo'yu yükleyip yeniden deneyin.",Toast.LENGTH_LONG).show()
  }
 }
 private fun home(root:LinearLayout) {
  val status=card(root).apply{background=shape(Color.rgb(229,249,239))}
  status.addView(text("✓  Uygulama hazır",19f,true,green))
  status.addView(pad(text("Kategori ekranında bekleniyor • Gerçek sepet seçimi henüz doğrulanmadı",13f,false,green),0,7))
  line(root,"◎   Seçili kategoriler   ›",if(selected.isEmpty())"Kategori seçilmedi" else selected.joinToString("  →  "){"$it. kategori"}){show("categories")}
  line(root,"▣   Maksimum toplam fiyat   ›","${prefs.getInt("maxPrice",3500)} TL"){show("settings")}
  line(root,"▤   Bilet adedi   ›","${prefs.getInt("quantity",1)} bilet"){show("settings")}
  action(root,"▶   BİLET ARAMAYI BAŞLAT",green){startSearch()}
  val shortcuts=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  root.addView(shortcuts)
  listOf("⌕\nEkran\nAnalizi" to "diagnostic","◷\nGeçmiş\nKayıtları" to "history","♧\nBildirim\nAyarları" to "settings","?\nNasıl\nKullanılır?" to "help").forEach{(label,id)->
   val tile=text(label,12f,true,navy).apply{gravity=Gravity.CENTER;background=shape(Color.WHITE,13);setPadding(dp(3),dp(13),dp(3),dp(13));setOnClickListener{show(id)}}
   shortcuts.addView(tile,LinearLayout.LayoutParams(0,dp(88),1f).apply{marginEnd=dp(5)})
  }
  title(root,"Son İşlemler")
  val c=card(root)
  c.addView(text(prefs.getString("activityLog","Henüz işlem kaydı yok.")?:"Henüz işlem kaydı yok.",13f,false,muted))
  line(root,"Erişilebilirlik izni   ›","Passo ekranını okuyabilmek için izin gerekir"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
 }
 private fun history(root:LinearLayout){title(root,"SON İŞLEMLER");card(root).addView(text(prefs.getString("activityLog","Henüz işlem kaydı yok.")?:"Henüz işlem kaydı yok.",14f))}
 private fun categories(root:LinearLayout) {
  val tip=card(root).apply{background=shape(Color.rgb(232,242,255))}
  tip.addView(text("Birden fazla kategori seçebilirsin. İşaretleme sırası önceliği belirler.",14f,false,navy))
  title(root,"KATEGORİ SEÇİMİ")
  val labels=listOf("VIP 100","VIP 101–126")+(1..20).map{"$it. Kategori"}
  labels.chunked(2).forEach{pair->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
   root.addView(row)
   pair.forEach{label->
    val number=Regex("^(\\d+)").find(label)?.groupValues?.get(1)?.toIntOrNull()
    val valid=number!=null && number in 1..20 && label.contains("Kategori")
    val cb=CheckBox(this).apply{
     text=label;textSize=12f;isEnabled=valid;isChecked=valid && selected.contains(number)
     setOnCheckedChangeListener{_,checked->if(valid){if(checked && !selected.contains(number))selected.add(number!!) else if(!checked)selected.remove(number);summary?.text=priority()}}
    }
    row.addView(cb,LinearLayout.LayoutParams(0,-2,1f))
   }
  }
  title(root,"ÖNCELİK SIRASI")
  summary=text(priority(),15f,true,blue);root.addView(pad(summary!!,8,10))
  action(root,"AYARLARI KAYDET"){saveCategories();show("home")}
 }
 private fun priority()=if(selected.isEmpty())"Kategori seçilmedi" else selected.mapIndexed{i,n->"${i+1}.  $n. Kategori"}.joinToString("\n")
 private fun saveCategories(){prefs.edit().putString("categories",selected.joinToString(",")).apply()}
 private fun settings(root:LinearLayout){
  title(root,"BİLET AYARLARI")
  val a=card(root);a.addView(text("Maksimum toplam fiyat (TL)",15f,true))
  priceField=EditText(this).apply{inputType=2;setSingleLine(true);setText(prefs.getInt("maxPrice",3500).toString())};a.addView(priceField)
  val b=card(root);b.addView(text("Bilet adedi",15f,true))
  qtySpinner=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,(1..4).map{"$it bilet"});setSelection((prefs.getInt("quantity",1)-1).coerceIn(0,3))};b.addView(qtySpinner)
  title(root,"ÇALIŞMA AYARLARI")
  val c=card(root);c.addView(text("Ekran analizi yenileme aralığı",15f,true))
  intervalSpinner=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("5 saniye","10 saniye","30 saniye"));setSelection(prefs.getInt("intervalIndex",1).coerceIn(0,2))};c.addView(intervalSpinner)
  c.addView(text("Bu seçenek şimdilik kayıtlı tercihtir; otomatik tarama başlatmaz.",12f,false,muted))
  val d=card(root);notifications=Switch(this).apply{text="Bildirim tercihi";isChecked=prefs.getBoolean("notify",true)};d.addView(notifications)
  val autoCard=card(root)
  val auto=Switch(this).apply{text="Otomatik deneme";isChecked=prefs.getBoolean("automationEnabled",false);setOnCheckedChangeListener{_,v->prefs.edit().putBoolean("automationEnabled",v).apply()}}
  autoCard.addView(auto)
  autoCard.addView(text("Seçili kategorileri Passo ekranında denemek için izin gerekir.",12f,false,muted))
  action(root,"AYARLARI KAYDET"){
   val amount=priceField?.text?.toString()?.toIntOrNull()
   if(amount==null || amount<=0){Toast.makeText(this,"Geçerli bir fiyat gir.",Toast.LENGTH_LONG).show()}
   else{log("Bilet ayarları kaydedildi");prefs.edit().putInt("maxPrice",amount).putInt("quantity",(qtySpinner?.selectedItemPosition?:0)+1).putInt("intervalIndex",intervalSpinner?.selectedItemPosition?:1).putBoolean("notify",notifications?.isChecked?:true).apply();show("home")}
  }
  title(root,"UYGULAMA DURUMU")
  line(root,"Erişilebilirlik izni   ›","Android ayarlarına git"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
  line(root,"Passo uygulaması   ›","Passo uygulamasını aç"){startSearch()}
  val reset=text("↻   AYARLARI SIFIRLA",15f,true,Color.RED).apply{gravity=Gravity.CENTER;background=shape(Color.WHITE,12,Color.RED);setPadding(dp(12),dp(17),dp(12),dp(17));setOnClickListener{
   android.app.AlertDialog.Builder(this@MainActivity).setTitle("Ayarları sıfırla?").setPositiveButton("Sıfırla"){_,_->prefs.edit().clear().apply();selected.clear();selected.addAll(listOf(11,10));show("home")}.setNegativeButton("Vazgeç",null).show()
  }}
  root.addView(reset)

 }
 private fun diagnostic(root:LinearLayout){
  title(root,"SON PASSO EKRANI")
  val d=getSharedPreferences("passo_diagnostic",MODE_PRIVATE)
  val c=card(root)
  c.addView(text(if(d.getLong("lastSeen",0L)==0L)"Henüz Passo ekranı analiz edilmedi." else "Ekran öğeleri: ${d.getInt("nodeCount",0)}\nTıklanabilir öğeler: ${d.getInt("clickableCount",0)}\nSon işlem: ${d.getString("stage","Bekleniyor")}\nKoltuk etiketli öğeler: ${d.getInt("seatCandidates",0)}\nGörsel koltuk adayları: ${d.getInt("visualSeatCandidates",0)}\n11. kategori etiketi: ${d.getBoolean("category11Visible",false)}\n10. kategori etiketi: ${d.getBoolean("category10Visible",false)}",15f))
  action(root,"YENİLE"){show("diagnostic")}
  line(root,"Erişilebilirlik ayarları","Hizmeti etkinleştir"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
 }
 private fun help(root:LinearLayout){card(root).addView(text("Önce kategorileri ve bütçeni seç. Bilet aramayı başlat düğmesi arama tercihini açar ve Passo’ya geçmeyi dener. Erişilebilirlik iznini etkinleştir. Gerçek koltuk seçimi ve sepet işlemi deneysel olup doğrulanmamıştır. Güvenlik doğrulaması ve ödeme kullanıcıya aittir."))}
 override fun onBackPressed(){if(page=="home")super.onBackPressed() else show("home")}
}
