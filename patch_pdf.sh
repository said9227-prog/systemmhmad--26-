#!/bin/bash
cat << 'INNER_EOF' >> app/src/main/java/com/example/util/PdfExportManager.kt

fun exportInvoiceToPdf(
    context: Context,
    invoice: Invoice,
    items: List<com.example.data.model.InvoiceItem>,
    storeName: String,
    client: Client?
): String? {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        val paint = Paint()
        var yPos = 50f
        val startX = 550f // Right aligned for Arabic
        
        // Headers
        paint.textSize = 24f
        paint.fontWeight = 700
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(storeName, startX, yPos, paint)
        yPos += 40f
        
        paint.textSize = 18f
        canvas.drawText("🧾 فاتورة مبيعات", startX, yPos, paint)
        yPos += 30f
        
        paint.textSize = 14f
        paint.fontWeight = 400
        canvas.drawText("رقم الفاتورة: ${invoice.invoiceNumber}", startX, yPos, paint)
        canvas.drawText("التاريخ: ${formatPdfTimestamp(invoice.date)}", startX - 250f, yPos, paint)
        yPos += 25f
        
        canvas.drawText("العملة: ${invoice.currency}", startX, yPos, paint)
        yPos += 30f
        
        canvas.drawText("العميل: ${invoice.clientName}", startX, yPos, paint)
        if (client != null) {
            canvas.drawText("رقم الهاتف: ${client.phone}", startX - 250f, yPos, paint)
        }
        yPos += 40f
        
        // Table Header
        paint.textSize = 14f
        paint.fontWeight = 700
        canvas.drawText("الصنف", 500f, yPos, paint)
        canvas.drawText("الكمية", 300f, yPos, paint)
        canvas.drawText("السعر", 200f, yPos, paint)
        canvas.drawText("الإجمالي", 100f, yPos, paint)
        yPos += 20f
        
        // Table Items
        paint.fontWeight = 400
        items.forEach { item ->
            canvas.drawText(item.itemName, 500f, yPos, paint)
            canvas.drawText(item.quantity.toString(), 300f, yPos, paint)
            canvas.drawText(FormatUtils.formatAmount(item.unitPrice), 200f, yPos, paint)
            canvas.drawText(FormatUtils.formatAmount(item.totalPrice), 100f, yPos, paint)
            yPos += 20f
        }
        yPos += 30f
        
        // Summary
        paint.fontWeight = 700
        canvas.drawText("الإجمالي: ${FormatUtils.formatAmount(invoice.totalAmount)} ${invoice.currency}", startX, yPos, paint)
        yPos += 20f
        canvas.drawText("المدفوع: ${FormatUtils.formatAmount(invoice.paidAmount)} ${invoice.currency}", startX, yPos, paint)
        yPos += 20f
        canvas.drawText("المتبقي: ${FormatUtils.formatAmount(invoice.remainingAmount)} ${invoice.currency}", startX, yPos, paint)
        yPos += 40f
        
        if (client != null) {
            canvas.drawText("رصيد العميل الحالي: ${FormatUtils.formatAmount(client.balance)} ${invoice.currency}", startX, yPos, paint)
        }
        
        pdfDocument.finishPage(page)
        
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "CashFlow")
        if (!dir.exists()) dir.mkdirs()
        
        val file = File(dir, "Invoice_${invoice.invoiceNumber}.pdf")
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        outputStream.close()
        
        return file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
INNER_EOF
