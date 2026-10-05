package id.rizoglabs.invoicemaker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rizoglabs.invoicemaker.data.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val Teal = Color(0xFF00685F)
private val Ink = Color(0xFF131B2E)
private val Soft = Color(0xFFF2F3FF)
private val Amber = Color(0xFFFEA619)

class MainActivity : ComponentActivity() {
    private lateinit var store: InvoiceStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = InvoiceStore(this)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary=Teal,secondary=Amber,background=Color(0xFFFAF8FF),surface=Color.White,onSurface=Ink)) { InvoiceApp(store, ::sharePdf) } }
    }
    private fun sharePdf(invoice: Invoice, store: InvoiceStore) {
        val uri = InvoicePdf.create(this,invoice,mapOf("business_name" to store.setting("business_name"),"business_address" to store.setting("business_address"),"business_phone" to store.setting("business_phone"),"business_email" to store.setting("business_email")))
        val intent = Intent(Intent.ACTION_SEND).apply { type="application/pdf"; putExtra(Intent.EXTRA_STREAM,uri); clipData=android.content.ClipData.newUri(contentResolver,"Invoice PDF",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        startActivity(Intent.createChooser(intent,"Bagikan invoice"))
    }
}

private enum class Tab(val label:String) { Home("Beranda"), Invoices("Invoice"), People("Klien & Produk"), Settings("Pengaturan") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun InvoiceApp(store: InvoiceStore, share: (Invoice,InvoiceStore)->Unit) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var refresh by remember { mutableIntStateOf(0) }
    var addInvoice by remember { mutableStateOf(false) }
    var addCustomer by remember { mutableStateOf(false) }
    var addProduct by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var selected by remember { mutableStateOf<Invoice?>(null) }
    var editing by remember { mutableStateOf<Invoice?>(null) }
    var snackbar by remember { mutableStateOf("") }
    val snackState = remember { SnackbarHostState() }
    LaunchedEffect(snackbar) { if(snackbar.isNotBlank()) { snackState.showSnackbar(snackbar); snackbar="" } }
    Scaffold(
        topBar = { TopAppBar(title={ Column { Text("Invoice Maker",fontWeight=FontWeight.Bold); Text("by RizogLabs",fontSize=12.sp,color=Color.Gray) } }, colors=TopAppBarDefaults.topAppBarColors(containerColor=Color(0xFFFAF8FF))) },
        bottomBar = { NavigationBar(containerColor=Color.White) { Tab.entries.forEach { t -> NavigationBarItem(selected=tab==t,onClick={tab=t},icon={ Icon(when(t){Tab.Home->Icons.Default.Home;Tab.Invoices->Icons.Default.ReceiptLong;Tab.People->Icons.Default.Person;Tab.Settings->Icons.Default.Settings},null) },label={Text(t.label,fontSize=10.sp)}) } } },
        snackbarHost={ SnackbarHost(snackState) },
        floatingActionButton = { if(tab==Tab.Invoices) FloatingActionButton(onClick={addInvoice=true},containerColor=Teal,contentColor=Color.White) { Icon(Icons.Default.Add,"Buat invoice") } }
    ) { padding ->
        key(refresh) {
            when(tab) {
                Tab.Home -> Dashboard(store,Modifier.padding(padding),onCreate={addInvoice=true},onInvoices={tab=Tab.Invoices},onSelect={selected=it})
                Tab.Invoices -> InvoiceList(store,Modifier.padding(padding),onSelect={selected=it},onMessage={snackbar=it})
                Tab.People -> PeopleScreen(store,Modifier.padding(padding),onAddCustomer={addCustomer=true},onAddProduct={addProduct=true},onEditCustomer={editingCustomer=it},onEditProduct={editingProduct=it},onMessage={snackbar=it})
                Tab.Settings -> SettingsScreen(store,Modifier.padding(padding),onMessage={snackbar=it})
            }
        }
    }
    if(addCustomer || editingCustomer!=null) { val existing=editingCustomer; EntryDialog(title=if(existing==null) "Tambah klien" else "Edit klien",fields=listOf("Nama klien","Telepon","Email","Alamat","Catatan"),defaults=existing?.let{listOf(it.name,it.phone,it.email,it.address,it.notes)}?:List(5){""},onDismiss={addCustomer=false;editingCustomer=null}) { v -> if(v[0].isNotBlank()) { if(existing==null) store.saveCustomer(v[0],v[1],v[2],v[3],v[4]) else store.updateCustomer(existing.id,v[0],v[1],v[2],v[3],v[4]) }; addCustomer=false;editingCustomer=null;refresh++ } }
    if(addProduct || editingProduct!=null) { val existing=editingProduct; EntryDialog(title=if(existing==null) "Tambah produk" else "Edit produk",fields=listOf("Nama produk","Deskripsi","SKU","Satuan (contoh: pcs)","Harga (Rp)","Pajak (%)"),defaults=existing?.let{listOf(it.name,it.description,it.sku,it.unit,it.price.toString(),it.taxRate.toString())}?:listOf("","","","pcs","","11"),onDismiss={addProduct=false;editingProduct=null}) { v ->
        val price=v[4].filter(Char::isDigit).toLongOrNull(); val tax=v[5].toIntOrNull()
        if(v[0].isNotBlank() && price!=null && tax!=null) { if(existing==null) store.saveProduct(v[0],v[1],v[2],v[3].ifBlank{"pcs"},price,tax) else store.updateProduct(existing.id,v[0],v[1],v[2],v[3].ifBlank{"pcs"},price,tax); refresh++ } else snackbar="Lengkapi nama, harga, dan pajak dengan benar"
        addProduct=false;editingProduct=null
    } }
    if(addInvoice) InvoiceDialog(store,editing,onDismiss={addInvoice=false;editing=null},onSaved={snackbar="Invoice tersimpan";addInvoice=false;editing=null;refresh++})
    selected?.let { invoice -> InvoiceDetail(invoice,onDismiss={selected=null},onShare={share(invoice,store)},onEdit={selected=null;editing=invoice;addInvoice=true},onDuplicate={store.saveInvoice(store.customerByName(invoice.customer),invoice.item,invoice.quantity,invoice.unitPrice,invoice.taxRate,invoice.discount,invoice.shipping,defaultDue(),invoice.notes);selected=null;snackbar="Salinan invoice dibuat";refresh++},onStatus={store.setStatus(invoice.id,it);selected=null;refresh++},onDelete={store.deleteInvoice(invoice.id);selected=null;refresh++}) }
}

@Composable private fun Dashboard(store: InvoiceStore, modifier:Modifier, onCreate:()->Unit, onInvoices:()->Unit, onSelect:(Invoice)->Unit) {
    val invoices=store.invoices()
    val total=invoices.filter{it.status!="Lunas"}.sumOf{it.total}
    LazyColumn(modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(top=14.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Teal),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${store.setting("business_name").ifBlank{"Usaha Saya"}}  •  Offline",color=Color.White.copy(alpha=.8f),fontSize=13.sp)
            Text(money(total),color=Color.White,fontSize=29.sp,fontWeight=FontWeight.ExtraBold)
            Text("Total tagihan belum lunas",color=Color.White.copy(alpha=.85f))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Button(onClick=onCreate,colors=ButtonDefaults.buttonColors(containerColor=Amber,contentColor=Ink)) { Icon(Icons.Default.Add,null); Spacer(Modifier.width(5.dp)); Text("Buat invoice") }; OutlinedButton(onClick=onInvoices,colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White)) { Text("Lihat semua") } }
        } } }
        item { Text("Status tagihan",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Ink) }
        item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Metric("Draft",invoices.count{it.status=="Draft"},Modifier.weight(1f)); Metric("Terkirim",invoices.count{it.status=="Terkirim"},Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Metric("Lunas",invoices.count{it.status=="Lunas"},Modifier.weight(1f)); Metric("Jatuh tempo",invoices.count{it.status=="Jatuh Tempo"},Modifier.weight(1f))
            }
        } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text("Invoice terbaru",fontSize=18.sp,fontWeight=FontWeight.Bold); TextButton(onClick=onInvoices){Text("Lihat semua")} } }
        if(invoices.isEmpty()) item { EmptyCard("Belum ada invoice","Buat invoice pertama untuk mulai mencatat tagihan.","Buat invoice",onCreate) }
        items(invoices.take(4),key={it.id}) { InvoiceCard(it,Modifier.clickable { onSelect(it) }) }
    }
}

