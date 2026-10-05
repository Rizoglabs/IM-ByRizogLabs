package id.rizoglabs.invoicemaker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

data class Customer(val id: Long, val name: String, val phone: String, val email: String, val address: String, val notes: String = "")
data class Product(val id: Long, val name: String, val description: String, val sku: String, val unit: String, val price: Long, val taxRate: Int)
data class Invoice(val id: Long, val number: String, val customer: String, val customerAddress: String, val date: String, val due: String, val status: String, val item: String, val quantity: Int, val unitPrice: Long, val taxRate: Int, val discount: Long, val shipping: Long, val notes: String) {
    val subtotal: Long get() = quantity * unitPrice
    val tax: Long get() = ((subtotal - discount).coerceAtLeast(0) * taxRate) / 100
    val total: Long get() = (subtotal - discount).coerceAtLeast(0) + tax + shipping
}

class InvoiceStore(context: Context) : SQLiteOpenHelper(context, "invoice_maker.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', sku TEXT NOT NULL DEFAULT '', unit TEXT NOT NULL DEFAULT 'pcs', price INTEGER NOT NULL, tax_rate INTEGER NOT NULL DEFAULT 11)")
        db.execSQL("CREATE TABLE invoices(id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL UNIQUE, customer_id INTEGER, customer_name TEXT NOT NULL, customer_address TEXT NOT NULL DEFAULT '', date TEXT NOT NULL, due TEXT NOT NULL, status TEXT NOT NULL, item TEXT NOT NULL, quantity INTEGER NOT NULL, unit_price INTEGER NOT NULL, tax_rate INTEGER NOT NULL DEFAULT 11, discount INTEGER NOT NULL DEFAULT 0, shipping INTEGER NOT NULL DEFAULT 0, notes TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("INSERT INTO settings(key,value) VALUES('business_name','Usaha Saya'),('business_email',''),('business_phone',''),('business_address',''),('business_tax',''),('currency','IDR')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun customers(): List<Customer> = readableDatabase.rawQuery("SELECT id,name,phone,email,address,notes FROM customers ORDER BY name", null).use { c -> buildList { while(c.moveToNext()) add(Customer(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5))) } }
    fun products(): List<Product> = readableDatabase.rawQuery("SELECT id,name,description,sku,unit,price,tax_rate FROM products ORDER BY name", null).use { c -> buildList { while(c.moveToNext()) add(Product(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5),c.getInt(6))) } }
    fun invoices(filter: String = "Semua", query: String = ""): List<Invoice> {
        val sql = "SELECT id,number,customer_name,customer_address,date,due,status,item,quantity,unit_price,tax_rate,discount,shipping,notes FROM invoices WHERE number LIKE ? OR customer_name LIKE ? ORDER BY id DESC"
        return readableDatabase.rawQuery(sql, arrayOf("%$query%","%$query%")).use { c -> buildList { while(c.moveToNext()) add(Invoice(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getInt(8),c.getLong(9),c.getInt(10),c.getLong(11),c.getLong(12),c.getString(13))) } }.map { invoice ->
            val dueDate = runCatching { SimpleDateFormat("dd MMM yyyy",Locale("id","ID")).parse(invoice.due) }.getOrNull()
            val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.time
            if(invoice.status != "Lunas" && dueDate != null && dueDate.before(today)) invoice.copy(status="Jatuh Tempo") else invoice
        }.filter { filter == "Semua" || it.status == filter }
    }
    fun saveCustomer(name: String, phone: String, email: String, address: String, notes: String = "") { writableDatabase.insertOrThrow("customers",null, ContentValues().apply { put("name",name.trim()); put("phone",phone); put("email",email); put("address",address); put("notes",notes) }) }
    fun updateCustomer(id: Long, name: String, phone: String, email: String, address: String, notes: String = "") { writableDatabase.update("customers",ContentValues().apply { put("name",name.trim()); put("phone",phone); put("email",email); put("address",address); put("notes",notes) },"id=?",arrayOf(id.toString())) }
    fun customerByName(name: String): Customer {
        readableDatabase.rawQuery("SELECT id,name,phone,email,address FROM customers WHERE lower(name)=lower(?) LIMIT 1",arrayOf(name.trim())).use { c -> if(c.moveToFirst()) return Customer(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4)) }
        val id = writableDatabase.insertOrThrow("customers",null,ContentValues().apply { put("name",name.trim()) })
        return Customer(id,name.trim(),"","","")
    }
    fun deleteCustomer(id: Long): Boolean = writableDatabase.delete("customers","id=? AND id NOT IN (SELECT customer_id FROM invoices WHERE customer_id IS NOT NULL)",arrayOf(id.toString())) > 0
    fun saveProduct(name: String, description: String, sku: String, unit: String, price: Long, tax: Int) { writableDatabase.insertOrThrow("products",null,ContentValues().apply { put("name",name.trim()); put("description",description); put("sku",sku); put("unit",unit); put("price",price); put("tax_rate",tax) }) }
    fun updateProduct(id: Long, name: String, description: String, sku: String, unit: String, price: Long, tax: Int) { writableDatabase.update("products",ContentValues().apply { put("name",name.trim()); put("description",description); put("sku",sku); put("unit",unit); put("price",price); put("tax_rate",tax) },"id=?",arrayOf(id.toString())) }
    fun deleteProduct(id: Long) { writableDatabase.delete("products","id=?",arrayOf(id.toString())) }
    fun saveInvoice(customer: Customer, item: String, quantity: Int, unitPrice: Long, taxRate: Int, discount: Long, shipping: Long, due: String, notes: String, status: String = "Draft") {
        val db = writableDatabase; db.beginTransaction()
        try {
            val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
            val cursor = db.rawQuery("SELECT MAX(CAST(SUBSTR(number,10) AS INTEGER)) FROM invoices WHERE number LIKE ?",arrayOf("INV-$year-%"))
            val next = cursor.use { if(it.moveToFirst() && !it.isNull(0)) it.getInt(0)+1 else 1 }
            val number = "INV-$year-${next.toString().padStart(4,'0')}"
            db.insertOrThrow("invoices",null,ContentValues().apply {
                put("number",number); put("customer_id",customer.id); put("customer_name",customer.name); put("customer_address",customer.address)
                put("date",SimpleDateFormat("dd MMM yyyy",Locale("id","ID")).format(Date())); put("due",due); put("status",status)
                put("item",item); put("quantity",quantity); put("unit_price",unitPrice); put("tax_rate",taxRate); put("discount",discount); put("shipping",shipping); put("notes",notes)
            })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun updateInvoice(id: Long, customer: Customer, item: String, quantity: Int, unitPrice: Long, taxRate: Int, discount: Long, shipping: Long, due: String, notes: String) {
        writableDatabase.update("invoices",ContentValues().apply {
            put("customer_id",customer.id); put("customer_name",customer.name); put("customer_address",customer.address)
            put("item",item); put("quantity",quantity); put("unit_price",unitPrice); put("tax_rate",taxRate)
            put("discount",discount); put("shipping",shipping); put("due",due); put("notes",notes)
        },"id=?",arrayOf(id.toString()))
    }
    fun setStatus(id: Long, status: String) { writableDatabase.update("invoices",ContentValues().apply { put("status",status) },"id=?",arrayOf(id.toString())) }
    fun deleteInvoice(id: Long) { writableDatabase.delete("invoices","id=?",arrayOf(id.toString())) }
    fun setting(key: String): String = readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?",arrayOf(key)).use { if(it.moveToFirst()) it.getString(0) else "" }
    fun saveSettings(values: Map<String,String>) { writableDatabase.beginTransaction(); try { values.forEach { (k,v) -> writableDatabase.insertWithOnConflict("settings",null,ContentValues().apply { put("key",k); put("value",v) },SQLiteDatabase.CONFLICT_REPLACE) }; writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() } }
}

