package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box

class ShoppingCartActivity : ComponentActivity() {
    private var renderKey by mutableIntStateOf(0)
    private var editingCustomer by mutableStateOf<ShoppingCartStore.Customer?>(null)
    private var reviewMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ShoppingCartScreen(
                renderKey = renderKey,
                editingCustomer = editingCustomer,
                reviewMessage = reviewMessage,
                onBack = { finish() },
                onAddCustomer = {
                    ShoppingCartStore.addCustomer("Customer ${ShoppingCartStore.customers().size + 1}")
                    refresh()
                },
                onEditCustomer = { editingCustomer = it },
                onDismissCustomerEditor = { editingCustomer = null },
                onSaveCustomer = { customer, name, company, email, phone, country, notes ->
                    ShoppingCartStore.updateCustomer(
                        id = customer.id,
                        name = name,
                        email = email,
                        phone = phone,
                        company = company,
                        country = country,
                        notes = notes
                    )
                    editingCustomer = null
                    refresh()
                },
                onAddProduct = {
                    startActivity(Intent(this, PackagesActivity::class.java))
                },
                onAssignCustomer = { itemId, customer ->
                    ShoppingCartStore.assignCustomer(itemId, customer)
                    refresh()
                },
                onIncrease = {
                    ShoppingCartStore.increase(it.id)
                    refresh()
                },
                onDecrease = {
                    ShoppingCartStore.decrease(it.id)
                    refresh()
                },
                onRemove = {
                    ShoppingCartStore.remove(it.id)
                    refresh()
                },
                onCheckout = { checkout() },
                onDismissReview = { reviewMessage = null },
                onPlaceOrder = {
                    ShoppingCartStore.clear()
                    reviewMessage = null
                    refresh()
                    Toast.makeText(this, "B2B order created locally", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        renderKey += 1
    }

    private fun checkout() {
        if (ShoppingCartStore.isEmpty()) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val unassigned = ShoppingCartStore.all().any { it.customerId.isNullOrBlank() }
        if (unassigned) {
            Toast.makeText(this, "Assign all products to a customer first", Toast.LENGTH_SHORT).show()
            return
        }

        val incompleteCustomer = ShoppingCartStore.customers()
            .firstOrNull { customer ->
                ShoppingCartStore.all().any { it.customerId == customer.id } &&
                    (customer.name.isBlank() || (customer.email.isBlank() && customer.phone.isBlank()))
            }

        if (incompleteCustomer != null) {
            Toast.makeText(
                this,
                "Complete ${incompleteCustomer.name}: add email or phone",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        reviewMessage = checkoutReviewMessage()
    }

    private fun checkoutReviewMessage(): String {
        val groups = ShoppingCartStore.groupedItems()
            .filter { (customer, items) -> customer != null && items.isNotEmpty() }

        return buildString {
            appendLine("B2B order summary")
            appendLine()

            groups.forEach { (customer, items) ->
                appendLine(customer?.name ?: "Customer")
                customer?.company?.takeIf { it.isNotBlank() }?.let { appendLine("Company: $it") }
                customer?.email?.takeIf { it.isNotBlank() }?.let { appendLine("Email: $it") }
                customer?.phone?.takeIf { it.isNotBlank() }?.let { appendLine("Phone: $it") }
                customer?.country?.takeIf { it.isNotBlank() }?.let { appendLine("Country: $it") }

                items.forEach { item ->
                    appendLine("• ${item.title} x${item.quantity} — ${r2wMoney(item.price)}")
                }

                appendLine()
            }

            appendLine("Grand total: ${ShoppingCartStore.totalLabel()}")
        }
    }
}

@Composable
private fun ShoppingCartScreen(
    renderKey: Int,
    editingCustomer: ShoppingCartStore.Customer?,
    reviewMessage: String?,
    onBack: () -> Unit,
    onAddCustomer: () -> Unit,
    onEditCustomer: (ShoppingCartStore.Customer) -> Unit,
    onDismissCustomerEditor: () -> Unit,
    onSaveCustomer: (ShoppingCartStore.Customer, String, String, String, String, String, String) -> Unit,
    onAddProduct: () -> Unit,
    onAssignCustomer: (String, ShoppingCartStore.Customer?) -> Unit,
    onIncrease: (ShoppingCartStore.Item) -> Unit,
    onDecrease: (ShoppingCartStore.Item) -> Unit,
    onRemove: (ShoppingCartStore.Item) -> Unit,
    onCheckout: () -> Unit,
    onDismissReview: () -> Unit,
    onPlaceOrder: () -> Unit
) {
    val orange = Color(0xFFFF7900)
    val bg = Color(0xFFF7F7FA)

    val allItems = remember(renderKey) { ShoppingCartStore.all() }
    val customers = remember(renderKey) { ShoppingCartStore.customers() }
    val total = remember(renderKey) { ShoppingCartStore.totalLabel() }
    val assignedCustomerCount = remember(renderKey) { ShoppingCartStore.customerCountForCheckout() }

    val checkoutText = when {
        allItems.isEmpty() -> "Cart is empty"
        assignedCustomerCount <= 0 -> "Assign customers"
        else -> "Proceed to Checkout"
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 116.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CartHero(
                        itemCount = ShoppingCartStore.count(),
                        total = total,
                        onBack = onBack,
                        onAddCustomer = onAddCustomer
                    )

                    if (allItems.isEmpty()) {
                        CartCard(title = "Cart is empty") {
                            Text(
                                text = "Add packages from the Packages screen.",
                                color = Color(0xFF6B7280)
                            )
                            Button(
                                onClick = onAddProduct,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = orange),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Add Product", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    customers.forEachIndexed { index, customer ->
                        val items = allItems.filter { it.customerId == customer.id }
                        CustomerSection(
                            number = index + 1,
                            customer = customer,
                            items = items,
                            customers = customers,
                            onEditCustomer = onEditCustomer,
                            onAssignCustomer = onAssignCustomer,
                            onIncrease = onIncrease,
                            onDecrease = onDecrease,
                            onRemove = onRemove,
                            onAddProduct = onAddProduct
                        )
                    }

                    val unassigned = allItems.filter { it.customerId.isNullOrBlank() }
                    if (unassigned.isNotEmpty()) {
                        UnassignedSection(
                            number = customers.size + 1,
                            items = unassigned,
                            customers = customers,
                            onAssignCustomer = onAssignCustomer,
                            onIncrease = onIncrease,
                            onDecrease = onDecrease,
                            onRemove = onRemove,
                            onAddProduct = onAddProduct
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                CartFooter(
                    total = total,
                    checkoutText = checkoutText,
                    checkoutEnabled = allItems.isNotEmpty(),
                    onCheckout = onCheckout
                )
            }

            editingCustomer?.let { customer ->
                CustomerEditorDialog(
                    customer = customer,
                    onDismiss = onDismissCustomerEditor,
                    onSave = onSaveCustomer
                )
            }

            reviewMessage?.let {
                AlertDialog(
                    onDismissRequest = onDismissReview,
                    title = { Text("Review B2B Checkout", fontWeight = FontWeight.Black) },
                    text = { Text(it) },
                    confirmButton = {
                        Button(
                            onClick = onPlaceOrder,
                            colors = ButtonDefaults.buttonColors(containerColor = orange)
                        ) {
                            Text("Place B2B Order")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissReview) {
                            Text("Cancel")
                        }
                    }
                )
            }
        
                R2wBottomNav(
                    selected = R2wBottomTab.Packages,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun CartHero(
    itemCount: Int,
    total: String,
    onBack: () -> Unit,
    onAddCustomer: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Shopping Cart",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "$itemCount item(s) • $total",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = "Back",
                    color = Color(0xFFFF7900),
                    modifier = Modifier.clickable(onClick = onBack),
                    fontWeight = FontWeight.Black
                )
            }

            Button(
                onClick = onAddCustomer,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("+ Add customer", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CustomerSection(
    number: Int,
    customer: ShoppingCartStore.Customer,
    items: List<ShoppingCartStore.Item>,
    customers: List<ShoppingCartStore.Customer>,
    onEditCustomer: (ShoppingCartStore.Customer) -> Unit,
    onAssignCustomer: (String, ShoppingCartStore.Customer?) -> Unit,
    onIncrease: (ShoppingCartStore.Item) -> Unit,
    onDecrease: (ShoppingCartStore.Item) -> Unit,
    onRemove: (ShoppingCartStore.Item) -> Unit,
    onAddProduct: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("$number. Customer: ${customer.name}")

        CustomerInfoCard(customer = customer, onEdit = { onEditCustomer(customer) })

        if (items.isEmpty()) {
            CartCard(title = "No products") {
                Text(
                    text = "No products assigned to this customer yet.",
                    color = Color(0xFF6B7280)
                )
            }
        } else {
            items.forEach { item ->
                ProductRow(
                    item = item,
                    assigned = true,
                    customers = customers,
                    onAssignCustomer = onAssignCustomer,
                    onIncrease = onIncrease,
                    onDecrease = onDecrease,
                    onRemove = onRemove
                )
            }
        }

        AddProductButton(onAddProduct)
    }
}

@Composable
private fun UnassignedSection(
    number: Int,
    items: List<ShoppingCartStore.Item>,
    customers: List<ShoppingCartStore.Customer>,
    onAssignCustomer: (String, ShoppingCartStore.Customer?) -> Unit,
    onIncrease: (ShoppingCartStore.Item) -> Unit,
    onDecrease: (ShoppingCartStore.Item) -> Unit,
    onRemove: (ShoppingCartStore.Item) -> Unit,
    onAddProduct: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("$number. Unassigned Products")

        items.forEach { item ->
            ProductRow(
                item = item,
                assigned = false,
                customers = customers,
                onAssignCustomer = onAssignCustomer,
                onIncrease = onIncrease,
                onDecrease = onDecrease,
                onRemove = onRemove
            )
        }

        AddProductButton(onAddProduct)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFF17181C),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black
    )
}

@Composable
private fun CustomerInfoCard(
    customer: ShoppingCartStore.Customer,
    onEdit: () -> Unit
) {
    CartCard(title = "Customer info") {
        InfoLine("Name", customer.name)
        InfoLine("Company", customer.company.ifBlank { "—" })
        InfoLine("Email", customer.email.ifBlank { "—" })
        InfoLine("Phone", customer.phone.ifBlank { "—" })

        OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Edit")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
        Text(value, color = Color(0xFF17181C), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProductRow(
    item: ShoppingCartStore.Item,
    assigned: Boolean,
    customers: List<ShoppingCartStore.Customer>,
    onAssignCustomer: (String, ShoppingCartStore.Customer?) -> Unit,
    onIncrease: (ShoppingCartStore.Item) -> Unit,
    onDecrease: (ShoppingCartStore.Item) -> Unit,
    onRemove: (ShoppingCartStore.Item) -> Unit
) {
    var customerMenuExpanded by remember { mutableStateOf(false) }
    var itemMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, color = Color(0xFF17181C), fontWeight = FontWeight.Black)
                    Text(item.subtitle.ifBlank { "eSIM data plan" }, color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall)
                    Text(item.provider.ifBlank { "eSIM" }, color = Color(0xFFFF7900), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r2wMoney(item.price), color = Color(0xFF17181C), fontWeight = FontWeight.Black)

                    Text(
                        text = "More",
                        color = Color(0xFF6B7280),
                        modifier = Modifier.clickable { itemMenuExpanded = true },
                        fontWeight = FontWeight.Bold
                    )

                    DropdownMenu(
                        expanded = itemMenuExpanded,
                        onDismissRequest = { itemMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Change customer") },
                            onClick = {
                                itemMenuExpanded = false
                                customerMenuExpanded = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                itemMenuExpanded = false
                                onRemove(item)
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            if (assigned) {
                QuantityRow(
                    quantity = item.quantity,
                    onDecrease = { onDecrease(item) },
                    onIncrease = { onIncrease(item) }
                )
            } else {
                OutlinedButton(
                    onClick = { customerMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Select Customer")
                }
            }

            DropdownMenu(
                expanded = customerMenuExpanded,
                onDismissRequest = { customerMenuExpanded = false }
            ) {
                customers.forEach { customer ->
                    DropdownMenuItem(
                        text = { Text(customer.name) },
                        onClick = {
                            customerMenuExpanded = false
                            onAssignCustomer(item.id, customer)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Unassigned") },
                    onClick = {
                        customerMenuExpanded = false
                        onAssignCustomer(item.id, null)
                    }
                )
            }
        }
    }
}

@Composable
private fun QuantityRow(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onDecrease, shape = RoundedCornerShape(14.dp)) {
            Text("−", fontWeight = FontWeight.Black)
        }
        Text(
            text = quantity.toString(),
            modifier = Modifier.padding(horizontal = 18.dp),
            color = Color(0xFF17181C),
            fontWeight = FontWeight.Black
        )
        OutlinedButton(onClick = onIncrease, shape = RoundedCornerShape(14.dp)) {
            Text("+", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AddProductButton(onAddProduct: () -> Unit) {
    OutlinedButton(
        onClick = onAddProduct,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("+ Add Product", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CartFooter(
    total: String,
    checkoutText: String,
    checkoutEnabled: Boolean,
    onCheckout: () -> Unit
) {
    Surface(shadowElevation = 8.dp, color = Color.White) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", color = Color(0xFF6B7280), fontWeight = FontWeight.Bold)
                Text(total, color = Color(0xFF17181C), fontWeight = FontWeight.Black)
            }

            Button(
                onClick = onCheckout,
                enabled = checkoutEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(checkoutText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CustomerEditorDialog(
    customer: ShoppingCartStore.Customer,
    onDismiss: () -> Unit,
    onSave: (ShoppingCartStore.Customer, String, String, String, String, String, String) -> Unit
) {
    var name by remember(customer.id) { mutableStateOf(customer.name) }
    var company by remember(customer.id) { mutableStateOf(customer.company) }
    var email by remember(customer.id) { mutableStateOf(customer.email) }
    var phone by remember(customer.id) { mutableStateOf(customer.phone) }
    var country by remember(customer.id) { mutableStateOf(customer.country) }
    var notes by remember(customer.id) { mutableStateOf(customer.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customer info", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer name") }, singleLine = true)
                OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Company") }, singleLine = true)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text("Country") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(customer, name, company, email, phone, country, notes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CartCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF17181C),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()
            content()
        }
    }
}