@Composable private fun Metric(label:String,value:Int,modifier:Modifier=Modifier) { Card(modifier,shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.padding(16.dp)) { Text(value.toString(),fontSize=23.sp,fontWeight=FontWeight.Bold,color=Teal); Text(label,color=Color.DarkGray,fontSize=12.sp) } } }

@Composable private fun InvoiceList(store:InvoiceStore, modifier:Modifier,onSelect:(Invoice)->Unit,onMessage:(String)->Unit) {
    var filter by remember { mutableStateOf("Semua") }; var query by remember { mutableStateOf("") }
    val statuses=listOf("Semua","Draft","Terkirim","Lunas","Jatuh Tempo")
    Column(modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Spacer(Modifier.height(8.dp)); OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Cari nomor atau klien")},shape=RoundedCornerShape(16.dp))
        Spacer(Modifier.height(8.dp)); Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { statuses.forEach { s -> FilterChip(selected=filter==s,onClick={filter=s},label={Text(s,fontSize=11.sp)}) } }
        val rows=store.invoices(filter,query)
        if(rows.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { EmptyCard("Invoice belum ditemukan","Coba ubah kata pencarian atau buat invoice baru.",null,{}) }
        else LazyColumn(contentPadding=PaddingValues(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { items(rows,key={it.id}) { InvoiceCard(it,Modifier.clickable { onSelect(it) }) } }
    }
}

@Composable private fun InvoiceCard(invoice:Invoice,modifier:Modifier=Modifier) {
    val tint=when(invoice.status){"Lunas"->Color(0xFF13795B);"Jatuh Tempo"->Color(0xFFB3261E);"Terkirim"->Color(0xFF006194);else->Color(0xFF855300)}
    Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) { Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(invoice.customer, fontWeight=FontWeight.SemiBold, maxLines=1, overflow=TextOverflow.Ellipsis); Text(invoice.number,color=Color.Gray,fontSize=12.sp); Text("${invoice.date}  ·  Tempo ${invoice.due}",color=Color.Gray,fontSize=11.sp) }
        Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(6.dp)) { Text(money(invoice.total),fontWeight=FontWeight.Bold); Surface(color=tint.copy(alpha=.1f),shape=CircleShape) { Text(invoice.status,Modifier.padding(horizontal=9.dp,vertical=4.dp),fontSize=10.sp,fontWeight=FontWeight.Bold,color=tint) } }
    } }
}

