package com.biletasistani.app

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.view.ViewGroup

class MainActivity : Activity() {
 private val handler=Handler(Looper.getMainLooper())
 private val categories=listOf(Category(11,2800,listOf(Block("A", listOf()),Block("B", listOf(7,8)))), Category(10,3500,listOf(Block("C",listOf(3,4)))))
 data class Block(val name:String,val availableSeats:List<Int>)
 data class Category(val number:Int,val price:Int,val blocks:List<Block>)
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(32,48,32,24) }
  val scroll=ScrollView(this);scroll.addView(root);setContentView(scroll)
  fun label(value:String,size:Float=17f):TextView { val view=TextView(this).apply { text=value;textSize=size;setPadding(0,8,0,12) };root.addView(view);return view }
  fun button(value:String,action:()->Unit):Button { val b=Button(this).apply { text=value;setOnClickListener { action() } };root.addView(b,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return b }
  label("BİLET ASİSTANI • Otomatik seçim denemesi",22f)
  label("Hedef: 1 bilet • 11. kategori / 2.800 TL, alternatif 10. kategori / 3.500 TL")
  label("Örnek seçim testi vardır. Passo erişilebilirlik etiketleri üzerinden kategori ve 1/1 seçili koltuktan sonra Devam Et düğmesine dokunmayı dener. Görsel koltuk haritasında otomatik koltuk seçimi henüz yoktur.")
  val result=label("Denemeye hazır.")
  val start=button("OTOMATİK KOLTUK SEÇİMİNİ DENE") {
   result.text="Kategoriler kontrol ediliyor…"
   handler.postDelayed({
    val found=categories.asSequence().flatMap { category -> category.blocks.asSequence().flatMap { block -> block.availableSeats.asSequence().map { seat -> Triple(category,block,seat) } } }.firstOrNull()
    if(found==null) result.text="Uygun koltuk bulunamadı."
    else { val (category,block,seat)=found; result.text="TEST SEPETİNE EKLENDİ ✓\nKategori: ${category.number}\nBlok: ${block.name}\nKoltuk: $seat\nTutar: ${category.price} TL\nBu gerçek bilet değildir.";AlarmHelper.alert(this,true) }
   },700)
  }
  button("PASSO ERİŞİLEBİLİRLİK İZNİ") { startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
  button("BİLDİRİM İZNİ VER") { if(Build.VERSION.SDK_INT>=33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),101) }
  button("ALARM TESTİ") { AlarmHelper.alert(this,true) }
  label("Deneme verisi: 11. kategori A blok dolu, B blokta 7 ve 8 boş; 10. kategori C blokta 3 ve 4 boş. İlk uygun koltuk seçilir.",14f)
 }
}
