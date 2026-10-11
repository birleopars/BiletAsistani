package com.biletasistani.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
 private val selected=mutableListOf<Int>()
 private val prefs by lazy { getSharedPreferences("ticket_config",MODE_PRIVATE) }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=Color.rgb(16,24,40)
  window.navigationBarColor=Color.rgb(16,24,40)
  selected.addAll((prefs.getString("categories","11,10") ?: "11,10").split(",").mapNotNull { it.toIntOrNull() }.distinct())
  val scroll=ScrollView(this).apply { setBackgroundColor(Color.rgb(245,247,251)) }
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(36,32,36,48) }
  scroll.addView(root); setContentView(scroll)
  fun title(t:String,size:Float=19f):TextView=TextView(this).apply { text=t; textSize=size; setTextColor(Color.rgb(22,34,55)); setTypeface(null,Typeface.BOLD); setPadding(0,20,0,12); root.addView(this) }
  fun note(t:String):TextView=TextView(this).apply { text=t; textSize=14f; setTextColor(Color.DKGRAY); setPadding(0,4,0,14); root.addView(this) }
  fun button(t:String,action:()->Unit)=Button(this).apply { text=t; setOnClickListener { action() }; root.addView(this,LinearLayout.LayoutParams(-1,-2)) }
  title("BİLET ASİSTANI",26f)
  note("Maçına uygun kategorileri seç • Önceliği belirle • Kaydet")
  title("KATEGORİLER")
  note("İlk işaretlediğin kategori ilk öncelik olur. Seçimi kaldırıp yeniden işaretleyerek sıralamayı değiştirebilirsin.")
  val summary=TextView(this).apply { textSize=15f; setTextColor(Color.rgb(27,71,140)); setPadding(0,12,0,12); root.addView(this) }
  fun update() { summary.text="Öncelik: "+if(selected.isEmpty()) "Henüz kategori seçilmedi" else selected.joinToString(" → ") { "$it. kategori" } }
  val grid=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; root.addView(this) }
  for (start in listOf(1,6,11,16)) {
   val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; grid.addView(this) }
   for (number in start until start+5) {
    val check=CheckBox(this).apply {
     text="$number"; textSize=15f; isChecked=selected.contains(number)
     setOnCheckedChangeListener { _, checked -> if(checked) { if(!selected.contains(number)) selected.add(number) } else selected.remove(number); update() }
    }
    row.addView(check,LinearLayout.LayoutParams(0,-2,1f))
   }
  }
  update()
  title("BİLET AYARLARI")
  note("Maksimum toplam bilet fiyatı (TL)")
  val price=EditText(this).apply { inputType=2; setSingleLine(true); setText(prefs.getInt("maxPrice",3500).toString()); root.addView(this) }
  note("Bilet adedi")
  val quantity=Spinner(this)
  quantity.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,(1..4).map { "$it bilet" })
  quantity.setSelection((prefs.getInt("quantity",1)-1).coerceIn(0,3)); root.addView(quantity)
  val feedback=note("Ayarlar yalnızca bu cihazda saklanır.")
  button("AYARLARI KAYDET") {
   val max=price.text.toString().toIntOrNull()
   if(selected.isEmpty() || max==null || max<=0) { feedback.text="En az bir kategori ve geçerli bir fiyat gir." }
   else { prefs.edit().putString("categories",selected.joinToString(",")).putInt("maxPrice",max).putInt("quantity",quantity.selectedItemPosition+1).apply(); feedback.text="Kaydedildi: ${selected.joinToString(", ")} • En fazla $max TL • ${quantity.selectedItemPosition+1} bilet" }
  }
  title("PASSO BAĞLANTISI")
  note("Erişilebilirlik ile kategori ekranı okunabilir. Koltuk haritası ve gerçek sepete ekleme henüz doğrulanmadı. Güvenlik doğrulaması ve ödeme otomatikleştirilmez.")
  button("ERİŞİLEBİLİRLİK İZNİ") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
  val diag=note("Passo ekranı henüz analiz edilmedi.")
  button("SON EKRAN ANALİZİ") {
   val d=getSharedPreferences("passo_diagnostic",MODE_PRIVATE)
   diag.text=if(d.getLong("lastSeen",0)==0L) "Passo ekranı henüz okunmadı." else "Ekran öğeleri: ${d.getInt("nodeCount",0)}\nTıklanabilir: ${d.getInt("clickableCount",0)}\nKoltuk etiketleri: ${d.getInt("seatCandidates",0)}"
  }
 }
}