@Composable private fun PeopleScreen(store:InvoiceStore,modifier:Modifier,onAddCustomer:()->Unit,onAddProduct:()->Unit,onEditCustomer:(Customer)->Unit,onEditProduct:(Product)->Unit,onMessage:(String)->Unit) {
    var section by remember { mutableStateOf("Klien") }; var search by remember { mutableStateOf("") }; var deleting by remember { mutableStateOf<Pair<Long,String>?>(null) }
    Column(modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Spacer(Modifier.height(10.dp)); Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { FilterChip(selected=section=="Klien",onClick={section="Klien"},label={Text("Klien")}); FilterChip(selected=section=="Produk",onClick={section="Produk"},label={Text("Produk")}) }
        OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Cari ${section.lowercase()}")},shape=RoundedCornerShape(16.dp))
        val customers=store.customers().filter{it.name.contains(search,true)||it.email.contains(search,true)||it.phone.contains(search,true)}
        val products=store.products().filter{it.name.contains(search,true)||it.description.contains(search,true)||it.sku.contains(search,true)}
        LazyColumn(contentPadding=PaddingValues(top=10.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            if(section=="Klien") {
                if(customers.isEmpty()) item { EmptyCard("Belum ada klien","Tambahkan klien untuk mempercepat pembuatan invoice.","Tambah klien",onAddCustomer) }
                items(customers,key={it.id}) { c -> Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) { Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(c.name,fontWeight=FontWeight.SemiBold); Text(listOf(c.phone,c.email).filter{it.isNotBlank()}.joinToString("  ·  ").ifBlank{c.address.ifBlank{"Belum ada detail"}},fontSize=12.sp,color=Color.Gray) }; IconButton(onClick={onEditCustomer(c)}) { Icon(Icons.Default.Edit,"Edit klien",tint=Teal) }; IconButton(onClick={deleting=c.id to c.name}) { Icon(Icons.Default.Delete,"Hapus klien",tint=Color.Gray) } } } }
            } else {
                if(products.isEmpty()) item { EmptyCard("Belum ada produk","Simpan produk yang sering digunakan di katalog.","Tambah produk",onAddProduct) }
                items(products,key={it.id}) { p -> Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) { Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(p.name,fontWeight=FontWeight.SemiBold); Text(p.description.ifBlank { "${p.sku.ifBlank{"Tanpa SKU"}}  ·  ${p.unit}  ·  Pajak ${p.taxRate}%" },fontSize=12.sp,color=Color.Gray) }; Text(money(p.price),fontWeight=FontWeight.Bold); IconButton(onClick={onEditProduct(p)}) { Icon(Icons.Default.Edit,"Edit produk",tint=Teal) }; IconButton(onClick={deleting=p.id to p.name}) { Icon(Icons.Default.Delete,"Hapus produk",tint=Color.Gray) } } } }
            }
        }
    }
    if(section=="Klien") { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomEnd) { FloatingActionButton(onClick=onAddCustomer,Modifier.padding(20.dp),containerColor=Teal,contentColor=Color.White){Icon(Icons.Default.Add,"Tambah klien")} } }
    if(section=="Produk") { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomEnd) { FloatingActionButton(onClick=onAddProduct,Modifier.padding(20.dp),containerColor=Teal,contentColor=Color.White){Icon(Icons.Default.Add,"Tambah produk")} } }
    deleting?.let { pair -> AlertDialog(onDismissRequest={deleting=null},title={Text("Hapus ${if(section=="Klien") "klien" else "produk"}?")},text={Text("${pair.second} akan dihapus dari daftar.")},confirmButton={TextButton(onClick={ if(section=="Klien") { val removed=store.deleteCustomer(pair.first); onMessage(if(removed) "Klien dihapus" else "Klien yang dipakai invoice tidak dapat dihapus") } else { store.deleteProduct(pair.first); onMessage("Produk dihapus") }; deleting=null }){Text("Hapus",color=Color(0xFFB3261E))}},dismissButton={TextButton(onClick={deleting=null}){Text("Batal")}}) }
}

