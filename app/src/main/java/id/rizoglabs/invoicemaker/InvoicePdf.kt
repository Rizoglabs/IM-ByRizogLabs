package id.rizoglabs.invoicemaker

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import id.rizoglabs.invoicemaker.data.Invoice
import java.io.File
import java.io.FileOutputStream

object InvoicePdf {
    fun create(context: Context, invoice: Invoice, business: Map<String,String>): android.net.Uri {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(595,842,1).create())
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(19,27,46); textSize = 12f }
        fun text(value: String, x: Float, y: Float, size: Float = 12f, bold: Boolean = false, color: Int = Color.rgb(19,27,46)) {
            paint.textSize = size; paint.typeface = if(bold) Typeface.create("sans-serif",Typeface.BOLD) else Typeface.create("sans-serif",Typeface.NORMAL); paint.color = color; canvas.drawText(value.take(70),x,y,paint)
        }
        val teal = Color.rgb(0,104,95)
        paint.color = teal; canvas.drawRect(0f,0f,595f,12f,paint)
        text(business["business_name"].orEmpty().ifBlank { "Usaha Saya" },42f,62f,20f,true,teal)
        text(business["business_address"].orEmpty(),42f,82f,10f)
        text(listOf(business["business_phone"],business["business_email"]).filterNotNull().filter { it.isNotBlank() }.joinToString(" • "),42f,98f,10f)
        text("FAKTUR TAGIHAN",42f,157f,22f,true,teal)
        text("${invoice.number}   •   ${invoice.status.uppercase()}",42f,180f,12f,true)
        text("Tanggal: ${invoice.date}",42f,218f,11f)
        text("Jatuh tempo: ${invoice.due}",330f,218f,11f)
        text("TAGIHAN KEPADA",42f,260f,10f,true,teal)
        text(invoice.customer,42f,281f,14f,true)
        if(invoice.customerAddress.isNotBlank()) text(invoice.customerAddress,42f,299f,10f)
        paint.color = Color.rgb(234,237,255); canvas.drawRect(42f,320f,553f,352f,paint)
        text("DESKRIPSI",52f,341f,10f,true); text("QTY",335f,341f,10f,true); text("HARGA",395f,341f,10f,true); text("JUMLAH",490f,341f,10f,true)
        text(invoice.item,52f,380f,11f); text(invoice.quantity.toString(),335f,380f,11f); text(money(invoice.unitPrice),395f,380f,11f); text(money(invoice.subtotal),490f,380f,11f)
        paint.color = Color.rgb(188,201,198); canvas.drawLine(42f,402f,553f,402f,paint)
        val x = 380f
        text("Subtotal",x,440f,11f); text(money(invoice.subtotal),490f,440f,11f)
        text("Diskon",x,464f,11f); text("-${money(invoice.discount)}",490f,464f,11f)
        text("Pajak ${invoice.taxRate}%",x,488f,11f); text(money(invoice.tax),490f,488f,11f)
        text("Ongkir",x,512f,11f); text(money(invoice.shipping),490f,512f,11f)
        paint.color = Color.rgb(242,243,255); canvas.drawRoundRect(365f,530f,553f,577f,12f,12f,paint)
        text("TOTAL",380f,559f,12f,true,teal); text(money(invoice.total),480f,559f,13f,true,teal)
        text("Catatan",42f,640f,11f,true,teal); text(invoice.notes.ifBlank { "Terima kasih atas kepercayaan Anda." },42f,660f,10f)
        text("Invoice Maker • by RizogLabs",42f,795f,9f,false,Color.GRAY)
        document.finishPage(page)
        val dir = File(context.cacheDir,"invoices").apply { mkdirs() }
        val file = File(dir,"${invoice.number}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }; document.close()
        return FileProvider.getUriForFile(context,"${context.packageName}.files",file)
    }
    private fun money(amount: Long) = "Rp ${"%,d".format(amount).replace(',', '.')}"
}

