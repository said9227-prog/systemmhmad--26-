with open("app/src/main/java/com/example/ui/screens/ClientsScreen.kt", "r", encoding="utf-8") as f:
    cs = f.read()

cs = cs.replace("import androidx.compose.animation.*", "import androidx.compose.animation.*\nimport androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.layout.ExperimentalLayoutApi\nimport androidx.compose.foundation.layout.FlowRow")
cs = cs.replace("@OptIn(ExperimentalMaterial3Api::class)", "@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)")
cs = cs.replace("receiptNumber = receiptNum", "voucherNumber = receiptNum")

with open("app/src/main/java/com/example/ui/screens/ClientsScreen.kt", "w", encoding="utf-8") as f:
    f.write(cs)

with open("app/src/main/java/com/example/ui/screens/CustomerProfileScreen.kt", "r", encoding="utf-8") as f:
    cp = f.read()

cp = cp.replace("WhatsAppHelper.openWhatsAppChat(context, client.phone)", 'WhatsAppHelper.sendWhatsAppMessage(context, client.phone, "مرحباً ${client.name}")')
cp = cp.replace("receiptNumber = receiptNum", "voucherNumber = receiptNum")

with open("app/src/main/java/com/example/ui/screens/CustomerProfileScreen.kt", "w", encoding="utf-8") as f:
    f.write(cp)

print("Applied fixes")