@Composable private fun SettingsScreen(store:InvoiceStore,modifier:Modifier,onMessage:(String)->Unit) {
    var business by remember { mutableStateOf(store.setting("business_name")) }; var email by remember { mutableStateOf(store.setting("business_email")) }; var phone by remember { mutableStateOf(store.setting("business_phone")) }; var address by remember { mutableStateOf(store.setting("business_address")) }; var website by remember { mutableStateOf(store.setting("business_website")) }; var tax by remember { mutableStateOf(store.setting("business_tax")) }
    var showLicense by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Text("Profil usaha lokal",fontSize=19.sp,fontWeight=FontWeight.Bold) }
        item { Card(shape=RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(business,{business=it},Modifier.fillMaxWidth(),label={Text("Nama usaha")},singleLine=true)
            OutlinedTextField(address,{address=it},Modifier.fillMaxWidth(),label={Text("Alamat")},minLines=2)
            OutlinedTextField(phone,{phone=it},Modifier.fillMaxWidth(),label={Text("Telepon")},singleLine=true)
            OutlinedTextField(email,{email=it},Modifier.fillMaxWidth(),label={Text("Email")},singleLine=true)
            OutlinedTextField(website,{website=it},Modifier.fillMaxWidth(),label={Text("Website")},singleLine=true)
            OutlinedTextField(tax,{tax=it},Modifier.fillMaxWidth(),label={Text("NPWP")},singleLine=true)
            Button(onClick={store.saveSettings(mapOf("business_name" to business,"business_address" to address,"business_phone" to phone,"business_email" to email,"business_website" to website,"business_tax" to tax));onMessage("Profil usaha tersimpan")},Modifier.fillMaxWidth()) { Text("Simpan profil") }
        } } }
        item { Text("Lisensi",fontSize=19.sp,fontWeight=FontWeight.Bold) }
        item { Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Soft)) { Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("RizogKey Engine 1.0.0",fontWeight=FontWeight.Bold,color=Teal)
            Text("Integrasi engine belum tersedia di proyek ini. Status lisensi dan Installation Code belum dapat diverifikasi.",fontSize=13.sp,color=Ink)
            TextButton(onClick={showLicense=true}) { Text("Detail integrasi") }
        } } }
        item { Text("Invoice & mata uang",fontSize=19.sp,fontWeight=FontWeight.Bold) }
        item { Card(shape=RoundedCornerShape(20.dp)) { Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { Text("Nomor invoice",fontWeight=FontWeight.SemiBold); Text("INV-[TAHUN]-[NNNN]",color=Teal); Text("Mata uang default",fontWeight=FontWeight.SemiBold); Text("IDR · Rupiah",color=Color.Gray); Text("Pajak standar untuk invoice baru",fontWeight=FontWeight.SemiBold); Text("PPN 11%",color=Color.Gray) } } }
        item { Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Soft)) { Column(Modifier.padding(17.dp)) { Text("Data tersimpan di perangkat",fontWeight=FontWeight.Bold); Text("${store.invoices().size} invoice · ${store.customers().size} klien · ${store.products().size} produk",color=Color.Gray,fontSize=13.sp) } } }
        item { Text("Invoice Maker v1.0.0 · RizogLabs",Modifier.fillMaxWidth().padding(8.dp),color=Color.Gray,fontSize=12.sp) }
    }
    if(showLicense) AlertDialog(onDismissRequest={showLicense=false},title={Text("Integrasi lisensi belum dikonfigurasi")},text={Text("PRD mewajibkan status, aktivasi, dan Installation Code berasal dari RizogKey Engine resmi. SDK, kontrak API, dan error mapping tidak disertakan, jadi aplikasi tidak membuat status atau kode tiruan.")},confirmButton={TextButton(onClick={showLicense=false}){Text("Mengerti")}})
}

