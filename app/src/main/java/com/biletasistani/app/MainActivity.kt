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

class MainActivity : Activity() {
 private val navy=Color.rgb(13,29,49)
 private val blue=Color.rgb(18,102,211)
 private val green=Color.rgb(7,151,93)
 private val bg=Color.rgb(244,247,251)
 private val ink=Color.rgb(22,37,57)
 private val muted=Color.rgb(101,114,133)
 private val prefs by lazy { getSharedPreferences("ticket_config",MODE_PRIVATE) }
 private val selected=mutableListOf<Int>()
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
  val head=col().apply{background=shape(navy,0);setPadding(dp(20),dp(18),dp(20),dp(20))}
  outer.addView(head)
  head.addView(text(when(target){"home"->"Bilet Asistanı";"categories"->"‹   Kategori Ayarları";"settings"->"‹   Diğer Ayarlar";"diagnostic"->"‹   Ekran Analizi";else->"‹   Hakkında"},24f,true,Color.WHITE).apply{if(target!="home")setOnClickListener{show("home")}})
  if(target=="home")head.addView(pad(text("PASSO  •  Bilet takip yardımcısı",13f,true,Color.rgb(140,207,255)),0,5))
  val scroll=ScrollView(this).apply{isFillViewport=true}
  outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val content=col().apply{setPadding(dp(16),dp(18),dp(16),dp(12))}
  scroll.addView(content);main=content
  when(target){"home"->home(content);"categories"->categories(content);"settings"->settings(content);"diagnostic"->diagnostic(content);else->help(content)}
  val bar=LinearLayout(this).apply{gravity=Gravity.CENTER;background=shape(Color.WHITE,0)}
  outer.addView(bar)
  listOf("Ana Sayfa" to "home","Kategoriler" to "categories","Ayarlar" to "settings","Analiz" to "diagnostic").forEach{(label,id)->
   val t=text(label,12f,id==target,if(id==target)blue else muted).apply{gravity=Gravity.CENTER;setPadding(0,dp(17),0,dp(17));setOnClickListener{show(id)}}
   bar.addView(t,LinearLayout.LayoutParams(0,-2,1f))
  }
 }
 private fun home(root:LinearLayout) {
  val status=card(root).apply{background=shape(Color.rgb(231,247,238))}
  status.addView(text("●  Uygulama hazır",18f,true,green))
  status.addView(pad(text("Kategori ayarları kaydedilebilir. Gerçek Passo sepeti otomasyonu henüz test aşamasında.",13f,false,ink),0,8))
  title(root,"BİLET TERCİHLERİ")
  line(root,"Seçili kategoriler",if(selected.isEmpty())"Kategori seçilmedi" else selected.joinToString("  →  "){"$it. kategori"}){show("categories")}
  line(root,"Maksimum toplam fiyat","${prefs.getInt("maxPrice",3500)} TL"){show("settings")}
  line(root,"Bilet adedi","${prefs.getInt("quantity",1)} bilet"){show("settings")}
  action(root,"KATEGORİLERİ DÜZENLE",green){show("categories")}
  val auto=Switch(this).apply { text="Passo ekranında otomatik seçim"; isChecked=prefs.getBoolean("automationEnabled",false); setOnCheckedChangeListener{_,checked->prefs.edit().putBoolean("automationEnabled",checked).apply()} }
  card(root).addView(auto)
  title(root,"PASSO BAĞLANTISI")
  line(root,"Erişilebilirlik izni","Android ayarlarından etkinleştir"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
  line(root,"Son ekran analizi","Passo ekranının okunabilir öğelerini görüntüle"){show("diagnostic")}
  title(root,"GELİŞTİRME DURUMU")
  val c=card(root)
  c.addView(text("Kategori, blok ve görsel boş koltuk denemesi eklendi. Passo üzerinde gerçek sepet akışı henüz doğrulanmadı. Tek biletlik görsel koltuk seçimi deneyseldir.",14f,false,muted))
 }
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
  action(root,"AYARLARI KAYDET"){
   val amount=priceField?.text?.toString()?.toIntOrNull()
   if(amount==null || amount<=0){Toast.makeText(this,"Geçerli bir fiyat gir.",Toast.LENGTH_LONG).show()}
   else{prefs.edit().putInt("maxPrice",amount).putInt("quantity",(qtySpinner?.selectedItemPosition?:0)+1).putInt("intervalIndex",intervalSpinner?.selectedItemPosition?:1).putBoolean("notify",notifications?.isChecked?:true).apply();show("home")}
  }
  line(root,"Erişilebilirlik izni","Android ayarlarına git"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
 }
 private fun diagnostic(root:LinearLayout){
  title(root,"SON PASSO EKRANI")
  val d=getSharedPreferences("passo_diagnostic",MODE_PRIVATE)
  val c=card(root)
  c.addView(text(if(d.getLong("lastSeen",0L)==0L)"Henüz Passo ekranı analiz edilmedi." else "Ekran öğeleri: ${d.getInt("nodeCount",0)}\nTıklanabilir öğeler: ${d.getInt("clickableCount",0)}\nSon işlem: ${d.getString("stage","Bekleniyor")}\nKoltuk etiketli öğeler: ${d.getInt("seatCandidates",0)}\nGörsel koltuk adayları: ${d.getInt("visualSeatCandidates",0)}\n11. kategori etiketi: ${d.getBoolean("category11Visible",false)}\n10. kategori etiketi: ${d.getBoolean("category10Visible",false)}",15f))
  action(root,"YENİLE"){show("diagnostic")}
  line(root,"Erişilebilirlik ayarları","Hizmeti etkinleştir"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
 }
 private fun help(root:LinearLayout){card(root).addView(text("Bu uygulama deneysel bir bilet seçimi yardımcısıdır. Gerçek bilet garantisi vermez."))}
 override fun onBackPressed(){if(page=="home")super.onBackPressed() else show("home")}
}
