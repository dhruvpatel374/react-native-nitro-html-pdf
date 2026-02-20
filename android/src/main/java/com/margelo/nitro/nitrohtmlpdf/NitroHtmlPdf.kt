package com.margelo.nitro.nitrohtmlpdf
  
import com.facebook.proguard.annotations.DoNotStrip

@DoNotStrip
class NitroHtmlPdf : HybridNitroHtmlPdfSpec() {
  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }
}