@Composable private fun InvoiceDialog(store:InvoiceStore,initial:Invoice?,onDismiss:()->Unit,onSaved:()->Unit) {
    val clients=store.customers(); val products=store.products()
    var client by remember { mutableStateOf(initial?.customer ?: clients.firstOrNull()?.name.orEmpty()) }; var item by remember { mutableStateOf(initial?.item.orEmpty()) }; var qty by remember { mutableStateOf(initial?.quantity?.toString() ?: "1") }; var price by remember { mutableStateOf(initial?.unitPrice?.toString().orEmpty()) }; var taxRate by remember { mutableIntStateOf(initial?.taxRate ?: 11) }; var discount by remember { mutableStateOf(initial?.discount?.toString() ?: "0") }; var shipping by remember { mutableStateOf(initial?.shipping?.toString() ?: "0") }; var due by remember { mutableStateOf(initial?.due ?: defaultDue()) }; var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }; var openClient by remember { mutableStateOf(false) }; var openProduct by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    val quantity=qty.toIntOrNull()?:0; val unitPrice=price.filter(Char::isDigit).toLongOrNull()?:0L; val disc=discount.filter(Char::isDigit).toLongOrNull()?:0L; val ship=shipping.filter(Char::isDigit).toLongOrNull()?:0L; val subtotal=quantity*unitPrice; val total=(subtotal-disc).coerceAtLeast(0)+((subtotal-disc).coerceAtLeast(0)*taxRate/100)+ship
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null) "Buat invoice" else "Edit ${initial.number}")},text={
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.heightIn(max=560.dp)) {
            item { Text("Nomor dibuat otomatis setelah disimpan",fontSize=12.sp,color=Color.Gray) }
            item { Box { OutlinedTextField(client,{client=it},Modifier.fillMaxWidth(),label={Text("Klien")},singleLine=true, trailingIcon={TextButton(onClick={openClient=true}){Text("Pilih")}}); DropdownMenu(openClient,{openClient=false}) { clients.forEach { DropdownMenuItem(text={Text(it.name)},onClick={client=it.name;openClient=false}) } } } }
            if(clients.isEmpty()) item { Text("Klien akan dibuat dari nama di atas.",fontSize=11.sp,color=Color.Gray) }
            item { Box { OutlinedTextField(item,{item=it},Modifier.fillMaxWidth(),label={Text("Deskripsi item")},singleLine=true,trailingIcon={TextButton(onClick={openProduct=true}){Text("Produk")}}); DropdownMenu(openProduct,{openProduct=false}) { products.forEach { p -> DropdownMenuItem(text={Text("${p.name} · ${money(p.price)}")},onClick={item=if(p.description.isBlank()) p.name else "${p.name} — ${p.description}";price=p.price.toString();taxRate=p.taxRate;openProduct=false}) } } } }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(qty,{qty=it.filter(Char::isDigit)},Modifier.weight(.7f),label={Text("Qty")},singleLine=true); OutlinedTextField(price,{price=it.filter(Char::isDigit)},Modifier.weight(1.3f),label={Text("Harga (Rp)")},singleLine=true) } }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(discount,{discount=it.filter(Char::isDigit)},Modifier.weight(1f),label={Text("Diskon Rp")},singleLine=true); OutlinedTextField(shipping,{shipping=it.filter(Char::isDigit)},Modifier.weight(1f),label={Text("Ongkir Rp")},singleLine=true) } }
            item { OutlinedTextField(due,{due=it},Modifier.fillMaxWidth(),label={Text("Jatuh tempo (contoh: 19 Okt 2026)")},singleLine=true) }
            item { OutlinedTextField(notes,{notes=it},Modifier.fillMaxWidth(),label={Text("Catatan")},minLines=2) }
            item { Text("Subtotal ${money(subtotal)}  ·  Pajak ${taxRate}%  ·  Total ${money(total)}",fontWeight=FontWeight.Bold,color=Teal) }
            if(error.isNotBlank()) item { Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
        }
    },confirmButton={ Button(onClick={
        if(client.isBlank()||item.isBlank()||quantity<1||unitPrice<=0) error="Isi klien, deskripsi, jumlah, dan harga dengan benar"
        else { val customer=store.customerByName(client); if(initial==null) store.saveInvoice(customer,item,quantity,unitPrice,taxRate,disc,ship,due,notes) else store.updateInvoice(initial.id,customer,item,quantity,unitPrice,taxRate,disc,ship,due,notes); onSaved() }
    }) { Text(if(initial==null) "Simpan invoice" else "Simpan perubahan") } },dismissButton={TextButton(onClick=onDismiss){Text("Batal")}})
}

