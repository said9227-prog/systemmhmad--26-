sed -i '/\/\/ Share text receipt/i \
                IconButton(\
                    onClick = {\
                        val client = clientsList.find { it.id == currentInvoice.clientId }\
                        val pdfPath = com.example.util.exportInvoiceToPdf(\
                            context = context,\
                            invoice = currentInvoice,\
                            items = invoiceItems,\
                            storeName = settings.storeName,\
                            client = client\
                        )\
                        if (pdfPath != null) {\
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", java.io.File(pdfPath))\
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {\
                                type = "application/pdf"\
                                putExtra(Intent.EXTRA_STREAM, uri)\
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)\
                            }\
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة PDF"))\
                        } else {\
                            Toast.makeText(context, "فشل إنشاء ملف PDF", Toast.LENGTH_SHORT).show()\
                        }\
                    }\
                ) {\
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "مشاركة PDF")\
                }' app/src/main/java/com/example/ui/screens/InvoiceDetailsScreen.kt