@Composable private fun InvoiceDetail(invoice:Invoice,onDismiss:()->Unit,onShare:()->Unit,onEdit:()->Unit,onDuplicate:()->Unit,onStatus:(String)->Unit,onDelete:()->Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss,title={Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onDismiss){Icon(Icons.Default.ArrowBack,null)};Column{Text(invoice.number,fontWeight=FontWeight.Bold);Text(invoice.status,fontSize=12.sp,color=Teal)}}},text={
        Column(Modifier.fillMaxWidth().heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(invoice.customer,fontWeight=FontWeight.Bold,fontSize=18.sp); Text("Tanggal ${invoice.date}  ·  Jatuh tempo ${invoice.due}",fontSize=12.sp,color=Color.Gray)
            HorizontalDivider(); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${invoice.item} × ${invoice.quantity}");Text(money(invoice.subtotal),fontWeight=FontWeight.SemiBold)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Diskon");Text("−${money(invoice.discount)}")}; Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Pajak ${invoice.taxRate}%");Text(money(invoice.tax))}; Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Ongkir");Text(money(invoice.shipping))}
            HorizontalDivider(); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Total",fontWeight=FontWeight.Bold);Text(money(invoice.total),fontWeight=FontWeight.Bold,color=Teal)}
            if(invoice.notes.isNotBlank()) Text("Catatan: ${invoice.notes}",fontSize=12.sp)
            Button(onClick=onShare,Modifier.fillMaxWidth()){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Bagikan PDF")}
            OutlinedButton(onClick=onEdit,Modifier.fillMaxWidth()){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(8.dp));Text("Edit invoice")}
            TextButton(onClick=onDuplicate,Modifier.align(Alignment.CenterHorizontally)){Icon(Icons.Default.ContentCopy,null);Spacer(Modifier.width(6.dp));Text("Duplikasi invoice")}
            var menu by remember { mutableStateOf(false) }
            Box { OutlinedButton(onClick={menu=true},Modifier.fillMaxWidth()){Text("Ubah status")}; DropdownMenu(menu,{menu=false}) { listOf("Draft","Terkirim","Lunas","Jatuh Tempo").forEach { DropdownMenuItem(text={Text(it)},onClick={menu=false;onStatus(it)}) } } }
            TextButton(onClick={confirmDelete=true},Modifier.align(Alignment.End)){Text("Hapus invoice",color=Color(0xFFB3261E))}
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("Tutup")}})
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Hapus invoice?")},text={Text("${invoice.number} akan dihapus permanen.")},confirmButton={TextButton(onClick=onDelete){Text("Hapus",color=Color(0xFFB3261E))}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Batal")}})
}

@Composable private fun EntryDialog(title:String,fields:List<String>,defaults:List<String> = List(fields.size){""},onDismiss:()->Unit,onSave:(List<String>)->Unit) {
    val values= remember { fields.indices.map { mutableStateOf(defaults.getOrElse(it){""}) } }
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { fields.forEachIndexed { i,label -> OutlinedTextField(values[i].value,{values[i].value=it},Modifier.fillMaxWidth(),label={Text(label)},singleLine=i!=fields.lastIndex) } }},confirmButton={Button(onClick={onSave(values.map{it.value})}){Text("Simpan")}},dismissButton={TextButton(onClick=onDismiss){Text("Batal")}})
}

@Composable private fun EmptyCard(title:String,description:String,action:String?,onClick:()->Unit) { Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Soft)) { Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text(title,fontWeight=FontWeight.Bold); Text(description,color=Color.Gray,fontSize=13.sp); if(action!=null) Button(onClick=onClick){Text(action)} } } }
private fun money(value:Long):String="Rp ${"%,d".format(value).replace(',', '.')}"
private fun defaultDue():String { val cal=Calendar.getInstance();cal.add(Calendar.DAY_OF_YEAR,14);return SimpleDateFormat("dd MMM yyyy",Locale("id","ID")).format(cal.time) }

